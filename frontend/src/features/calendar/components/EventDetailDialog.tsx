import { AlertTriangle, Bell, CalendarClock, Link2, MapPin, Repeat, Trash2 } from 'lucide-react'
import { Button } from '@/shared/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog'
import { formatDateTime, formatTime } from '@/shared/lib/dateUtils'
import { describeRrule } from '../lib/rrule'
import { REMINDER_OFFSET_LABELS, type CalendarEvent, type CalendarItem } from '../types'

/**
 * Read-only view of one calendar item, and the entry point to editing or removing it.
 *
 * Shows the instance the user clicked rather than the master row: for a repeating event those
 * differ, and quoting the series start here would tell them about a date they did not select.
 */

interface EventDetailDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  item: CalendarItem | null
  /** The master row, loaded lazily; absent while it is still in flight. */
  event?: CalendarEvent | null
  onEdit: () => void
  onDelete: () => void
}

export function EventDetailDialog({
  open,
  onOpenChange,
  item,
  event,
  onEdit,
  onDelete,
}: EventDetailDialogProps) {
  if (!item) {
    return null
  }

  const isTask = item.kind === 'TASK'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="pr-6">{item.title}</DialogTitle>
          <DialogDescription>
            {isTask
              ? `Task đến hạn ${formatDateTime(item.startAt)}`
              : item.allDay
                ? `Cả ngày · ${formatDateTime(item.startAt)}`
                : `${formatDateTime(item.startAt)} – ${formatTime(item.endAt)}`}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2 text-sm">
          {item.location && (
            <p className="flex items-center gap-2">
              <MapPin className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              {item.location}
            </p>
          )}

          {item.isRecurring && (
            <p className="flex items-center gap-2">
              <Repeat className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              {describeRrule(event?.rrule)}
              {item.isException && (
                <span className="text-xs text-muted-foreground">(lần này đã được sửa riêng)</span>
              )}
            </p>
          )}

          {item.reminders.length > 0 && (
            <p className="flex items-center gap-2">
              <Bell className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              {item.reminders
                .map((reminder) => REMINDER_OFFSET_LABELS[reminder.offsetMinutes] ?? `${reminder.offsetMinutes} phút trước`)
                .join(', ')}
            </p>
          )}

          {!isTask && item.linkedTaskId && (
            <p className="flex items-center gap-2">
              <Link2 className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              {event?.linkedTaskTitle ?? 'Có task liên kết'}
            </p>
          )}

          {item.hasConflict && (
            <p className="flex items-center gap-2 text-destructive">
              <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
              Trùng giờ với một sự kiện khác
            </p>
          )}

          {item.description && (
            <p className="whitespace-pre-wrap pt-1 text-muted-foreground">{item.description}</p>
          )}

          {isTask && (
            <p className="flex items-center gap-2 pt-1 text-xs text-muted-foreground">
              <CalendarClock className="h-3.5 w-3.5 shrink-0" aria-hidden />
              Mục này là hạn chót của một task. Sửa nó ở màn hình Công việc.
            </p>
          )}
        </div>

        {!isTask && (
          <DialogFooter>
            <Button variant="ghost" onClick={onDelete}>
              <Trash2 className="h-4 w-4" aria-hidden />
              Xóa
            </Button>
            <Button onClick={onEdit}>Sửa</Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}
