import { Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Button } from '@/shared/components/ui/button'
import { Input, Label, Select } from '@/shared/components/ui/input'
import { useDebounce } from '@/shared/hooks/useDebounce'
import { useTags } from '@/features/tasks/hooks'
import { categoryOptions } from '../lib/categoryTree'
import { useCategories, useWallets } from '../hooks'
import {
  TRANSACTION_TYPES,
  TRANSACTION_TYPE_LABELS,
  type TransactionQuery,
  type TransactionType,
} from '../types'
import { AmountInput } from './AmountInput'

interface TransactionFilterBarProps {
  query: TransactionQuery
  onChange: (query: TransactionQuery) => void
}

/**
 * Advanced filter bar for the ledger (FR-FIN-12).
 *
 * <p>The keyword box is debounced so typing does not fire a request per keystroke; every other
 * control applies immediately, since each is a single deliberate choice.
 */
export function TransactionFilterBar({ query, onChange }: TransactionFilterBarProps) {
  const { data: walletList } = useWallets()
  const { data: categories = [] } = useCategories()
  const { data: tags = [] } = useTags()
  const [keyword, setKeyword] = useState(query.q ?? '')
  const debouncedKeyword = useDebounce(keyword, 300)

  useEffect(() => {
    if ((query.q ?? '') !== debouncedKeyword) {
      onChange({ ...query, q: debouncedKeyword || undefined })
    }
    // The query object is rebuilt on every parent render, so depending on it would loop.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword])

  const hasFilters = Boolean(
    query.q ||
      query.walletIds?.length ||
      query.categoryIds?.length ||
      query.type?.length ||
      query.tagIds?.length ||
      query.from ||
      query.to ||
      query.minAmount ||
      query.maxAmount,
  )

  return (
    <div className="space-y-3 rounded-md border border-border bg-card p-3">
      <div className="flex flex-wrap items-end gap-3">
        <div className="relative min-w-[200px] flex-1">
          <Search
            className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <Input
            aria-label="Tìm trong ghi chú"
            placeholder="Tìm trong ghi chú…"
            className="pl-8"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="filter-type" className="text-xs text-muted-foreground">
            Loại
          </Label>
          <Select
            id="filter-type"
            className="w-32"
            value={query.type?.[0] ?? ''}
            onChange={(event) =>
              onChange({
                ...query,
                type: event.target.value ? [event.target.value as TransactionType] : undefined,
              })
            }
          >
            <option value="">Tất cả</option>
            {TRANSACTION_TYPES.map((type) => (
              <option key={type} value={type}>
                {TRANSACTION_TYPE_LABELS[type]}
              </option>
            ))}
          </Select>
        </div>

        <div className="space-y-1">
          <Label htmlFor="filter-wallet" className="text-xs text-muted-foreground">
            Ví
          </Label>
          <Select
            id="filter-wallet"
            className="w-40"
            value={query.walletIds?.[0] ?? ''}
            onChange={(event) =>
              onChange({ ...query, walletIds: event.target.value ? [event.target.value] : undefined })
            }
          >
            <option value="">Tất cả</option>
            {(walletList?.wallets ?? []).map((wallet) => (
              <option key={wallet.id} value={wallet.id}>
                {wallet.name}
              </option>
            ))}
          </Select>
        </div>

        <div className="space-y-1">
          <Label htmlFor="filter-category" className="text-xs text-muted-foreground">
            Danh mục
          </Label>
          <Select
            id="filter-category"
            className="w-44"
            value={query.categoryIds?.[0] ?? ''}
            onChange={(event) =>
              onChange({
                ...query,
                categoryIds: event.target.value ? [event.target.value] : undefined,
              })
            }
          >
            <option value="">Tất cả</option>
            {categoryOptions(categories).map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="filter-from" className="text-xs text-muted-foreground">
            Từ ngày
          </Label>
          <Input
            id="filter-from"
            type="date"
            className="w-40"
            value={query.from?.slice(0, 10) ?? ''}
            onChange={(event) =>
              onChange({
                ...query,
                from: event.target.value ? new Date(`${event.target.value}T00:00:00`).toISOString() : undefined,
              })
            }
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="filter-to" className="text-xs text-muted-foreground">
            Đến ngày
          </Label>
          <Input
            id="filter-to"
            type="date"
            className="w-40"
            value={query.to ? new Date(new Date(query.to).getTime() - 1).toISOString().slice(0, 10) : ''}
            onChange={(event) =>
              onChange({
                ...query,
                // The upper bound is exclusive, so "đến 30/09" has to mean "before 01/10".
                to: event.target.value
                  ? new Date(`${event.target.value}T00:00:00`).toISOString().replace(/^(.*)$/, (iso) => {
                      const next = new Date(iso)
                      next.setDate(next.getDate() + 1)
                      return next.toISOString()
                    })
                  : undefined,
              })
            }
          />
        </div>

        <div className="space-y-1">
          <Label className="text-xs text-muted-foreground">Số tiền từ</Label>
          <AmountInput
            className="w-36"
            aria-label="Số tiền tối thiểu"
            value={query.minAmount ?? 0}
            onChange={(value) => onChange({ ...query, minAmount: value || undefined })}
          />
        </div>

        <div className="space-y-1">
          <Label className="text-xs text-muted-foreground">đến</Label>
          <AmountInput
            className="w-36"
            aria-label="Số tiền tối đa"
            value={query.maxAmount ?? 0}
            onChange={(value) => onChange({ ...query, maxAmount: value || undefined })}
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="filter-tag" className="text-xs text-muted-foreground">
            Nhãn
          </Label>
          <Select
            id="filter-tag"
            className="w-36"
            value={query.tagIds?.[0] ?? ''}
            onChange={(event) =>
              onChange({ ...query, tagIds: event.target.value ? [event.target.value] : undefined })
            }
          >
            <option value="">Tất cả</option>
            {tags.map((tag) => (
              <option key={tag.id} value={tag.id}>
                {tag.name}
              </option>
            ))}
          </Select>
        </div>

        {hasFilters && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setKeyword('')
              onChange({})
            }}
          >
            <X className="mr-1 h-3.5 w-3.5" aria-hidden />
            Xóa bộ lọc
          </Button>
        )}
      </div>
    </div>
  )
}
