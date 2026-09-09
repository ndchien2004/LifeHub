import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { stubLifeHubBridge } from '@/test/lifehubBridge'
import { CommandPalette } from './CommandPalette'

/**
 * UC-09 end to end in the renderer.
 *
 * <p>The assertion that matters most is the one about Escape: the acceptance criterion for this
 * phase is that closing the prefilled form writes nothing, and the way to prove that from here is to
 * show that no request other than the parse itself was ever issued.
 */

const fetchMock = vi.fn()

const WALLETS = {
  wallets: [
    {
      id: 'w1',
      name: 'Tiền mặt',
      type: 'CASH',
      initialBalance: 0,
      balance: 0,
      currency: 'VND',
      isDefault: true,
      sortOrder: 0,
      createdAt: '2026-09-01T00:00:00+07:00',
    },
  ],
  totalAssets: 0,
}

const CATEGORIES = [
  {
    id: 'c1',
    name: 'Ăn uống',
    type: 'EXPENSE',
    color: '#f59e0b',
    isSystem: true,
    sortOrder: 0,
    children: [],
  },
]

const AI_STATUS = { enabled: true, configured: true, model: 'claude-opus-5', available: true }

const TRANSACTION_RESULT = {
  intent: 'TRANSACTION',
  confidence: 0.94,
  source: 'AI',
  transaction: {
    type: 'EXPENSE',
    amount: 45000,
    categoryId: 'c1',
    categoryName: 'Ăn uống',
    walletId: 'w1',
    walletName: 'Tiền mặt',
    note: 'Cơm gà, ăn cùng team',
    occurredAt: '2026-09-11T12:00:00+07:00',
    fieldConfidence: { amount: 0.99, categoryName: 0.87, occurredAt: 0.7 },
  },
  task: null,
  event: null,
  warning: null,
}

function renderPalette() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <CommandPalette />
    </QueryClientProvider>,
  )
}

/** Every non-GET call, i.e. everything that could have changed data. */
function writeCalls() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')
}

function parseCalls() {
  return fetchMock.mock.calls.filter(([url]) => String(url).includes('/ai/parse'))
}

function stubApi(parseResult: unknown = TRANSACTION_RESULT, status = AI_STATUS) {
  fetchMock.mockImplementation((url: string) => {
    const respond = (data: unknown) =>
      Promise.resolve({ ok: true, status: 200, json: async () => ({ success: true, data }) })

    if (url.includes('/ai/status')) return respond(status)
    if (url.includes('/ai/parse')) return respond(parseResult)
    if (url.includes('/ai/suggest-category')) return respond({ suggestions: [] })
    if (url.includes('/wallets')) return respond(WALLETS)
    if (url.includes('/categories')) return respond(url.includes('INCOME') ? [] : CATEGORIES)
    if (url.includes('/tags')) return respond([])
    if (url.includes('/projects')) return respond([])
    return respond({ transaction: { id: 'new' }, walletBalance: 0 })
  })
}

/** Opens the palette the way a user does, from anywhere in the app. */
async function openPalette(user: ReturnType<typeof userEvent.setup>) {
  await user.keyboard('{Control>}{ }{/Control}')
  await waitFor(() => expect(screen.getByLabelText('Câu cần phân tích')).toBeInTheDocument())
}

