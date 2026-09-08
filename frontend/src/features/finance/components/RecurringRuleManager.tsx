import { Pause, Play, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { RecurrenceBuilder } from '@/features/calendar/components/RecurrenceBuilder'
import { buildRrule, describeRrule, parseRrule, type Recurrence } from '@/features/calendar/lib/rrule'
import { Button } from '@/shared/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog'
import { Input, Label, Select } from '@/shared/components/ui/input'
import { formatDate } from '@/shared/lib/dateUtils'
import { formatCurrency } from '@/shared/lib/formatters'
import {
  useCategories,
  useCreateRecurringRule,
  useDeleteRecurringRule,
  useRecurringRules,
  useRunRecurringRules,
  useUpdateRecurringRule,
  useWallets,
} from '../hooks'
import { categoryOptions } from '../lib/categoryTree'
import { AmountInput } from './AmountInput'

/**
 * Recurring transactions: rent, subscriptions, salary (FR-FIN-13).
 *
 * <p>Reuses the calendar module's RRULE builder rather than growing a second one. A monthly rent
 * and a weekly meeting are the same kind of rule, and two builders would inevitably drift apart in
 * what they accept.
 */
export function RecurringRuleManager() {
  const { data: rules = [], isPending } = useRecurringRules()
  const updateRule = useUpdateRecurringRule()
  const deleteRule = useDeleteRecurringRule()
  const runRules = useRunRecurringRules()
  const [formOpen, setFormOpen] = useState(false)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          Giao dịch định kỳ được sinh tự động khi tới hạn, kể cả những lần bị lỡ lúc ứng dụng đóng.
        </p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => runRules.mutate()} disabled={runRules.isPending}>
            <RefreshCw className="h-4 w-4" aria-hidden />
            Chạy ngay
          </Button>
          <Button onClick={() => setFormOpen(true)}>
            <Plus className="h-4 w-4" aria-hidden />
            Thêm quy luật
          </Button>
        </div>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Đang tải quy luật…</p>}

      {!isPending && rules.length === 0 && (
        <p className="rounded-md border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Chưa có giao dịch định kỳ nào. Thêm tiền nhà hay subscription để không phải nhập lại mỗi tháng.
        </p>
      )}

      <ul className="space-y-2">
        {rules.map((rule) => (
          <li
            key={rule.id}
            className="group flex items-center gap-3 rounded-md border border-border bg-card px-4 py-3"
          >
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">
                {rule.template?.note || 'Giao dịch định kỳ'}
                {rule.template && (
                  <span className="ml-2 tabular-nums text-muted-foreground">
                    {formatCurrency(rule.template.amount)}
                  </span>
                )}
              </p>
              <p className="text-xs text-muted-foreground">
                {describeRrule(rule.rrule)}
                {rule.nextRunDate
                  ? ` · lần kế tiếp ${formatDate(rule.nextRunDate)}`
                  : ' · đã chạy hết chuỗi'}
              </p>
            </div>

            <span
              className={
                rule.isActive
                  ? 'rounded bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800 dark:bg-emerald-900/50 dark:text-emerald-300'
                  : 'rounded bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground'
              }
            >
              {rule.isActive ? 'Đang chạy' : 'Đã dừng'}
            </span>

            <div className="flex gap-0.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
              <Button
                variant="ghost"
                size="icon"
                aria-label={rule.isActive ? 'Tạm dừng quy luật' : 'Chạy lại quy luật'}
                onClick={() => updateRule.mutate({ id: rule.id, input: { isActive: !rule.isActive } })}
              >
                {rule.isActive ? (
                  <Pause className="h-3.5 w-3.5" aria-hidden />
                ) : (
                  <Play className="h-3.5 w-3.5" aria-hidden />
                )}
              </Button>
              <Button
                variant="ghost"
                size="icon"
                aria-label="Xóa quy luật định kỳ"
                onClick={() => deleteRule.mutate(rule.id)}
              >
                <Trash2 className="h-3.5 w-3.5 text-destructive" aria-hidden />
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <RecurringRuleFormDialog open={formOpen} onOpenChange={setFormOpen} />
    </div>
  )
}

function RecurringRuleFormDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { data: walletList } = useWallets()
  const { data: categories = [] } = useCategories('EXPENSE')
  const createRule = useCreateRecurringRule()

  const wallets = walletList?.wallets ?? []
  const [amount, setAmount] = useState(0)
  const [walletId, setWalletId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [note, setNote] = useState('')
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [recurrence, setRecurrence] = useState<Recurrence>(() => parseRrule('FREQ=MONTHLY'))
  const [error, setError] = useState('')

  const rrule = buildRrule(recurrence)

  const [wasOpen, setWasOpen] = useState(false)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setAmount(0)
      setWalletId(wallets.find((wallet) => wallet.isDefault)?.id ?? wallets[0]?.id ?? '')
      setCategoryId('')
      setNote('')
      setStartDate(new Date().toISOString().slice(0, 10))
      setRecurrence(parseRrule('FREQ=MONTHLY'))
      setError('')
    }
  }

  async function submit() {
    if (amount <= 0) {
      setError('Số tiền phải lớn hơn 0')
      return
    }
    if (!walletId || !categoryId) {
      setError('Hãy chọn ví và danh mục')
      return
    }
    if (!rrule) {
      setError('Hãy chọn quy luật lặp')
      return
    }

    try {
      await createRule.mutateAsync({
        rrule,
        startDate,
        template: {
          type: 'EXPENSE',
          amount,
          walletId,
          categoryId,
          note: note.trim() || null,
          occurredAt: new Date(`${startDate}T09:00:00`).toISOString(),
        },
      })
      onOpenChange(false)
    } catch {
      setError('Không lưu được quy luật. Xem thông báo lỗi ở góc màn hình.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Thêm giao dịch định kỳ</DialogTitle>
          <DialogDescription>
            Mỗi lần tới hạn, hệ thống tạo một giao dịch chi theo đúng mẫu bên dưới.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="recurring-amount">Số tiền</Label>
            <AmountInput
              id="recurring-amount"
              value={amount}
              onChange={setAmount}
              aria-label="Số tiền định kỳ"
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="recurring-wallet">Ví</Label>
              <Select
                id="recurring-wallet"
                value={walletId}
                onChange={(event) => setWalletId(event.target.value)}
              >
                <option value="">— Chọn ví —</option>
                {wallets.map((wallet) => (
                  <option key={wallet.id} value={wallet.id}>
                    {wallet.name}
                  </option>
                ))}
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="recurring-category">Danh mục</Label>
              <Select
                id="recurring-category"
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
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="recurring-note">Ghi chú</Label>
            <Input
              id="recurring-note"
              value={note}
              placeholder="Tiền nhà tháng"
              onChange={(event) => setNote(event.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="recurring-start">Bắt đầu từ</Label>
            <Input
              id="recurring-start"
              type="date"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
            />
          </div>

          <RecurrenceBuilder value={recurrence} onChange={setRecurrence} rrule={rrule} />

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
