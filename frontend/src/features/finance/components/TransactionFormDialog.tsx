import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/shared/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog'
import { FieldError, Input, Label, Select, Textarea } from '@/shared/components/ui/input'
import { useTags } from '@/features/tasks/hooks'
import { cn } from '@/shared/lib/utils'
import { useCategories, useCreateTransaction, useUpdateTransaction, useWallets } from '../hooks'
import { categoryOptions } from '../lib/categoryTree'
import {
  TRANSACTION_TYPES,
  TRANSACTION_TYPE_LABELS,
  type Transaction,
  type TransactionInput,
  type TransactionType,
} from '../types'
import { AmountInput } from './AmountInput'

/**
 * Create and edit form for a transaction (UC-06).
 *
 * <p>The form changes shape with the type: a transfer swaps the category picker for a destination
 * wallet, because a movement between two of your own wallets is neither income nor expense and has
 * nothing to classify (FR-FIN-05, UC-06 alternate flow 5a).
 *
 * <p>Validation mirrors the backend rules rather than merely hinting at them, so the two exception
 * flows the use case names - a non-positive amount and a transfer to the same wallet - are caught
 * inline before any request leaves the renderer.
 */

const transactionSchema = z
  .object({
    type: z.enum(TRANSACTION_TYPES),
    amount: z.number().int().positive('Số tiền phải lớn hơn 0'),
    walletId: z.string().min(1, 'Hãy chọn ví'),
    toWalletId: z.string().optional(),
    categoryId: z.string().optional(),
    note: z.string().max(500, 'Ghi chú tối đa 500 ký tự').optional(),
    occurredAt: z.string().min(1, 'Hãy chọn thời điểm giao dịch'),
    tagIds: z.array(z.string()).optional(),
  })
  .superRefine((values, ctx) => {
    if (values.type === 'TRANSFER') {
      if (!values.toWalletId) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['toWalletId'], message: 'Hãy chọn ví đích' })
      } else if (values.toWalletId === values.walletId) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['toWalletId'],
          message: 'Ví nguồn và ví đích phải khác nhau',
        })
      }
    } else if (!values.categoryId) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['categoryId'], message: 'Hãy chọn danh mục' })
    }
  })

type TransactionFormValues = z.infer<typeof transactionSchema>

interface TransactionFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present when editing; absent when creating. */
  transaction?: Transaction | null
}

