import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { stubLifeHubBridge } from '@/test/lifehubBridge'
import { EventFormDialog } from './EventFormDialog'
import type { CalendarEvent } from '../types'

/**
 * The event form's two rules that are not plain plumbing.
 *
 * An invalid time range must be caught before a request leaves the renderer, and editing an
 * instance of a repeating series must not write anything until the user has chosen a scope
 * (FR-CAL-04). The second is the one that matters: applying an edit to a whole series when the user
 * meant one date is not something the app can undo for them.
 */

const fetchMock = vi.fn()

const repeatingEvent: CalendarEvent = {
  id: 'e1',
  title: 'Standup tuần',
  startAt: '2026-09-14T09:00:00+07:00',
  endAt: '2026-09-14T09:30:00+07:00',
  allDay: false,
  rrule: 'FREQ=WEEKLY',
  timezone: 'Asia/Ho_Chi_Minh',
  reminderOffsets: [15],
  createdAt: '2026-09-01T00:00:00+07:00',
  updatedAt: '2026-09-01T00:00:00+07:00',
}

function renderForm(props: Partial<React.ComponentProps<typeof EventFormDialog>> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <EventFormDialog
        open
        onOpenChange={vi.fn()}
        requestScope={vi.fn().mockResolvedValue(null)}
        {...props}
      />
    </QueryClientProvider>,
  )
}

/** Answers the list endpoints the dialog loads and accepts anything written. */
function stubApi() {
  fetchMock.mockImplementation((url: string) => {
    if (url.includes('/tasks') || url.includes('/conflicts')) {
      return Promise.resolve({ ok: true, status: 200, json: async () => ({ success: true, data: [] }) })
    }
    return Promise.resolve({
      ok: true,
      status: 201,
      json: async () => ({ success: true, data: { id: 'new-event' } }),
    })
  })
}

function writeCalls() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')
}

describe('EventFormDialog', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    stubApi()
    vi.stubGlobal('fetch', fetchMock)
    stubLifeHubBridge()
  })

  it('tiêu đề rỗng hiện lỗi inline và KHÔNG gọi API', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.clear(screen.getByLabelText('Tiêu đề'))
    await user.click(screen.getByRole('button', { name: 'Tạo sự kiện' }))

    expect(await screen.findByText('Tiêu đề không được để trống')).toBeInTheDocument()
    expect(writeCalls()).toHaveLength(0)
  })

  it('kết thúc trước lúc bắt đầu bị chặn tại form, không gửi lên backend', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('Tiêu đề'), 'Họp nhóm')
    await user.clear(screen.getByLabelText('Bắt đầu'))
    await user.type(screen.getByLabelText('Bắt đầu'), '2026-09-20T10:00')
    await user.clear(screen.getByLabelText('Kết thúc'))
    await user.type(screen.getByLabelText('Kết thúc'), '2026-09-20T09:00')
    await user.click(screen.getByRole('button', { name: 'Tạo sự kiện' }))

    expect(
      await screen.findByText('Thời gian kết thúc phải sau thời gian bắt đầu'),
    ).toBeInTheDocument()
    expect(writeCalls()).toHaveLength(0)
  })

  it('bộ dựng RRULE hiện mô tả tiếng Việt của quy luật đang chọn', async () => {
    const user = userEvent.setup()
    renderForm()

    // Scoped to the summary line: the same words are also an option in the frequency select.
    expect(screen.getByText('Không lặp lại', { selector: 'p' })).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Lặp lại'), 'WEEKLY')
    await user.click(screen.getByRole('button', { name: 'T2', pressed: false }))
    await user.click(screen.getByRole('button', { name: 'T4', pressed: false }))

    expect(await screen.findByText('Hằng tuần vào thứ 2, thứ 4')).toBeInTheDocument()
  })

  it('FR-CAL-04 — sửa một instance của chuỗi lặp phải hỏi phạm vi trước khi ghi', async () => {
    const user = userEvent.setup()
    const requestScope = vi.fn().mockResolvedValue('THIS_ONLY')

    renderForm({
      event: repeatingEvent,
      occurrenceStart: '2026-09-14T09:00:00+07:00',
      requestScope,
    })

    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    await waitFor(() => expect(requestScope).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(writeCalls()).toHaveLength(1))
    expect(writeCalls()[0]![0]).toContain('/occurrences/')
    expect(JSON.parse(writeCalls()[0]![1].body)).toMatchObject({ scope: 'THIS_ONLY' })
  })

  it('hủy hộp thoại phạm vi thì không ghi gì cả', async () => {
    const user = userEvent.setup()
    const requestScope = vi.fn().mockResolvedValue(null)

    renderForm({
      event: repeatingEvent,
      occurrenceStart: '2026-09-14T09:00:00+07:00',
      requestScope,
    })

    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    await waitFor(() => expect(requestScope).toHaveBeenCalledTimes(1))
    expect(writeCalls()).toHaveLength(0)
  })

  it('sự kiện không lặp lưu thẳng, không hỏi phạm vi', async () => {
    const user = userEvent.setup()
    const requestScope = vi.fn()
    const oneOff: CalendarEvent = { ...repeatingEvent, rrule: undefined }

    renderForm({ event: oneOff, occurrenceStart: '2026-09-14T09:00:00+07:00', requestScope })

    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))

    await waitFor(() => expect(writeCalls()).toHaveLength(1))
    expect(requestScope).not.toHaveBeenCalled()
    expect(writeCalls()[0]![0]).not.toContain('/occurrences/')
  })
})
