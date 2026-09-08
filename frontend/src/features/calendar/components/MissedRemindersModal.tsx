import { BellOff, CalendarClock, Clock } from 'lucide-react'
import { Button } from '@/shared/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog'
import { formatDateTime } from '@/shared/lib/dateUtils'
import { useDismissAllReminders, useDismissReminder, useSnoozeReminder } from '../hooks'
import type { Reminder } from '../types'

/**
 * "Bạn đã bỏ lỡ N nhắc hẹn" (FR-CAL-09, UC-05).
 *
 * Shown once at startup for reminders whose moment passed while the app was closed, within the last
 * 24 hours — anything older is expired by the backend and deliberately not offered, because a
 * reminder for something three days gone is noise rather than information.
 */

/** UC-05 step 3 offers an hour, which is long enough to be a real "later". */
const SNOOZE_MINUTES = 60

interface MissedRemindersModalProps {
  reminders: Reminder[]
  open: boolean
  onClose: () => void
  /** Jumps the calendar to the event a reminder points at. */
  onOpenEvent: (reminder: Reminder) => void
}

export function MissedRemindersModal({
  reminders,
  open,
  onClose,
  onOpenEvent,
}: MissedRemindersModalProps) {
  const snooze = useSnoozeReminder()
  const dismiss = useDismissReminder()
  const dismissAll = useDismissAllReminders()

  const handleDismissAll = async () => {
    await dismissAll.mutateAsync()
    onClose()
  }

  return (
    <Dialog open={open && reminders.length > 0} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[80vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Bạn đã bỏ lỡ {reminders.length} nhắc hẹn</DialogTitle>
          <DialogDescription>
            Đây là các nhắc hẹn đến hạn trong lúc ứng dụng không chạy, trong vòng 24 giờ qua.
          </DialogDescription>
        </DialogHeader>

        <ul className="space-y-2">
          {reminders.map((reminder) => (
            <li key={reminder.id} className="rounded-md border border-border p-3">
              <p className="text-sm font-medium">{reminder.title}</p>
              <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                <Clock className="h-3 w-3" aria-hidden />
                {formatDateTime(reminder.triggerAt)} · {reminder.body}
              </p>

              <div className="mt-2 flex flex-wrap gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onOpenEvent(reminder)}
                  disabled={reminder.refType !== 'EVENT'}
                >
                  <CalendarClock className="h-3.5 w-3.5" aria-hidden />
                  Mở sự kiện
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => snooze.mutate({ id: reminder.id, minutes: SNOOZE_MINUTES })}
                >
                  Nhắc lại sau 1 giờ
                </Button>
                <Button size="sm" variant="ghost" onClick={() => dismiss.mutate(reminder.id)}>
                  <BellOff className="h-3.5 w-3.5" aria-hidden />
                  Bỏ qua
                </Button>
              </div>
            </li>
          ))}
        </ul>

        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            Để sau
          </Button>
          <Button onClick={handleDismissAll} disabled={dismissAll.isPending}>
            Bỏ qua tất cả
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
