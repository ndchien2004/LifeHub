import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { stubLifeHubBridge } from '@/test/lifehubBridge'
import { TransactionFormDialog } from './TransactionFormDialog'
import type { Transaction } from '../types'

/**
 * The transaction form has to stop the two mistakes UC-06 names as exception flows before any
 * request leaves the renderer, and it has to send a plain integer amount however the user typed it
 * (T3-16, FR-FIN-06).
 */

const fetchMock = vi.fn()

const WALLETS = {
  wallets: [
    { id: 'w1', name: 'Tiền mặt', type: 'CASH', initialBalance: 0, balance: 0, currency: 'VND', isDefault: true, sortOrder: 0, createdAt: '2026-09-01T00:00:00+07:00' },
    { id: 'w2', name: 'Vietcombank', type: 'BANK', initialBalance: 0, balance: 0, currency: 'VND', isDefault: false, sortOrder: 1, createdAt: '2026-09-01T00:00:00+07:00' },
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
    children: [
      { id: 'c2', parentId: 'c1', name: 'Cà phê', type: 'EXPENSE', color: '#f59e0b', isSystem: true, sortOrder: 1 },
    ],
  },
]

function renderForm(props: Partial<React.ComponentProps<typeof TransactionFormDialog>> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <TransactionFormDialog open onOpenChange={vi.fn()} {...props} />
    </QueryClientProvider>,
  )
}

function stubApi() {
  fetchMock.mockImplementation((url: string) => {
    const respond = (data: unknown, status = 200) =>
      Promise.resolve({ ok: true, status, json: async () => ({ success: true, data }) })

    if (url.includes('/wallets')) {
      return respond(WALLETS)
    }
    if (url.includes('/categories')) {
      return respond(url.includes('INCOME') ? [] : CATEGORIES)
    }
    return respond({ transaction: { id: 'new-transaction' }, walletBalance: 0 }, 201)
  })
}

function writeCalls() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')
}