describe('CommandPalette', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    stubApi()
    vi.stubGlobal('fetch', fetchMock)
    stubLifeHubBridge()
  })

  it('FR-AI-01 — Ctrl+Space mở ô nhập nhanh và focus sẵn', async () => {
    const user = userEvent.setup()
    renderPalette()

    expect(screen.queryByLabelText('Câu cần phân tích')).not.toBeInTheDocument()

    await openPalette(user)
    expect(screen.getByLabelText('Câu cần phân tích')).toHaveFocus()
  })

  it('FR-AI-06 — kết quả AI mở form giao dịch đã điền sẵn', async () => {
    const user = userEvent.setup()
    renderPalette()
    await openPalette(user)

    await user.type(screen.getByLabelText('Câu cần phân tích'), 'ăn trưa cơm gà 45k')
    await user.keyboard('{Enter}')

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    expect(screen.getByLabelText('Số tiền')).toHaveValue('45.000')
    expect(screen.getByLabelText(/^Danh mục/)).toHaveValue('c1')
    expect(screen.getByLabelText('Ghi chú')).toHaveValue('Cơm gà, ăn cùng team')
  })

  it('UC-09 step 10 — trường do AI suy ra có badge kèm độ tin cậy', async () => {
    const user = userEvent.setup()
    renderPalette()
    await openPalette(user)

    await user.type(screen.getByLabelText('Câu cần phân tích'), 'ăn trưa cơm gà 45k')
    await user.keyboard('{Enter}')

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    expect(screen.getByText('AI 99%')).toBeInTheDocument()
    expect(screen.getByText('AI 87%')).toBeInTheDocument()
  })

  it('UC-09 10a — trường có confidence < 0.6 để trống và được highlight', async () => {
    const user = userEvent.setup()
    stubApi({
      ...TRANSACTION_RESULT,
      transaction: {
        ...TRANSACTION_RESULT.transaction,
        fieldConfidence: { amount: 0.99, categoryName: 0.3 },
      },
    })
    renderPalette()
    await openPalette(user)

    await user.type(screen.getByLabelText('Câu cần phân tích'), 'mua gì đó 45k')
    await user.keyboard('{Enter}')

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    expect(screen.getByLabelText(/^Danh mục/))
      .toHaveValue('')
    expect(screen.getByText('Cần kiểm tra')).toBeInTheDocument()
  })

  it('**Esc ở form kết quả không tạo bản ghi nào** — chỉ có đúng một lời gọi /ai/parse', async () => {
    const user = userEvent.setup()
    renderPalette()
    await openPalette(user)

    await user.type(screen.getByLabelText('Câu cần phân tích'), 'ăn trưa cơm gà 45k')
    await user.keyboard('{Enter}')

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    await user.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByLabelText('Số tiền')).not.toBeInTheDocument())

    expect(parseCalls()).toHaveLength(1)
    expect(writeCalls().filter(([url]) => !String(url).includes('/ai/'))).toHaveLength(0)
  })

  it('FR-AI-08 — kết quả rule-based hiện banner chế độ ngoại tuyến', async () => {
    const user = userEvent.setup()
    stubApi({ ...TRANSACTION_RESULT, source: 'RULE', warning: 'TIMEOUT' })
    renderPalette()
    await openPalette(user)

    await user.type(screen.getByLabelText('Câu cần phân tích'), 'ăn trưa cơm gà 45k')
    await user.keyboard('{Enter}')

    expect(await screen.findByText(/chế độ ngoại tuyến/i)).toBeInTheDocument()
    expect(screen.getByText(/Không kết nối được dịch vụ AI/)).toBeInTheDocument()
  })

  it('FR-AI-12 — AI tắt thì ô nhập nhanh báo đang dùng bộ luật ngoại tuyến', async () => {
    const user = userEvent.setup()
    stubApi(TRANSACTION_RESULT, { ...AI_STATUS, enabled: false, available: false })
    renderPalette()

    await openPalette(user)

    expect(await screen.findByText(/AI đang tắt/)).toBeInTheDocument()
  })

  it('UC-09 9a — intent UNKNOWN hỏi lại người dùng muốn tạo gì', async () => {
    const user = userEvent.setup()
    stubApi({
      intent: 'UNKNOWN',
      confidence: 0,
      source: 'AI',
      transaction: null,
      task: null,
      event: null,
      warning: null,
    })
    renderPalette()
    await openPalette(user)

    await user.type(screen.getByLabelText('Câu cần phân tích'), 'xyz')
    await user.keyboard('{Enter}')

    expect(await screen.findByText('Mình chưa hiểu ý bạn. Bạn muốn tạo gì?')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Công việc/ }))

    await waitFor(() => expect(screen.getByLabelText(/Tiêu đề/)).toBeInTheDocument())
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveValue('xyz')
    expect(writeCalls().filter(([url]) => !String(url).includes('/ai/'))).toHaveLength(0)
  })
})
