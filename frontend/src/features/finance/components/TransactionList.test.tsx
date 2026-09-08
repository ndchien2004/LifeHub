import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { TransactionList } from './TransactionList'
import type { Transaction } from '../types'

/**
 * The ledger has to read correctly at a glance: right sign, right grouping, and a day total that
 * does not count transfers as spending.
 */

function transaction(overrides: Partial<Transaction> & Pick<Transaction, 'id' | 'type' | 'amount'>): Transaction {
  return {
    wallet: { id: 'w1', name: 'Tiền mặt' },
    note: undefined,
    occurredAt: '2026-09-08T12:00:00+07:00',
    source: 'MANUAL',
    tags: [],
    createdAt: '2026-09-08T12:00:00+07:00',
    updatedAt: '2026-09-08T12:00:00+07:00',
    ...overrides,
  } as Transaction
}

describe('TransactionList', () => {
  it('Chi hiện dấu trừ, thu hiện dấu cộng, chuyển khoản không có dấu', () => {
    render(
      <TransactionList
        transactions={[
          transaction({ id: 't1', type: 'EXPENSE', amount: 45000, category: { id: 'c1', name: 'Cà phê', color: '#f59e0b' } }),
          transaction({ id: 't2', type: 'INCOME', amount: 15000000, category: { id: 'c2', name: 'Lương', color: '#22c55e' } }),
          transaction({ id: 't3', type: 'TRANSFER', amount: 500000, toWallet: { id: 'w2', name: 'Vietcombank' } }),
        ]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('−45.000 ₫')).toBeInTheDocument()
    expect(screen.getByText('+15.000.000 ₫')).toBeInTheDocument()
    expect(screen.getByText('500.000 ₫')).toBeInTheDocument()
  })

  it('Tổng ròng của ngày bỏ qua chuyển khoản — chuyển tiền không phải là chi', () => {
    render(
      <TransactionList
        transactions={[
          transaction({ id: 't1', type: 'EXPENSE', amount: 200000, category: { id: 'c1', name: 'Cà phê', color: '#f59e0b' } }),
          transaction({ id: 't2', type: 'TRANSFER', amount: 9000000, toWallet: { id: 'w2', name: 'Vietcombank' } }),
        ]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    // The row and the day total show the same figure — which is the point: the 9.000.000 ₫
    // transfer moved money between the user's own wallets and must not read as spending.
    expect(screen.getAllByText('−200.000 ₫')).toHaveLength(2)
    expect(screen.queryByText('−9.200.000 ₫')).not.toBeInTheDocument()
  })

  it('Giao dịch được gom theo ngày', () => {
    render(
      <TransactionList
        transactions={[
          transaction({ id: 't1', type: 'EXPENSE', amount: 1000, category: { id: 'c1', name: 'Cà phê', color: '#f59e0b' }, occurredAt: '2026-09-08T12:00:00+07:00' }),
          transaction({ id: 't2', type: 'EXPENSE', amount: 2000, category: { id: 'c1', name: 'Cà phê', color: '#f59e0b' }, occurredAt: '2026-09-07T12:00:00+07:00' }),
        ]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByRole('heading', { name: '08/09/2026' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '07/09/2026' })).toBeInTheDocument()
  })

  it('Nhãn của giao dịch hiển thị dưới dòng ghi chú (FR-FIN-04)', () => {
    render(
      <TransactionList
        transactions={[
          transaction({
            id: 't1',
            type: 'EXPENSE',
            amount: 45000,
            category: { id: 'c1', name: 'Cà phê', color: '#f59e0b' },
            tags: [{ id: 'tag1', name: 'công tác', color: '#3b82f6' }],
          }),
        ]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('công tác')).toBeInTheDocument()
  })

  it('Danh sách rỗng hiện thông báo thay vì khoảng trắng', () => {
    render(<TransactionList transactions={[]} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText(/Chưa có giao dịch nào khớp bộ lọc/)).toBeInTheDocument()
  })
})
