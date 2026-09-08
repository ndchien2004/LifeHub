import { useEffect, useMemo, useState } from 'react'
import { Plus } from 'lucide-react'
import { Button } from '@/shared/components/ui/button'
import { Select } from '@/shared/components/ui/input'
import { formatCurrency } from '@/shared/lib/formatters'
import { cn } from '@/shared/lib/utils'
import { BudgetManager } from './components/BudgetManager'
import { CategoryManager } from './components/CategoryManager'
import { SpendingPieChart, TrendLineChart } from './components/FinanceCharts'
import { RecurringRuleManager } from './components/RecurringRuleManager'
import { TransactionFilterBar } from './components/TransactionFilterBar'
import { TransactionFormDialog } from './components/TransactionFormDialog'
import { TransactionList } from './components/TransactionList'
import { WalletManager } from './components/WalletManager'
import { useDeleteTransaction, useSummary, useTransactions, useWallets } from './hooks'
import type { SummaryGroupBy, Transaction, TransactionQuery } from './types'

type Tab = 'transactions' | 'reports' | 'budgets' | 'wallets' | 'categories' | 'recurring'

const TABS: { id: Tab; label: string }[] = [
  { id: 'transactions', label: 'Giao dịch' },
  { id: 'reports', label: 'Báo cáo' },
  { id: 'budgets', label: 'Ngân sách' },
  { id: 'wallets', label: 'Ví' },
  { id: 'categories', label: 'Danh mục' },
  { id: 'recurring', label: 'Định kỳ' },
]

/**
 * The finance screen (FR-FIN-01 → FR-FIN-13).
 *
 * <p>Tabs rather than separate routes: the app has no router by design (see {@code navStore}), and
 * all six views are facets of one ledger the user moves between constantly.
 *
 * <p>Pressing {@code N} opens the transaction form, matching the shortcut the task screen already
 * uses for the same "create the main thing on this page" action.
 */
export function FinancePage() {
  const [tab, setTab] = useState<Tab>('transactions')
  const [query, setQuery] = useState<TransactionQuery>({})
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Transaction | null>(null)

  const { data: walletList } = useWallets()
  const hasWallet = (walletList?.wallets.length ?? 0) > 0

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const typing =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.tagName === 'SELECT' ||
        target?.isContentEditable
      if (typing || event.metaKey || event.ctrlKey || event.altKey) {
        return
      }
      if (event.key === 'n' || event.key === 'N') {
        event.preventDefault()
        setEditing(null)
        setFormOpen(true)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between gap-4 border-b border-border px-6 py-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Tài chính</h1>
          <p className="text-sm text-muted-foreground">
            Sổ thu chi, ngân sách và biểu đồ. Số tiền tính bằng đồng.
          </p>
        </div>
        <Button
          disabled={!hasWallet}
          title={hasWallet ? 'Thêm giao dịch (phím N)' : 'Hãy tạo ví trước'}
          onClick={() => {
            setEditing(null)
            setFormOpen(true)
          }}
        >
          <Plus className="h-4 w-4" aria-hidden />
          Thêm giao dịch
        </Button>
      </header>

      <nav className="flex gap-1 border-b border-border px-6" aria-label="Mục tài chính">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-current={tab === item.id ? 'page' : undefined}
            onClick={() => setTab(item.id)}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              tab === item.id
                ? 'border-primary text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {item.label}
          </button>
        ))}
      </nav>

      <div className="flex-1 overflow-y-auto p-6">
        {!hasWallet && tab !== 'wallets' && tab !== 'categories' ? (
          <div className="rounded-md border border-dashed border-border p-8 text-center">
            <p className="mb-1 text-sm font-medium">Chưa có ví nào</p>
            <p className="mb-4 text-sm text-muted-foreground">
              Mọi giao dịch đều thuộc về một ví, nên hãy tạo ví đầu tiên trước.
            </p>
            <Button onClick={() => setTab('wallets')}>Đi tới quản lý ví</Button>
          </div>
        ) : (
          <>
            {tab === 'transactions' && (
              <TransactionsTab
                query={query}
                onQueryChange={setQuery}
                onEdit={(transaction) => {
                  setEditing(transaction)
                  setFormOpen(true)
                }}
              />
            )}
            {tab === 'reports' && <ReportsTab />}
            {tab === 'budgets' && <BudgetManager />}
            {tab === 'wallets' && <WalletManager />}
            {tab === 'categories' && <CategoryManager />}
            {tab === 'recurring' && <RecurringRuleManager />}
          </>
        )}
      </div>

      <TransactionFormDialog open={formOpen} onOpenChange={setFormOpen} transaction={editing} />
    </div>
  )
}

