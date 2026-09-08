import { Lock, Pencil, Plus, Trash2 } from 'lucide-react'
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
import { cn } from '@/shared/lib/utils'
import { useCategories, useCreateCategory, useDeleteCategory, useUpdateCategory } from '../hooks'
import type { Category, CategoryType } from '../types'

const PALETTE = [
  '#f59e0b',
  '#3b82f6',
  '#10b981',
  '#ec4899',
  '#ef4444',
  '#8b5cf6',
  '#06b6d4',
  '#f97316',
  '#22c55e',
  '#94a3b8',
]

/**
 * Two level category tree with colours (FR-FIN-02, FR-FIN-03).
 *
 * <p>System categories show a lock and cannot be deleted: they are the labels on every chart the
 * user has ever seen, and removing one would silently rewrite their history. Renaming and
 * recolouring stay allowed, because those are presentation, not identity.
 */
export function CategoryManager() {
  const [type, setType] = useState<CategoryType>('EXPENSE')
  const { data: categories = [], isPending } = useCategories(type)
  const deleteCategory = useDeleteCategory()

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [parentId, setParentId] = useState<string | null>(null)

  function openCreate(parent: string | null) {
    setEditing(null)
    setParentId(parent)
    setFormOpen(true)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div
          role="radiogroup"
          aria-label="Loại danh mục"
          className="inline-flex rounded-md bg-muted p-1"
        >
          {(['EXPENSE', 'INCOME'] as CategoryType[]).map((option) => (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={type === option}
              onClick={() => setType(option)}
              className={cn(
                'rounded px-3 py-1.5 text-sm font-medium transition-colors',
                type === option
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {option === 'EXPENSE' ? 'Danh mục chi' : 'Danh mục thu'}
            </button>
          ))}
        </div>

        <Button onClick={() => openCreate(null)}>
          <Plus className="h-4 w-4" aria-hidden />
          Thêm danh mục
        </Button>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Đang tải danh mục…</p>}

      <ul className="space-y-2">
        {categories.map((category) => (
          <li key={category.id} className="rounded-md border border-border bg-card">
            <CategoryRow
              category={category}
              onEdit={() => {
                setEditing(category)
                setParentId(category.parentId ?? null)
                setFormOpen(true)
              }}
              onDelete={() => deleteCategory.mutate(category.id)}
              onAddChild={() => openCreate(category.id)}
            />

            {(category.children ?? []).length > 0 && (
              <ul className="border-t border-border pl-6">
                {(category.children ?? []).map((child) => (
                  <li key={child.id} className="border-b border-border last:border-b-0">
                    <CategoryRow
                      category={child}
                      onEdit={() => {
                        setEditing(child)
                        setParentId(child.parentId ?? null)
                        setFormOpen(true)
                      }}
                      onDelete={() => deleteCategory.mutate(child.id)}
                    />
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>

      <CategoryFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        category={editing}
        parentId={parentId}
        type={type}
        parents={categories}
      />
    </div>
  )
}

function CategoryRow({
  category,
  onEdit,
  onDelete,
  onAddChild,
}: {
  category: Category
  onEdit: () => void
  onDelete: () => void
  onAddChild?: () => void
}) {
  return (
    <div className="group flex items-center gap-3 px-4 py-2.5">
      <span
        className="h-4 w-4 shrink-0 rounded-full"
        style={{ backgroundColor: category.color }}
        aria-hidden
      />
      <span className="flex-1 truncate text-sm font-medium">{category.name}</span>
      {category.isSystem && (
        <Lock className="h-3 w-3 text-muted-foreground" aria-label="Danh mục hệ thống" />
      )}

      <div className="flex gap-0.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
        {onAddChild && (
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Thêm danh mục con của ${category.name}`}
            onClick={onAddChild}
          >
            <Plus className="h-3.5 w-3.5" aria-hidden />
          </Button>
        )}
        <Button variant="ghost" size="icon" aria-label={`Sửa danh mục ${category.name}`} onClick={onEdit}>
          <Pencil className="h-3.5 w-3.5" aria-hidden />
        </Button>
        {!category.isSystem && (
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Xóa danh mục ${category.name}`}
            onClick={onDelete}
          >
            <Trash2 className="h-3.5 w-3.5 text-destructive" aria-hidden />
          </Button>
        )}
      </div>
    </div>
  )
}

function CategoryFormDialog({
  open,
  onOpenChange,
  category,
  parentId,
  type,
  parents,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  category: Category | null
  parentId: string | null
  type: CategoryType
  parents: Category[]
}) {
  const createCategory = useCreateCategory()
  const updateCategory = useUpdateCategory()

  const [name, setName] = useState('')
  const [color, setColor] = useState(PALETTE[0])
  const [parent, setParent] = useState('')
  const [error, setError] = useState('')

  const [lastOpenedFor, setLastOpenedFor] = useState<string | null>(null)
  const key = open ? `${category?.id ?? 'new'}:${parentId ?? ''}` : null
  if (key !== lastOpenedFor) {
    setLastOpenedFor(key)
    setName(category?.name ?? '')
    setColor(category?.color ?? PALETTE[0])
    setParent(category?.parentId ?? parentId ?? '')
    setError('')
  }

  /** Saves, keeping the dialog open if the request fails (the toast carries the reason). */
  async function submit() {
    if (!name.trim()) {
      setError('Tên danh mục không được để trống')
      return
    }
    try {
      if (category) {
        await updateCategory.mutateAsync({
          id: category.id,
          input: { name: name.trim(), color, parentId: parent || null },
        })
      } else {
        await createCategory.mutateAsync({
          name: name.trim(),
          type,
          color,
          parentId: parent || null,
        })
      }
      onOpenChange(false)
    } catch {
      setError('Không lưu được danh mục. Xem thông báo lỗi ở góc màn hình.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{category ? 'Sửa danh mục' : 'Thêm danh mục'}</DialogTitle>
          <DialogDescription>Danh mục hỗ trợ tối đa 2 cấp.</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="category-name">Tên danh mục</Label>
            <Input
              id="category-name"
              value={name}
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
            <Label htmlFor="category-parent">Danh mục cha</Label>
            <Select
              id="category-parent"
              value={parent}
              onChange={(event) => setParent(event.target.value)}
            >
              <option value="">— Không có (danh mục gốc) —</option>
              {parents
                .filter((option) => option.id !== category?.id)
                .map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.name}
                  </option>
                ))}
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label>Màu</Label>
            <div className="flex flex-wrap gap-2">
              {PALETTE.map((option) => (
                <button
                  key={option}
                  type="button"
                  aria-label={`Chọn màu ${option}`}
                  aria-pressed={color === option}
                  onClick={() => setColor(option)}
                  className={cn(
                    'h-7 w-7 rounded-full border-2 transition-transform',
                    color === option ? 'border-foreground scale-110' : 'border-transparent',
                  )}
                  style={{ backgroundColor: option }}
                />
              ))}
            </div>
          </div>
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
