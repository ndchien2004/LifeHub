import { Landmark, Pencil, Plus, Star, Trash2, Wallet as WalletIcon } from 'lucide-react'
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
import { Input, Label, Select } from '@/shared/components/ui/input'
import { formatCurrency } from '@/shared/lib/formatters'
import { cn } from '@/shared/lib/utils'
import { useCreateWallet, useDeleteWallet, useUpdateWallet, useWallets } from '../hooks'
import { WALLET_TYPES, WALLET_TYPE_LABELS, type Wallet, type WalletType } from '../types'
import { AmountInput } from './AmountInput'

/**
 * Wallet management with live balances and total net worth (FR-FIN-01, FR-FIN-07).
 *
 * <p>Every balance shown here is derived from the ledger rather than stored, so it cannot drift
 * away from the transactions that produced it. The opening balance is editable because it is the
 * one number the user genuinely owns; a credit card may legitimately start negative, which is why
 * the field accepts a sign the transaction form never would.
 */
export function WalletManager() {
  const { data, isPending } = useWallets()
  const deleteWallet = useDeleteWallet()
  const [editing, setEditing] = useState<Wallet | null>(null)
  const [formOpen, setFormOpen] = useState(false)

  const wallets = data?.wallets ?? []

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4 rounded-md border border-border bg-card p-4">
        <div>
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Tổng tài sản</p>
          <p className="text-2xl font-semibold tabular-nums">
            {formatCurrency(data?.totalAssets ?? 0)}
          </p>
        </div>
        <Button
          onClick={() => {
            setEditing(null)
            setFormOpen(true)
          }}
        >
          <Plus className="h-4 w-4" aria-hidden />
          Thêm ví
        </Button>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Đang tải ví…</p>}

      {!isPending && wallets.length === 0 && (
        <div className="rounded-md border border-dashed border-border p-8 text-center">
          <WalletIcon className="mx-auto mb-2 h-8 w-8 text-muted-foreground" aria-hidden />
          <p className="text-sm font-medium">Chưa có ví nào</p>
          <p className="mb-4 text-sm text-muted-foreground">
            Hãy tạo ví đầu tiên để bắt đầu ghi chép thu chi.
          </p>
          <Button
            onClick={() => {
              setEditing(null)
              setFormOpen(true)
            }}
          >
            <Plus className="h-4 w-4" aria-hidden />
            Tạo ví đầu tiên
          </Button>
        </div>
      )}

      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {wallets.map((wallet) => (
          <li
            key={wallet.id}
            className="group rounded-md border border-border bg-card p-4"
          >
            <div className="mb-2 flex items-start justify-between gap-2">
              <div className="flex items-center gap-2">
                <Landmark className="h-4 w-4 text-muted-foreground" aria-hidden />
                <div>
                  <p className="flex items-center gap-1 font-medium">
                    {wallet.name}
                    {wallet.isDefault && (
                      <Star className="h-3 w-3 fill-amber-400 text-amber-400" aria-label="Ví mặc định" />
                    )}
                  </p>
                  <p className="text-xs text-muted-foreground">{WALLET_TYPE_LABELS[wallet.type]}</p>
                </div>
              </div>

              <div className="flex gap-0.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Sửa ví ${wallet.name}`}
                  onClick={() => {
                    setEditing(wallet)
                    setFormOpen(true)
                  }}
                >
                  <Pencil className="h-3.5 w-3.5" aria-hidden />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Xóa ví ${wallet.name}`}
                  onClick={() => deleteWallet.mutate(wallet.id)}
                >
                  <Trash2 className="h-3.5 w-3.5 text-destructive" aria-hidden />
                </Button>
              </div>
            </div>

            <p
              className={cn(
                'text-xl font-semibold tabular-nums',
                wallet.balance < 0 && 'text-destructive',
              )}
            >
              {formatCurrency(wallet.balance)}
            </p>
            <p className="text-xs text-muted-foreground">
              Số dư ban đầu {formatCurrency(wallet.initialBalance)}
            </p>
          </li>
        ))}
      </ul>

      <WalletFormDialog open={formOpen} onOpenChange={setFormOpen} wallet={editing} />
    </div>
  )
}

function WalletFormDialog({
  open,
  onOpenChange,
  wallet,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  wallet: Wallet | null
}) {
  const createWallet = useCreateWallet()
  const updateWallet = useUpdateWallet()

  const [name, setName] = useState('')
  const [type, setType] = useState<WalletType>('CASH')
  const [initialBalance, setInitialBalance] = useState(0)
  const [negative, setNegative] = useState(false)
  const [isDefault, setIsDefault] = useState(false)
  const [error, setError] = useState('')

  // Reset when the dialog opens rather than on every render, so typing is not fought.
  const [lastOpenedFor, setLastOpenedFor] = useState<string | null>(null)
  const key = open ? (wallet?.id ?? 'new') : null
  if (key !== lastOpenedFor) {
    setLastOpenedFor(key)
    setName(wallet?.name ?? '')
    setType(wallet?.type ?? 'CASH')
    setInitialBalance(Math.abs(wallet?.initialBalance ?? 0))
    setNegative((wallet?.initialBalance ?? 0) < 0)
    setIsDefault(wallet?.isDefault ?? false)
    setError('')
  }

  /** Saves, keeping the dialog open if the request fails (the toast carries the reason). */
  async function submit() {
    if (!name.trim()) {
      setError('Tên ví không được để trống')
      return
    }
    const signedBalance = negative ? -initialBalance : initialBalance
    const input = { name: name.trim(), type, initialBalance: signedBalance, isDefault }

    try {
      if (wallet) {
        await updateWallet.mutateAsync({ id: wallet.id, input })
      } else {
        await createWallet.mutateAsync(input)
      }
      onOpenChange(false)
    } catch {
      setError('Không lưu được ví. Xem thông báo lỗi ở góc màn hình.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{wallet ? 'Sửa ví' : 'Thêm ví'}</DialogTitle>
          <DialogDescription>
            Số dư hiện tại được tính tự động từ các giao dịch, bạn chỉ cần nhập số dư ban đầu.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="wallet-name">Tên ví</Label>
            <Input
              id="wallet-name"
              value={name}
              placeholder="Tiền mặt"
              onChange={(event) => setName(event.target.value)}
              aria-invalid={Boolean(error)}
            />
            {error && (
              <p role="alert" className="text-xs font-medium text-destructive">
                {error}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="wallet-type">Loại ví</Label>
            <Select
              id="wallet-type"
              value={type}
              onChange={(event) => setType(event.target.value as WalletType)}
            >
              {WALLET_TYPES.map((option) => (
                <option key={option} value={option}>
                  {WALLET_TYPE_LABELS[option]}
                </option>
              ))}
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="wallet-initial">Số dư ban đầu</Label>
            <AmountInput
              id="wallet-initial"
              value={initialBalance}
              onChange={setInitialBalance}
              aria-label="Số dư ban đầu"
            />
            <label className="flex items-center gap-2 text-xs text-muted-foreground">
              <input
                type="checkbox"
                checked={negative}
                onChange={(event) => setNegative(event.target.checked)}
              />
              Đang nợ (số dư âm) — dùng cho thẻ tín dụng
            </label>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={isDefault}
              onChange={(event) => setIsDefault(event.target.checked)}
            />
            Đặt làm ví mặc định khi thêm giao dịch
          </label>
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