export function TransactionFormDialog({
  open,
  onOpenChange,
  transaction,
}: TransactionFormDialogProps) {
  const isEditing = Boolean(transaction)
  const { data: walletList } = useWallets()
  // Memoised: an inline `?? []` would be a new array on every render, and the reset effect below
  // depends on it — that combination is an infinite render loop, not a cosmetic detail.
  const wallets = useMemo(() => walletList?.wallets ?? [], [walletList])
  const { data: expenseCategories = [] } = useCategories('EXPENSE')
  const { data: incomeCategories = [] } = useCategories('INCOME')
  const { data: tags = [] } = useTags()
  const createTransaction = useCreateTransaction()
  const updateTransaction = useUpdateTransaction()

  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: emptyValues(),
  })

  const type = watch('type')
  const walletId = watch('walletId')
  const selectedTagIds = watch('tagIds') ?? []
  const isTransfer = type === 'TRANSFER'
  const categories = type === 'INCOME' ? incomeCategories : expenseCategories

  const defaultWalletId = useMemo(
    () => (wallets.find((wallet) => wallet.isDefault) ?? wallets[0])?.id ?? '',
    [wallets],
  )

  // Loads the dialog's starting state exactly once per open.
  useEffect(() => {
    if (open) {
      reset(transaction ? valuesFrom(transaction) : emptyValues())
    }
  }, [open, transaction, reset])

  /*
   * Preselects the default wallet for a new transaction (UC-06 step 2).
   *
   * Kept out of the reset above on purpose: the wallet list arrives asynchronously, and resetting
   * the whole form when it lands would wipe out whatever the user had already typed in the
   * meantime. This only fills a field that is still empty.
   */
  useEffect(() => {
    if (open && !transaction && !walletId && defaultWalletId) {
      setValue('walletId', defaultWalletId)
    }
  }, [open, transaction, walletId, defaultWalletId, setValue])

  // Switching to or from a transfer invalidates whichever of the two fields no longer applies;
  // leaving a stale value behind would send a category with a transfer.
  useEffect(() => {
    if (isTransfer) {
      setValue('categoryId', undefined)
    } else {
      setValue('toWalletId', undefined)
    }
  }, [isTransfer, setValue])

  const onSubmit = handleSubmit(async (values) => {
    const input: TransactionInput = {
      type: values.type,
      amount: values.amount,
      walletId: values.walletId,
      toWalletId: values.type === 'TRANSFER' ? values.toWalletId : null,
      categoryId: values.type === 'TRANSFER' ? null : values.categoryId,
      note: values.note?.trim() || null,
      occurredAt: new Date(values.occurredAt).toISOString(),
      tagIds: values.tagIds ?? [],
    }

    // A failed save leaves the dialog open with the user's input intact; the mutation's onError
    // has already shown why. Swallowing the rejection here keeps it from escaping the form's
    // submit handler as an unhandled promise.
    try {
      if (isEditing && transaction) {
        await updateTransaction.mutateAsync({ id: transaction.id, input })
      } else {
        await createTransaction.mutateAsync(input)
      }
      onOpenChange(false)
    } catch {
      // Intentionally ignored - reported by the mutation's onError.
    }
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-xl"
        onKeyDown={(event) => {
          if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault()
            void onSubmit()
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Sửa giao dịch' : 'Thêm giao dịch'}</DialogTitle>
          <DialogDescription>
            Số tiền tính bằng đồng, tự thêm dấu chấm phân cách nghìn khi bạn gõ.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} className="space-y-4">
          <div
            role="radiogroup"
            aria-label="Loại giao dịch"
            className="grid grid-cols-3 gap-1 rounded-md bg-muted p-1"
          >
            {TRANSACTION_TYPES.map((option) => (
              <button
                key={option}
                type="button"
                role="radio"
                aria-checked={type === option}
                onClick={() => setValue('type', option as TransactionType)}
                className={cn(
                  'rounded px-3 py-1.5 text-sm font-medium transition-colors',
                  type === option
                    ? 'bg-background text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {TRANSACTION_TYPE_LABELS[option]}
              </button>
            ))}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="transaction-amount">Số tiền</Label>
            <Controller
              control={control}
              name="amount"
              render={({ field }) => (
                <AmountInput
                  id="transaction-amount"
                  autoFocus
                  value={field.value}
                  onChange={field.onChange}
                  aria-invalid={Boolean(errors.amount)}
                  aria-label="Số tiền"
                />
              )}
            />
            <FieldError>{errors.amount?.message}</FieldError>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="transaction-wallet">{isTransfer ? 'Ví nguồn' : 'Ví'}</Label>
              <Select id="transaction-wallet" {...register('walletId')} aria-invalid={Boolean(errors.walletId)}>
                <option value="">— Chọn ví —</option>
                {wallets.map((wallet) => (
                  <option key={wallet.id} value={wallet.id}>
                    {wallet.name}
                  </option>
                ))}
              </Select>
              <FieldError>{errors.walletId?.message}</FieldError>
            </div>

            {isTransfer ? (
              <div className="space-y-1.5">
                <Label htmlFor="transaction-to-wallet">Ví đích</Label>
                <Select
                  id="transaction-to-wallet"
                  {...register('toWalletId')}
                  aria-invalid={Boolean(errors.toWalletId)}
                >
                  <option value="">— Chọn ví đích —</option>
                  {wallets.map((wallet) => (
                    <option key={wallet.id} value={wallet.id}>
                      {wallet.name}
                    </option>
                  ))}
                </Select>
                <FieldError>{errors.toWalletId?.message}</FieldError>
              </div>
            ) : (
              <div className="space-y-1.5">
                <Label htmlFor="transaction-category">Danh mục</Label>
                <Select
                  id="transaction-category"
                  {...register('categoryId')}
                  aria-invalid={Boolean(errors.categoryId)}
                >
                  <option value="">— Chọn danh mục —</option>
                  {categoryOptions(categories).map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </Select>
                <FieldError>{errors.categoryId?.message}</FieldError>
              </div>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="transaction-occurred-at">Thời điểm</Label>
            <Input
              id="transaction-occurred-at"
              type="datetime-local"
              {...register('occurredAt')}
              aria-invalid={Boolean(errors.occurredAt)}
            />
            <FieldError>{errors.occurredAt?.message}</FieldError>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="transaction-note">Ghi chú</Label>
            <Textarea id="transaction-note" placeholder="Cơm gà, ăn cùng team" {...register('note')} />
            <FieldError>{errors.note?.message}</FieldError>
          </div>

          {/* Tags are shared with the task module (FR-FIN-04, FR-PRJ-04), so "công tác" means the
              same thing on a trip expense as it does on the task that planned it. */}
          {tags.length > 0 && (
            <div className="space-y-1.5">
              <Label>Nhãn</Label>
              <div className="flex flex-wrap gap-1.5">
                {tags.map((tag) => {
                  const selected = selectedTagIds.includes(tag.id)
                  return (
                    <button
                      key={tag.id}
                      type="button"
                      aria-pressed={selected}
                      onClick={() =>
                        setValue(
                          'tagIds',
                          selected
                            ? selectedTagIds.filter((id) => id !== tag.id)
                            : [...selectedTagIds, tag.id],
                          { shouldDirty: true },
                        )
                      }
                      className={cn(
                        'rounded-full border px-2.5 py-0.5 text-xs transition-colors',
                        selected
                          ? 'border-transparent text-white'
                          : 'border-border text-muted-foreground hover:bg-accent',
                      )}
                      style={selected ? { backgroundColor: tag.color } : undefined}
                    >
                      {tag.name}
                    </button>
                  )
                })}
              </div>
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Đang lưu…' : 'Lưu'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function emptyValues(): TransactionFormValues {
  return {
    type: 'EXPENSE',
    amount: 0,
    walletId: '',
    toWalletId: undefined,
    categoryId: undefined,
    note: '',
    occurredAt: toLocalInput(new Date().toISOString()),
    tagIds: [],
  }
}

function valuesFrom(transaction: Transaction): TransactionFormValues {
  return {
    type: transaction.type,
    amount: transaction.amount,
    walletId: transaction.wallet.id,
    toWalletId: transaction.toWallet?.id,
    categoryId: transaction.category?.id,
    note: transaction.note ?? '',
    occurredAt: toLocalInput(transaction.occurredAt),
    tagIds: transaction.tags.map((tag) => tag.id),
  }
}

/** ISO instant -> the {@code yyyy-MM-ddTHH:mm} shape a datetime-local input expects. */
function toLocalInput(iso: string): string {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
