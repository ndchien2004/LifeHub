import {
  AlertCircle,
  AlertTriangle,
  CalendarDays,
  Clock,
  Loader2,
  TrendingDown,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/shared/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/components/ui/card'
import { ApiRequestError } from '@/shared/lib/apiClient'
import { formatCount, formatCurrency } from '@/shared/lib/formatters'
import { cn } from '@/shared/lib/utils'
import { useNavStore } from '@/shared/stores/navStore'
import { useBootstrap } from './useBootstrap'

/**
 * The home screen (FR-SYS-01).
 *
 * <p>Every figure comes from {@code /bootstrap}, the one call the app already makes at startup.
 * A second round trip for the same numbers would slow the first paint for nothing, and finance
 * mutations invalidate that query so the panel stays current.
 *
 * <p>Each tile is clickable: a dashboard that reports "2 việc quá hạn" and then makes the user hunt
 * for them is only half a feature.
 */
export function DashboardPage() {
  const { data, isPending, error, refetch, isFetching } = useBootstrap()
  const navigate = useNavStore((state) => state.navigate)

  if (isPending) {
    return (
      <CenteredState>
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-hidden />
        <p className="text-sm text-muted-foreground">Đang tải dữ liệu khởi động…</p>
      </CenteredState>
    )
  }

  if (error) {
    const apiError = error instanceof ApiRequestError ? error : null
    return (
      <CenteredState>
        <AlertCircle className="h-8 w-8 text-destructive" aria-hidden />
        <div className="space-y-1 text-center">
          <p className="font-medium">Không tải được dữ liệu khởi động</p>
          <p className="text-sm text-muted-foreground">
            {apiError?.message ?? 'Dịch vụ nền chưa sẵn sàng.'}
          </p>
          {apiError?.traceId && (
            <p className="font-mono text-xs text-muted-foreground">Mã lỗi: {apiError.traceId}</p>
          )}
        </div>
        <Button onClick={() => void refetch()} disabled={isFetching}>
          {isFetching ? 'Đang thử lại…' : 'Thử lại'}
        </Button>
      </CenteredState>
    )
  }

  const dashboard = data.dashboard

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Tổng quan</h1>
        <p className="text-sm text-muted-foreground">
          Công việc, lịch và chi tiêu của bạn trong một màn hình.
        </p>
      </header>

      {!dashboard ? (
        <Card>
          <CardHeader>
            <CardTitle>Chưa có số liệu</CardTitle>
            <CardDescription>
              Không tổng hợp được số liệu tổng quan lần này. Hãy thử tải lại.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => void refetch()}>
              Tải lại
            </Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile
              label="Việc hôm nay"
              value={formatCount(dashboard.todayTasks)}
              Icon={Clock}
              onClick={() => navigate('tasks')}
            />
            <StatTile
              label="Việc quá hạn"
              value={formatCount(dashboard.overdueTasks)}
              Icon={AlertTriangle}
              tone={dashboard.overdueTasks > 0 ? 'danger' : undefined}
              onClick={() => navigate('tasks')}
            />
            <StatTile
              label="Sự kiện 7 ngày tới"
              value={formatCount(dashboard.upcomingEvents)}
              Icon={CalendarDays}
              onClick={() => navigate('calendar')}
            />
            <StatTile
              label="Còn lại tháng này"
              value={formatCurrency(dashboard.monthIncome - dashboard.monthExpense)}
              Icon={Wallet}
              tone={dashboard.monthIncome - dashboard.monthExpense < 0 ? 'danger' : undefined}
              onClick={() => navigate('finance')}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <MoneyCard
              label="Thu tháng này"
              value={dashboard.monthIncome}
              Icon={TrendingUp}
              tone="income"
              onClick={() => navigate('finance')}
            />
            <MoneyCard
              label="Chi tháng này"
              value={dashboard.monthExpense}
              Icon={TrendingDown}
              tone="expense"
              onClick={() => navigate('finance')}
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Ngân sách cần chú ý</CardTitle>
              <CardDescription>
                Danh mục đã dùng từ 80% hạn mức của chu kỳ đang diễn ra.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {dashboard.budgetAlerts.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Không có ngân sách nào sắp vượt. Mọi thứ đang trong tầm kiểm soát.
                </p>
              ) : (
                <ul className="space-y-2">
                  {dashboard.budgetAlerts.map((alert) => {
                    const percent = Math.round(alert.usage * 100)
                    const exceeded = alert.usage >= 1
                    return (
                      <li
                        key={alert.categoryName}
                        className="flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2 text-sm"
                      >
                        <span className="font-medium">{alert.categoryName}</span>
                        <span
                          className={cn(
                            'font-semibold tabular-nums',
                            exceeded
                              ? 'text-red-600 dark:text-red-400'
                              : 'text-amber-600 dark:text-amber-400',
                          )}
                        >
                          {percent}% {exceeded ? '· đã vượt hạn mức' : '· sắp hết'}
                        </span>
                      </li>
                    )
                  })}
                </ul>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}

function StatTile({
  label,
  value,
  Icon,
  tone,
  onClick,
}: {
  label: string
  value: string
  Icon: typeof Clock
  tone?: 'danger'
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded-md border border-border bg-card p-4 text-left transition-colors hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <div className="mb-1 flex items-center gap-2 text-xs uppercase tracking-wide text-muted-foreground">
        <Icon className="h-3.5 w-3.5" aria-hidden />
        {label}
      </div>
      <p
        className={cn(
          'text-2xl font-semibold tabular-nums',
          tone === 'danger' && 'text-red-600 dark:text-red-400',
        )}
      >
        {value}
      </p>
    </button>
  )
}

function MoneyCard({
  label,
  value,
  Icon,
  tone,
  onClick,
}: {
  label: string
  value: number
  Icon: typeof TrendingUp
  tone: 'income' | 'expense'
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-4 rounded-md border border-border bg-card p-4 text-left transition-colors hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <span
        className={cn(
          'flex h-10 w-10 shrink-0 items-center justify-center rounded-full',
          tone === 'income'
            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300'
            : 'bg-red-100 text-red-700 dark:bg-red-900/50 dark:text-red-300',
        )}
      >
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="text-xl font-semibold tabular-nums">{formatCurrency(value)}</p>
      </div>
    </button>
  )
}

function CenteredState({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-8">{children}</div>
  )
}
