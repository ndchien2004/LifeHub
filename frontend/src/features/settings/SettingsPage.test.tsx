import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { stubLifeHubBridge } from '@/test/lifehubBridge'
import { SettingsPage } from './SettingsPage'

/**
 * FR-SYS-09, FR-AI-09 and FR-AI-12 from the user's side.
 *
 * <p>The important assertion is negative: the API key must never travel to {@code PUT /settings}. It
 * goes over the Electron bridge to the OS credential store, and this test would catch a refactor
 * that quietly turned it into just another setting.
 */

const fetchMock = vi.fn()

const SETTINGS = {
  settings: {
    'app.theme': 'SYSTEM',
    'app.timezone': 'Asia/Ho_Chi_Minh',
    'app.week_start': 'MONDAY',
    'app.currency': 'VND',
    'ai.enabled': 'true',
    'ai.model': 'claude-opus-5',
  },
  requiresRestart: false,
}

const AI_STATUS = { enabled: true, configured: true, model: 'claude-opus-5', available: true }

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <SettingsPage />
    </QueryClientProvider>,
  )
}

function stubApi(overrides: { settings?: unknown; status?: unknown } = {}) {
  fetchMock.mockImplementation((url: string, init?: RequestInit) => {
    const respond = (data: unknown) =>
      Promise.resolve({ ok: true, status: 200, json: async () => ({ success: true, data }) })

    if (url.includes('/ai/status')) return respond(overrides.status ?? AI_STATUS)
    if (url.includes('/ai/logs')) return respond({ items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 })
    if (url.includes('/settings')) {
      if (init?.method === 'PUT') {
        const body = JSON.parse(String(init.body)) as Record<string, string>
        return respond({
          settings: { ...SETTINGS.settings, ...body },
          requiresRestart: 'app.timezone' in body,
        })
      }
      return respond(overrides.settings ?? SETTINGS)
    }
    return respond({})
  })
}

function settingsWrites() {
  return fetchMock.mock.calls.filter(
    ([url, init]) => String(url).includes('/settings') && init?.method === 'PUT',
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    stubApi()
    vi.stubGlobal('fetch', fetchMock)
    stubLifeHubBridge()
  })

  it('hiển thị giá trị hiện tại của các cài đặt chung', async () => {
    renderPage()

    await waitFor(() => expect(screen.getByLabelText('Múi giờ')).toBeInTheDocument())
    expect(screen.getByLabelText('Múi giờ')).toHaveValue('Asia/Ho_Chi_Minh')
    expect(screen.getByLabelText('Ngày bắt đầu tuần')).toHaveValue('MONDAY')
    expect(screen.getByLabelText('Tiền tệ')).toHaveValue('VND')
  })

  it('đổi tiền tệ gửi PUT /settings với đúng khóa đã đổi', async () => {
    const user = userEvent.setup()
    renderPage()

    await waitFor(() => expect(screen.getByLabelText('Tiền tệ')).toBeInTheDocument())
    await user.selectOptions(screen.getByLabelText('Tiền tệ'), 'USD')

    await waitFor(() => expect(settingsWrites()).toHaveLength(1))
    expect(JSON.parse(String(settingsWrites()[0]![1].body))).toEqual({ 'app.currency': 'USD' })
  })

  it('FR-AI-12 — tắt AI gửi ai.enabled=false', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'AI' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Bật phân tích bằng AI')).toBeInTheDocument(),
    )

    await user.click(screen.getByLabelText('Bật phân tích bằng AI'))

    await waitFor(() => expect(settingsWrites()).toHaveLength(1))
    expect(JSON.parse(String(settingsWrites()[0]![1].body))).toEqual({ 'ai.enabled': 'false' })
  })

  it('FR-AI-09 — API key đi qua cầu Electron, KHÔNG bao giờ qua PUT /settings', async () => {
    const user = userEvent.setup()
    const bridge = stubLifeHubBridge()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'AI' }))
    const field = await screen.findByLabelText('Nhập API key')

    await user.type(field, 'sk-ant-secret-value')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    await waitFor(() => expect(bridge.setApiKey).toHaveBeenCalledWith('sk-ant-secret-value'))

    expect(settingsWrites()).toHaveLength(0)
    const everyBody = fetchMock.mock.calls
      .map(([, init]) => String(init?.body ?? ''))
      .join(' ')
    expect(everyBody).not.toContain('sk-ant-secret-value')
  })

  it('API key được che theo mặc định, bấm mới hiện', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'AI' }))
    const field = await screen.findByLabelText('Nhập API key')
    expect(field).toHaveAttribute('type', 'password')

    await user.click(screen.getByRole('button', { name: 'Hiện API key' }))
    expect(screen.getByLabelText('Nhập API key')).toHaveAttribute('type', 'text')
  })

  it('Chưa có key thì nút Kiểm tra kết nối bị vô hiệu hóa', async () => {
    stubApi({ status: { ...AI_STATUS, configured: false, available: false } })
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'AI' }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Kiểm tra kết nối/ })).toBeDisabled(),
    )
    expect(screen.getByText(/Chưa có API key/)).toBeInTheDocument()
  })
})
