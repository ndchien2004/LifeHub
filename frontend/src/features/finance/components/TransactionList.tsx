import { ArrowRight, Bot, Pencil, Repeat, Trash2 } from 'lucide-react'
import { Fragment } from 'react'
import { Button } from '@/shared/components/ui/button'
import { formatDate, formatTime } from '@/shared/lib/dateUtils'
import { formatCurrency } from '@/shared/lib/formatters'
import { cn } from '@/shared/lib/utils'
import { signOf } from '../lib/amount'
import { TYPE_CLASSES, type Transaction } from '../types'

interface TransactionListProps {
  transactions: Transaction[]
  onEdit: (transaction: Transaction) => void
  onDelete: (transaction: Transaction) => void
}

/**
 * The ledger, grouped by day (FR-FIN-12).
 *
 * <p>Each day carries its own net total, because "what did today cost me" is the question this
 * screen is actually asked. Transfers are excluded from that subtotal: moving money between your
 * own wallets is not spending, and counting it would make an ordinary day look catastrophic.
 */
export function TransactionList({ transactions, onEdit, onDelete }: TransactionListProps) {
  const days = groupByDay(transactions)

  if (days.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
        Chưa có giao dịch nào khớp bộ lọc.
      </p>
    )
  }

  return (
    <div className="space-y-6">
      {days.map(([day, items]) => (
        <section key={day} aria-label={`Giao dịch ngày ${day}`}>
          <header className="mb-2 flex items-baseline justify-between border-b border-border pb-1.5">
            <h3 className="text-sm font-semibold">{day}</h3>
            <span className="text-xs tabular-nums text-muted-foreground">{netOf(items)}</span>
          </header>

          <ul className="divide-y divide-border">
            {items.map((transaction) => (
              <li
                key={transaction.id}
                className="group flex items-center gap-3 py-2.5 text-sm"
              >
                <span
                  className="h-8 w-1 shrink-0 rounded-full"
                  style={{ backgroundColor: transaction.category?.color ?? '#64748b' }}
                  aria-hidden
                />

                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5">
                    <span className="truncate font-medium">
                      {transaction.type === 'TRANSFER' ? (
                        <Fragment>
                          {transaction.wallet.name}
                          <ArrowRight className="mx-1 inline h-3 w-3" aria-hidden />
                          {transaction.toWallet?.name}
                        </Fragment>
                      ) : (
                        transaction.category?.name
                      )}
                    </span>
                    {transaction.source === 'RECURRING' && (
                      <Repeat className="h-3 w-3 shrink-0 text-muted-foreground" aria-label="Giao dịch định kỳ" />
                    )}
                    {transaction.source === 'AI_PARSE' && (
                      <Bot className="h-3 w-3 shrink-0 text-muted-foreground" aria-label="Do AI tạo" />
                    )}
                  </div>
                  <p className="truncate text-xs text-muted-foreground">
                    {formatTime(transaction.occurredAt)}
                    {transaction.type !== 'TRANSFER' && ` · ${transaction.wallet.name}`}
                    {transaction.category?.parentName && ` · ${transaction.category.parentName}`}
                    {transaction.note && ` · ${transaction.note}`}
                  </p>
                  {transaction.tags.length > 0 && (
                    <ul className="mt-1 flex flex-wrap gap-1">
                      {transaction.tags.map((tag) => (
                        <li
                          key={tag.id}
                          className="rounded-full px-1.5 py-0.5 text-[10px] font-medium text-white"
                          style={{ backgroundColor: tag.color }}
                        >
                          {tag.name}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                <span
                  className={cn('shrink-0 font-semibold tabular-nums', TYPE_CLASSES[transaction.type])}
                >
                  {signOf(transaction.type)}
                  {formatCurrency(transaction.amount)}
                </span>

                <div className="flex shrink-0 gap-0.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Sửa giao dịch ${formatCurrency(transaction.amount)}`}
                    onClick={() => onEdit(transaction)}
                  >
                    <Pencil className="h-3.5 w-3.5" aria-hidden />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Xóa giao dịch ${formatCurrency(transaction.amount)}`}
                    onClick={() => onDelete(transaction)}
                  >
                    <Trash2 className="h-3.5 w-3.5 text-destructive" aria-hidden />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function groupByDay(transactions: Transaction[]): [string, Transaction[]][] {
  const groups = new Map<string, Transaction[]>()

  for (const transaction of transactions) {
    const day = formatDate(transaction.occurredAt)
    const bucket = groups.get(day)
    if (bucket) {
      bucket.push(transaction)
    } else {
      groups.set(day, [transaction])
    }
  }
  return [...groups.entries()]
}

function netOf(transactions: Transaction[]): string {
  const net = transactions.reduce((total, transaction) => {
    if (transaction.type === 'INCOME') {
      return total + transaction.amount
    }
    if (transaction.type === 'EXPENSE') {
      return total - transaction.amount
    }
    return total
  }, 0)

  return `${net > 0 ? '+' : net < 0 ? '−' : ''}${formatCurrency(Math.abs(net))}`
}
