import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TaskFormDialog } from './TaskFormDialog'
import type { Task } from '../types'

/**
 * T1-13 — the form must catch an empty title without calling the API.
 *
 * <p>The point is not that an error appears, but that no request is made: the backend rejects it
 * too, so a form that fired anyway would still "work" while wasting a round trip and showing the
 * user a toast instead of an inline message (UC-01 exception E1).
 */

const fetchMock = vi.fn()

function renderForm(props: Partial<React.ComponentProps<typeof TaskFormDialog>> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <TaskFormDialog open onOpenChange={vi.fn()} {...props} />
    </QueryClientProvider>,
  )
}

/** Responds to the list endpoints the dialog loads, and records everything else. */
function stubApi() {
  fetchMock.mockImplementation((url: string) => {
    const emptyList = { ok: true, status: 200, json: async () => ({ success: true, data: [] }) }
    if (url.includes('/projects') || url.includes('/tags')) {
      return Promise.resolve(emptyList)
    }
    return Promise.resolve({
      ok: true,
      status: 201,
      json: async () => ({ success: true, data: { id: 'new-task' } }),
    })
  })
}

function writeCalls() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')
}

describe('TaskFormDialog', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    stubApi()
    vi.stubGlobal('fetch', fetchMock)
    window.lifehub = {
      getBackendInfo: vi.fn().mockResolvedValue({ port: 51234, token: 'test-token' }),
      onBackendRestarted: vi.fn().mockReturnValue(() => {}),
    }
  })

  it('T1-13 — tiêu đề rỗng hiện lỗi inline và KHÔNG gọi API', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Tiêu đề không được để trống')
    expect(writeCalls()).toHaveLength(0)
  })

  it('T1-13 — tiêu đề chỉ có khoảng trắng cũng bị chặn', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText(/Tiêu đề/), '    ')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(writeCalls()).toHaveLength(0)
  })

  it('Tiêu đề hợp lệ thì gửi POST /tasks với đúng dữ liệu', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    renderForm({ onOpenChange })

    await user.type(screen.getByLabelText(/Tiêu đề/), 'Hoàn thiện SRS')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    await waitFor(() => expect(writeCalls()).toHaveLength(1))

    const [url, init] = writeCalls()[0]!
    expect(url).toContain('/api/v1/tasks')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toMatchObject({ title: 'Hoàn thiện SRS', priority: 'MEDIUM' })
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it('Ước lượng thời gian bằng 0 bị chặn trước khi gọi API', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText(/Tiêu đề/), 'Task hợp lệ')
    await user.type(screen.getByLabelText(/Ước lượng/), '0')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('lớn hơn 0')
    expect(writeCalls()).toHaveLength(0)
  })

  it('UC-01 E2 — ngày đến hạn ở quá khứ chỉ cảnh báo, vẫn cho lưu', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText(/Tiêu đề/), 'Nộp báo cáo')
    await user.type(screen.getByLabelText(/Ngày đến hạn/), '2020-01-01T09:00')

    expect(await screen.findByText(/Ngày đến hạn đã qua/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Lưu' }))
    await waitFor(() => expect(writeCalls()).toHaveLength(1))
  })

  it('Ctrl+Enter lưu form mà không cần bấm nút', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText(/Tiêu đề/), 'Lưu bằng phím tắt')
    await user.keyboard('{Control>}{Enter}{/Control}')

    await waitFor(() => expect(writeCalls()).toHaveLength(1))
  })

  it('Mở ở chế độ sửa thì điền sẵn dữ liệu và gửi PATCH', async () => {
    const user = userEvent.setup()
    const task: Task = {
      id: 'task-1',
      title: 'Task đang sửa',
      status: 'TODO',
      priority: 'HIGH',
      isOverdue: false,
      tags: [],
      subtaskCount: 0,
      completedSubtaskCount: 0,
      sortOrder: 0,
      createdAt: '2026-09-08T10:00:00+07:00',
      updatedAt: '2026-09-08T10:00:00+07:00',
    }
    renderForm({ task })

    expect(screen.getByLabelText(/Tiêu đề/)).toHaveValue('Task đang sửa')

    await user.clear(screen.getByLabelText(/Tiêu đề/))
    await user.type(screen.getByLabelText(/Tiêu đề/), 'Tiêu đề mới')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    await waitFor(() => expect(writeCalls()).toHaveLength(1))
    const [url, init] = writeCalls()[0]!
    expect(url).toContain('/tasks/task-1')
    expect(init.method).toBe('PATCH')
  })
})