function TransactionsTab({
  query,
  onQueryChange,
  onEdit,
}: {
  query: TransactionQuery
  onQueryChange: (query: TransactionQuery) => void
  onEdit: (transaction: Transaction) => void
}) {
  const { data, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useTransactions(query)
  const deleteTransaction = useDeleteTransaction()

  const transactions = useMemo(
    () => (data?.pages ?? []).flatMap((page) => page.items),
    [data],
  )
  const totalItems = data?.pages[0]?.totalItems ?? 0

  return (
    <div className="space-y-4">
      <TransactionFilterBar query={query} onChange={onQueryChange} />

      <p className="text-xs text-muted-foreground">
        {isPending ? 'Đang tải giao dịch…' : `${totalItems} giao dịch`}
      </p>

      <TransactionList
        transactions={transactions}
        onEdit={onEdit}
        onDelete={(transaction) => deleteTransaction.mutate(transaction)}
      />

      {hasNextPage && (
        <div className="flex justify-center pt-2">
          <Button
            variant="outline"
            onClick={() => void fetchNextPage()}
            disabled={isFetchingNextPage}
          >
            {isFetchingNextPage ? 'Đang tải…' : 'Tải thêm'}
          </Button>
        </div>
      )}
    </div>
  )
}

/** Preset ranges, because "tháng này" is what the user actually asks for. */
const RANGES = [
  { id: 'THIS_MONTH', label: 'Tháng này' },
  { id: 'LAST_MONTH', label: 'Tháng trước' },
  { id: 'THIS_YEAR', label: 'Năm nay' },
  { id: 'LAST_90', label: '90 ngày gần nhất' },
] as const

type RangeId = (typeof RANGES)[number]['id']

function ReportsTab() {
  const [range, setRange] = useState<RangeId>('THIS_MONTH')
  const [groupBy, setGroupBy] = useState<SummaryGroupBy>('DAY')

  const { from, to } = useMemo(() => resolveRange(range), [range])

  const pie = useSummary({ from, to, groupBy: 'CATEGORY', type: 'EXPENSE' })
  const expenseTrend = useSummary({ from, to, groupBy, type: 'EXPENSE' })
  const incomeTrend = useSummary({ from, to, groupBy, type: 'INCOME' })

  const totals = pie.data

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <Select
          aria-label="Khoảng thời gian"
          className="w-48"
          value={range}
          onChange={(event) => setRange(event.target.value as RangeId)}
        >
          {RANGES.map((option) => (
            <option key={option.id} value={option.id}>
              {option.label}
            </option>
          ))}
        </Select>

        <Select
          aria-label="Nhóm theo"
          className="w-40"
          value={groupBy}
          onChange={(event) => setGroupBy(event.target.value as SummaryGroupBy)}
        >
          <option value="DAY">Theo ngày</option>
          <option value="WEEK">Theo tuần</option>
          <option value="MONTH">Theo tháng</option>
        </Select>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryCard label="Tổng thu" value={totals?.totalIncome ?? 0} tone="income" />
        <SummaryCard label="Tổng chi" value={totals?.totalExpense ?? 0} tone="expense" />
        <SummaryCard label="Chênh lệch" value={totals?.net ?? 0} tone="net" />
      </div>

      <section className="rounded-md border border-border bg-card p-4">
        <h2 className="mb-3 text-sm font-semibold">Cơ cấu chi tiêu theo danh mục</h2>
        {pie.data ? <SpendingPieChart summary={pie.data} /> : <ChartSkeleton />}
      </section>

      <section className="rounded-md border border-border bg-card p-4">
        <h2 className="mb-3 text-sm font-semibold">Xu hướng thu và chi</h2>
        {expenseTrend.data && incomeTrend.data ? (
          <TrendLineChart expense={expenseTrend.data} income={incomeTrend.data} />
        ) : (
          <ChartSkeleton />
        )}
      </section>
    </div>
  )
}

function SummaryCard({
  label,
  value,
  tone,
}: {
  label: string
  value: number
  tone: 'income' | 'expense' | 'net'
}) {
  return (
    <div className="rounded-md border border-border bg-card p-4">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p
        className={cn(
          'text-xl font-semibold tabular-nums',
          tone === 'income' && 'text-emerald-600 dark:text-emerald-400',
          tone === 'expense' && 'text-red-600 dark:text-red-400',
          tone === 'net' && value < 0 && 'text-red-600 dark:text-red-400',
        )}
      >
        {formatCurrency(value)}
      </p>
    </div>
  )
}

function ChartSkeleton() {
  return (
    <div className="flex h-72 items-center justify-center">
      <p className="text-sm text-muted-foreground">Đang tải biểu đồ…</p>
    </div>
  )
}

/** Local calendar ranges, half-open so the upper bound never double-counts a boundary day. */
function resolveRange(range: RangeId): { from: string; to: string } {
  const now = new Date()
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1)

  switch (range) {
    case 'LAST_MONTH': {
      const start = new Date(now.getFullYear(), now.getMonth() - 1, 1)
      return { from: start.toISOString(), to: startOfMonth.toISOString() }
    }
    case 'THIS_YEAR': {
      const start = new Date(now.getFullYear(), 0, 1)
      const end = new Date(now.getFullYear() + 1, 0, 1)
      return { from: start.toISOString(), to: end.toISOString() }
    }
    case 'LAST_90': {
      const start = new Date(now)
      start.setDate(start.getDate() - 90)
      start.setHours(0, 0, 0, 0)
      const end = new Date(now)
      end.setDate(end.getDate() + 1)
      end.setHours(0, 0, 0, 0)
      return { from: start.toISOString(), to: end.toISOString() }
    }
    default: {
      const end = new Date(now.getFullYear(), now.getMonth() + 1, 1)
      return { from: startOfMonth.toISOString(), to: end.toISOString() }
    }
  }
}
