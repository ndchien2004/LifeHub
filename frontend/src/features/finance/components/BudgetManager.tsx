import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/shared/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog'
import { Label, Select } from '@/shared/components/ui/input'
import { formatDate } from '@/shared/lib/dateUtils'
import { formatCurrency } from '@/shared/lib/formatters'
import { cn } from '@/shared/lib/utils'
import { useBudgets, useCategories, useCreateBudget, useDeleteBudget } from '../hooks'
import { categoryOptions } from '../lib/categoryTree'
import { BUDGET_PERIODS, BUDGET_PERIOD_LABELS, type Budget, type BudgetPeriod } from '../types'
import { AmountInput } from './AmountInput'

/**
 * Budgets with a progress bar that changes colour at the two thresholds (FR-FIN-08, FR-FIN-09).
 *
 * <p>Colour alone would not be enough - the percentage and the remaining amount are spelled out in
 * text as well, so the state is readable without relying on colour perception.
 */
export function BudgetManager() {
  const { data: budgets = [], isPending } = useBudgets()
  const deleteBudget = useDeleteBudget()
  const [formOpen, setFormOpen] = useState(false)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Mức sử dụng tính theo chu kỳ đang diễn ra, gồm cả chi tiêu ở danh mục con.
        </p>
        <Button onClick={() => setFormOpen(true)}>
          <Plus className="h-4 w-4" aria-hidden />
          Thêm ngân sách
        </Button>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Đang tải ngân sách…</p>}

      {!isPending && budgets.length === 0 && (
        <p className="rounded-md border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Chưa đặt ngân sách nào. Đặt hạn mức cho một danh mục để được cảnh báo khi sắp vượt.
        </p>
      )}

      <ul className="space-y-3">
        {budgets.map((budget) => (
          <li key={budget.id} className="group rounded-md border border-border bg-card p-4">
            <BudgetRow budget={budget} onDelete={() => deleteBudget.mutate(budget.id)} />
          </li>
        ))}
      </ul>

      <BudgetFormDialog open={formOpen} onOpenChange={setFormOpen} />
    </div>
  )
}

function BudgetRow({ budget, onDelete }: { budget: Budget; onDelete: () => void }) {
  const percent = Math.round(budget.usage * 100)
  const barWidth = Math.min(percent, 100)

  return (
    <>
      <div className="mb-2 flex items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 font-medium">
            <span
              className="h-3 w-3 rounded-full"
              style={{ backgroundColor: budget.category.color }}
              aria-hidden
            />
            {budget.category.name}
            <span className="text-xs font-normal text-muted-foreground">
              {BUDGET_PERIOD_LABELS[budget.period]}
            </span>
          </p>
          <p className="text-xs text-muted-foreground">
            Chu kỳ {formatDate(budget.periodStart)} – {formatDate(budget.periodEnd)}
          </p>
        </div>

        <Button
          variant="ghost"
          size="icon"
          aria-label={`Xóa ngân sách ${budget.category.name}`}
          className="opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100"
          onClick={onDelete}
        >
          <Trash2 className="h-3.5 w-3.5 text-destructive" aria-hidden />
        </Button>
      </div>

      <div
        className="h-2 w-full overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`Mức sử dụng ngân sách ${budget.category.name}`}
      >
        <div
          className={cn(
            'h-full rounded-full transition-all',
            budget.level === 'EXCEEDED'
              ? 'bg-red-500'
              : budget.level === 'WARNING'
                ? 'bg-amber-500'
                : 'bg-emerald-500',
          )}
          style={{ width: `${barWidth}%` }}
        />
      </div>

      <div className="mt-1.5 flex items-baseline justify-between text-sm">
        <span className="tabular-nums">
          {formatCurrency(budget.spentAmount)} / {formatCurrency(budget.limitAmount)}
        </span>
        <span
          className={cn(
            'font-medium tabular-nums',
            budget.level === 'EXCEEDED'
              ? 'text-red-600 dark:text-red-400'
              : budget.level === 'WARNING'
                ? 'text-amber-600 dark:text-amber-400'
                : 'text-muted-foreground',
          )}
        >
          {percent}%
          {budget.level === 'EXCEEDED'
            ? ` · vượt ${formatCurrency(budget.spentAmount - budget.limitAmount)}`
            : ` · còn ${formatCurrency(budget.remainingAmount)}`}
        </span>
      </div>
    </>
  )
}

function BudgetFormDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { data: categories = [] } = useCategories('EXPENSE')
  const createBudget = useCreateBudget()

  const [categoryId, setCategoryId] = useState('')
  const [limitAmount, setLimitAmount] = useState(0)
  const [period, setPeriod] = useState<BudgetPeriod>('MONTHLY')
  const [error, setError] = useState('')

  const [wasOpen, setWasOpen] = useState(false)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setCategoryId('')
      setLimitAmount(0)
      setPeriod('MONTHLY')
      setError('')
    }
  }

  /**
   * Saves, keeping the dialog open if the request fails.
   *
   * <p>The mutation's {@code onError} already shows the message, so the rejection is swallowed
   * here rather than escaping the click handler as an unhandled promise. Staying open matters:
   * a duplicate budget is refused with a 409, and closing would throw away what the user typed
   * along with any chance to correct it.
   */
  async function submit() {
    if (!categoryId) {
      setError('Hãy chọn danh mục')
      return
    }
    if (limitAmount <= 0) {
      setError('Hạn mức phải lớn hơn 0')
      return
    }
    try {
      await createBudget.mutateAsync({
        categoryId,
        limitAmount,
        period,
        startDate: new Date().toISOString().slice(0, 10),
      })
      onOpenChange(false)
    } catch {
      setError('Không lưu được ngân sách. Xem thông báo lỗi ở góc màn hình.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Thêm ngân sách</DialogTitle>
          <DialogDescription>
            Chỉ đặt được ngân sách cho danh mục chi. Mỗi danh mục có tối đa một ngân sách cho mỗi chu kỳ.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="budget-category">Danh mục</Label>
            <Select
              id="budget-category"
              value={categoryId}
              onChange={(event) => setCategoryId(event.target.value)}
            >
              <option value="">— Chọn danh mục —</option>
              {categoryOptions(categories).map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="budget-limit">Hạn mức</Label>
            <AmountInput
              id="budget-limit"
              value={limitAmount}
              onChange={setLimitAmount}
              aria-label="Hạn mức ngân sách"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="budget-period">Chu kỳ</Label>
            <Select
              id="budget-period"
              value={period}
              onChange={(event) => setPeriod(event.target.value as BudgetPeriod)}
            >
              {BUDGET_PERIODS.map((option) => (
                <option key={option} value={option}>
                  {BUDGET_PERIOD_LABELS[option]}
                </option>
              ))}
            </Select>
          </div>

          {error && (
            <p role="alert" className="text-xs font-medium text-destructive">
              {error}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Hủy
          </Button>
          <Button onClick={() => void submit()}>Lưu</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