describe('TransactionFormDialog', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    stubApi()
    vi.stubGlobal('fetch', fetchMock)
    stubLifeHubBridge()
  })

  it('T3-16 — gõ 1500000 hiển thị 1.500.000 nhưng gửi lên API là 1500000', async () => {
    const user = userEvent.setup()
    renderForm()

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    await user.type(screen.getByLabelText('Số tiền'), '1500000')
    expect(screen.getByLabelText('Số tiền')).toHaveValue('1.500.000')

    await waitFor(() =>
      expect(screen.getByLabelText('Danh mục')).toHaveDisplayValue(['— Chọn danh mục —']),
    )
    await user.selectOptions(screen.getByLabelText('Danh mục'), 'c1')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    await waitFor(() => expect(writeCalls()).toHaveLength(1))
    const [url, init] = writeCalls()[0]!
    expect(url).toContain('/api/v1/transactions')
    expect(JSON.parse(init.body)).toMatchObject({ amount: 1500000, type: 'EXPENSE', categoryId: 'c1' })
  })

  it('UC-06 E1 — số tiền bằng 0 hiện lỗi inline và KHÔNG gọi API', async () => {
    const user = userEvent.setup()
    renderForm()

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    expect(await screen.findByText('Số tiền phải lớn hơn 0')).toBeInTheDocument()
    expect(writeCalls()).toHaveLength(0)
  })

  it('UC-06 E2 — chuyển khoản cùng một ví bị chặn trước khi gọi API', async () => {
    const user = userEvent.setup()
    renderForm()

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    await user.click(screen.getByRole('radio', { name: 'Chuyển khoản' }))

    await user.type(screen.getByLabelText('Số tiền'), '100000')
    await user.selectOptions(screen.getByLabelText('Ví nguồn'), 'w1')
    await user.selectOptions(screen.getByLabelText('Ví đích'), 'w1')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    expect(await screen.findByText('Ví nguồn và ví đích phải khác nhau')).toBeInTheDocument()
    expect(writeCalls()).toHaveLength(0)
  })

  it('UC-06 5a — chọn TRANSFER thì form đổi sang ví đích và ẩn ô danh mục', async () => {
    const user = userEvent.setup()
    renderForm()

    await waitFor(() => expect(screen.getByLabelText('Danh mục')).toBeInTheDocument())

    await user.click(screen.getByRole('radio', { name: 'Chuyển khoản' }))

    expect(screen.getByLabelText('Ví đích')).toBeInTheDocument()
    expect(screen.queryByLabelText('Danh mục')).not.toBeInTheDocument()
  })

  it('Giao dịch thu/chi thiếu danh mục bị chặn', async () => {
    const user = userEvent.setup()
    renderForm()

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    await user.type(screen.getByLabelText('Số tiền'), '45000')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    expect(await screen.findByText('Hãy chọn danh mục')).toBeInTheDocument()
    expect(writeCalls()).toHaveLength(0)
  })

  it('Mở ở chế độ sửa thì điền sẵn dữ liệu và gửi PATCH', async () => {
    const user = userEvent.setup()
    const transaction: Transaction = {
      id: 'txn-1',
      type: 'EXPENSE',
      amount: 45000,
      wallet: { id: 'w1', name: 'Tiền mặt' },
      category: { id: 'c1', name: 'Ăn uống', color: '#f59e0b' },
      note: 'Cơm gà',
      occurredAt: '2026-09-08T12:15:00+07:00',
      source: 'MANUAL',
      tags: [],
      createdAt: '2026-09-08T12:15:00+07:00',
      updatedAt: '2026-09-08T12:15:00+07:00',
    }
    renderForm({ transaction })

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toHaveValue('45.000'))

    await user.clear(screen.getByLabelText('Số tiền'))
    await user.type(screen.getByLabelText('Số tiền'), '250000')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    await waitFor(() => expect(writeCalls()).toHaveLength(1))
    const [url, init] = writeCalls()[0]!
    expect(url).toContain('/transactions/txn-1')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toMatchObject({ amount: 250000 })
  })

  it('Ctrl+Enter lưu form mà không cần bấm nút', async () => {
    const user = userEvent.setup()
    renderForm()

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    await user.type(screen.getByLabelText('Số tiền'), '99000')
    await waitFor(() => expect(screen.getByLabelText('Danh mục')).toBeInTheDocument())
    await user.selectOptions(screen.getByLabelText('Danh mục'), 'c2')
    await user.keyboard('{Control>}{Enter}{/Control}')

    await waitFor(() => expect(writeCalls()).toHaveLength(1))
  })

  it('Lưu thất bại thì giữ nguyên form và dữ liệu đã nhập, không đóng dialog', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    // The backend refuses the write; the dialog has to survive it with the input intact, and the
    // rejection must not escape as an unhandled promise.
    fetchMock.mockImplementation((url: string, init?: { method?: string }) => {
      const respond = (data: unknown) =>
        Promise.resolve({ ok: true, status: 200, json: async () => ({ success: true, data }) })

      if (url.includes('/wallets')) return respond(WALLETS)
      if (url.includes('/categories')) return respond(url.includes('INCOME') ? [] : CATEGORIES)
      if (url.includes('/tags')) return respond([])
      if (init?.method === 'POST') {
        return Promise.resolve({
          ok: false,
          status: 409,
          json: async () => ({
            success: false,
            error: { code: 'CONFLICT', message: 'Ví không tồn tại' },
          }),
        })
      }
      return respond({})
    })

    renderForm({ onOpenChange })

    await waitFor(() => expect(screen.getByLabelText('Số tiền')).toBeInTheDocument())
    await user.type(screen.getByLabelText('Số tiền'), '75000')
    await waitFor(() => expect(screen.getByLabelText('Danh mục')).toBeInTheDocument())
    await user.selectOptions(screen.getByLabelText('Danh mục'), 'c1')
    await user.click(screen.getByRole('button', { name: 'Lưu' }))

    await waitFor(() => expect(writeCalls()).toHaveLength(1))
    expect(onOpenChange).not.toHaveBeenCalledWith(false)
    expect(screen.getByLabelText('Số tiền')).toHaveValue('75.000')
  })
})
