import { isSameMonth, isToday } from 'date-fns'
import { cn } from '@/shared/lib/utils'
import { CalendarItemChip } from './CalendarItemChip'
import { itemsOnDay, monthGrid, WEEKDAY_HEADINGS } from '../lib/grid'
import type { CalendarItem } from '../types'

/**
 * Month grid (FR-CAL-02).
 *
 * A cell shows at most {@link MAX_VISIBLE} items and then a count, because a busy day would
 * otherwise stretch its row and shear the whole grid. Clicking the count switches to the day view,
 * which is where a full list belongs.
 */

const MAX_VISIBLE = 3

interface CalendarMonthViewProps {
  anchor: Date
  items: CalendarItem[]
  onSelectItem: (item: CalendarItem) => void
  onSelectDay: (day: Date) => void
  onCreateAt: (day: Date) => void
}

export function CalendarMonthView({
  anchor,
  items,
  onSelectItem,
  onSelectDay,
  onCreateAt,
}: CalendarMonthViewProps) {
  const days = monthGrid(anchor)

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-border">
      <div className="grid grid-cols-7 border-b border-border bg-muted/40">
        {WEEKDAY_HEADINGS.map((heading) => (
          <div key={heading} className="px-2 py-1.5 text-center text-xs font-medium text-muted-foreground">
            {heading}
          </div>
        ))}
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-7 auto-rows-fr">
        {days.map((day) => {
          const dayItems = itemsOnDay(items, day)
          const outside = !isSameMonth(day, anchor)

          return (
            <div
              key={day.toISOString()}
              className={cn(
                'flex min-h-24 flex-col gap-0.5 border-b border-r border-border p-1',
                outside && 'bg-muted/30',
              )}
              // Double click on empty space is the fastest way to start an event on that day.
              onDoubleClick={() => onCreateAt(day)}
            >
              <button
                type="button"
                onClick={() => onSelectDay(day)}
                className={cn(
                  'mb-0.5 h-6 w-6 shrink-0 self-start rounded-full text-xs tabular-nums transition-colors',
                  outside ? 'text-muted-foreground/60' : 'text-foreground',
                  isToday(day) && 'bg-primary font-semibold text-primary-foreground',
                  !isToday(day) && 'hover:bg-accent',
                )}
                aria-label={`Xem ngày ${day.getDate()}`}
              >
                {day.getDate()}
              </button>

              {dayItems.slice(0, MAX_VISIBLE).map((item) => (
                <CalendarItemChip
                  key={`${item.eventId ?? item.linkedTaskId}-${item.occurrenceStart}`}
                  item={item}
                  onSelect={onSelectItem}
                  dense
                />
              ))}

              {dayItems.length > MAX_VISIBLE && (
                <button
                  type="button"
                  onClick={() => onSelectDay(day)}
                  className="px-1.5 text-left text-[11px] text-muted-foreground hover:text-foreground"
                >
                  +{dayItems.length - MAX_VISIBLE} mục khác
                </button>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
