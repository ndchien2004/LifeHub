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
import { cn } from '@/shared/lib/utils'
import { EDIT_SCOPES, EDIT_SCOPE_LABELS, type EditScope } from '../types'

/**
 * Asks how far a change to one occurrence of a series should reach (FR-CAL-04, SD-05).
 *
 * Deliberately has no default beyond the narrowest option, and cannot be dismissed into a save.
 * Applying an edit to a whole series when the user meant one date is not something the app can undo
 * for them, so the choice is made explicitly every time.
 */

const SCOPE_HINTS: Record<EditScope, string> = {
  THIS_ONLY: 'Các lần khác trong chuỗi giữ nguyên.',
  THIS_AND_FOLLOWING: 'Chuỗi cũ dừng trước lần này; từ đây trở đi thành một chuỗi mới.',
  ALL: 'Mọi lần trong chuỗi, kể cả những lần đã qua, đều đổi theo.',
}

interface EditScopeDialogProps {
  open: boolean
  /** What the scope applies to, so the wording matches the action. */
  action: 'edit' | 'delete'
  onCancel: () => void
  onConfirm: (scope: EditScope) => void
}

export function EditScopeDialog({ open, action, onCancel, onConfirm }: EditScopeDialogProps) {
  const [scope, setScope] = useState<EditScope>('THIS_ONLY')

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {action === 'delete' ? 'Xóa sự kiện lặp lại' : 'Áp dụng thay đổi cho...'}
          </DialogTitle>
          <DialogDescription>
            Sự kiện này thuộc một chuỗi lặp. Chọn phạm vi trước khi{' '}
            {action === 'delete' ? 'xóa' : 'lưu'}.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          {EDIT_SCOPES.map((option) => (
            <label
              key={option}
              className={cn(
                'flex cursor-pointer gap-3 rounded-md border p-3 transition-colors',
                scope === option ? 'border-primary bg-accent/50' : 'border-border hover:bg-accent/30',
              )}
            >
              <input
                type="radio"
                name="edit-scope"
                className="mt-1 h-4 w-4"
                checked={scope === option}
                onChange={() => setScope(option)}
              />
              <span>
                <span className="block text-sm font-medium">{EDIT_SCOPE_LABELS[option]}</span>
                <span className="block text-xs text-muted-foreground">{SCOPE_HINTS[option]}</span>
              </span>
            </label>
          ))}
        </div>

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onCancel}>
            Hủy
          </Button>
          <Button
            type="button"
            variant={action === 'delete' ? 'destructive' : 'default'}
            onClick={() => onConfirm(scope)}
          >
            {action === 'delete' ? 'Xóa' : 'Áp dụng'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
