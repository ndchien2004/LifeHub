import { AlertTriangle, CheckCircle2, ListTodo, Repeat } from 'lucide-react'
import { formatTime } from '@/shared/lib/dateUtils'
import { cn } from '@/shared/lib/utils'
import type { CalendarItem } from '../types'

/**
 * One item as it appears in a grid cell.
 *
 * Events and task deadlines are deliberately styled apart (FR-CAL-10): an event is a filled block
 * because it occupies time, a task is an outlined marker with a checklist icon because it is a
 * moment something is due. Conflicts and overdue deadlines are the two states that carry an icon of
 * their own, so they survive being scanned rather than read (FR-CAL-11, FR-TSK-12).
 */

interface CalendarItemChipProps {
  item: CalendarItem
  onSelect: (item: CalendarItem) => void
  /** Compact form for the month grid, where a cell holds several items. */
  dense?: boolean
}

export function CalendarItemChip({ item, onSelect, dense = false }: CalendarItemChipProps) {
  const isTask = item.kind === 'TASK'
  const isDone = item.taskStatus === 'DONE'
  const label = describe(item)

  return (
    <button
      type="button"
      onClick={() => onSelect(item)}
      title={label}
      aria-label={label}
      className={cn(
        'flex w-full items-center gap-1 overflow-hidden rounded px-1.5 text-left transition-colors',
        dense ? 'h-5 text-[11px]' : 'py-1 text-xs',
        isTask
          ? 'border border-dashed border-primary/60 bg-primary/5 text-foreground hover:bg-primary/10'
          : 'bg-primary/90 text-primary-foreground hover:bg-primary',
        item.hasConflict && 'ring-1 ring-destructive',
        isDone && 'opacity-60 line-through',
      )}
    >
      {isTask ? (
        isDone ? (
          <CheckCircle2 className="h-3 w-3 shrink-0" aria-hidden />
        ) : (
          <ListTodo className={cn('h-3 w-3 shrink-0', item.isOverdue && 'text-destructive')} aria-hidden />
        )
      ) : null}

      {!item.allDay && !isTask && (
        <span className="shrink-0 tabular-nums opacity-80">{formatTime(item.startAt)}</span>
      )}

      <span className="truncate">{item.title}</span>

      {item.isRecurring && <Repeat className="ml-auto h-3 w-3 shrink-0 opacity-70" aria-hidden />}
      {item.hasConflict && (
        <AlertTriangle className="ml-auto h-3 w-3 shrink-0 text-destructive" aria-hidden />
      )}
    </button>
  )
}

/** The tooltip and accessible name, which is where the detail that does not fit on screen goes. */
function describe(item: CalendarItem): string {
  const parts: string[] = []
  if (item.kind === 'TASK') {
    parts.push(`Task đến hạn ${formatTime(item.startAt)}`)
  } else if (item.allDay) {
    parts.push('Cả ngày')
  } else {
    parts.push(`${formatTime(item.startAt)} – ${formatTime(item.endAt)}`)
  }
  parts.push(item.title)
  if (item.location) {
    parts.push(item.location)
  }
  if (item.hasConflict) {
    parts.push('Trùng giờ với sự kiện khác')
  }
  if (item.isOverdue) {
    parts.push('Đã quá hạn')
  }
  return parts.join(' · ')
}
