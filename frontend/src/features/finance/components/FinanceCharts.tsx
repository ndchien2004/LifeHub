import {
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatDate } from '@/shared/lib/dateUtils'
import { formatAmount, formatCurrency } from '@/shared/lib/formatters'
import type { Summary, SummaryGroup } from '../types'

/** Fallback palette for slices whose category has no colour of its own. */
const FALLBACK_COLORS = [
  '#f59e0b',
  '#3b82f6',
  '#10b981',
  '#ec4899',
  '#ef4444',
  '#8b5cf6',
  '#06b6d4',
  '#f97316',
]

/**
 * Spending breakdown by category (FR-FIN-10).
 *
 * <p>Slices use each category's own colour, so the chart, the list and the budget bars all speak
 * the same visual language. The figures come straight from the summary endpoint rather than being
 * recomputed here, which is what guarantees the chart agrees with the transaction list for the
 * same date range.
 */
export function SpendingPieChart({ summary }: { summary: Summary }) {
  if (summary.groups.length === 0) {
    return <EmptyChart message="Chưa có chi tiêu nào trong khoảng thời gian này." />
  }

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={summary.groups}
            dataKey="amount"
            nameKey="label"
            innerRadius="45%"
            outerRadius="75%"
            paddingAngle={1}
          >
            {summary.groups.map((group, index) => (
              <Cell
                key={group.key}
                fill={group.color ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length]}
              />
            ))}
          </Pie>
          <Tooltip
            formatter={(value: number, _name, item) => [
              `${formatCurrency(value)} (${(item?.payload as SummaryGroup)?.percentage ?? 0}%)`,
              (item?.payload as SummaryGroup)?.label ?? '',
            ]}
          />
          <Legend
            layout="vertical"
            align="right"
            verticalAlign="middle"
            formatter={(value: string) => <span className="text-xs">{value}</span>}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}

/**
 * Income and expense over time (FR-FIN-11).
 *
 * <p>Two series on one axis rather than two charts: the question the user is asking is whether
 * they earned more than they spent, and that comparison has to be readable at a glance.
 */
export function TrendLineChart({
  expense,
  income,
}: {
  expense: Summary
  income: Summary
}) {
  const points = mergeSeries(expense.groups, income.groups)

  if (points.length === 0) {
    return <EmptyChart message="Chưa có dữ liệu để vẽ xu hướng." />
  }

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
          <XAxis
            dataKey="key"
            tickFormatter={(value: string) => formatDate(value)}
            tick={{ fontSize: 11 }}
          />
          <YAxis tickFormatter={(value: number) => compact(value)} tick={{ fontSize: 11 }} width={56} />
          <Tooltip
            labelFormatter={(value: string) => formatDate(value)}
            formatter={(value: number, name: string) => [formatCurrency(value), name]}
          />
          <Legend formatter={(value: string) => <span className="text-xs">{value}</span>} />
          <Line type="monotone" dataKey="Chi" stroke="#ef4444" strokeWidth={2} dot={false} />
          <Line type="monotone" dataKey="Thu" stroke="#22c55e" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function EmptyChart({ message }: { message: string }) {
  return (
    <div className="flex h-72 items-center justify-center rounded-md border border-dashed border-border">
      <p className="text-sm text-muted-foreground">{message}</p>
    </div>
  )
}

/** Aligns the two series on a shared, sorted set of time buckets so gaps do not shift the lines. */
function mergeSeries(
  expense: SummaryGroup[],
  income: SummaryGroup[],
): { key: string; Chi: number; Thu: number }[] {
  const keys = [...new Set([...expense.map((g) => g.key), ...income.map((g) => g.key)])].sort()
  const expenseByKey = new Map(expense.map((group) => [group.key, group.amount]))
  const incomeByKey = new Map(income.map((group) => [group.key, group.amount]))

  return keys.map((key) => ({
    key,
    Chi: expenseByKey.get(key) ?? 0,
    Thu: incomeByKey.get(key) ?? 0,
  }))
}

/** Axis labels: "4,3 tr" reads far better than "4.250.000" squeezed into a tick. */
function compact(value: number): string {
  if (Math.abs(value) >= 1_000_000) {
    return `${(value / 1_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} tr`
  }
  if (Math.abs(value) >= 1_000) {
    return `${(value / 1_000).toLocaleString('vi-VN', { maximumFractionDigits: 0 })} k`
  }
  return formatAmount(value)
}
