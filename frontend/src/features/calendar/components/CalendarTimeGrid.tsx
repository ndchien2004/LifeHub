import { format, isToday, startOfDay } from 'date-fns'
import { vi } from 'date-fns/locale'
import { useEffect, useRef } from 'react'
import { cn } from '@/shared/lib/utils'
import { CalendarItemChip } from './CalendarItemChip'
import { blockPosition, itemsOnDay } from '../lib/grid'
import type { CalendarItem } from '../types'

/**
 * The week and day views, which share one 24 hour column layout (FR-CAL-02).
 *
 * All-day items are pulled out into a strip above the grid: they have no position on an hour scale,
 * and stretching them across it would bury every timed event underneath.
 */

const HOURS = Array.from({ length: 24 }, (_, hour) => hour)
const HOUR_HEIGHT_REM = 3

interface CalendarTimeGridProps {
  days: Date[]
  items: CalendarItem[]
  onSelectItem: (item: CalendarItem) => void
  onCreateAt: (day: Date) => void
}

export function CalendarTimeGrid({ days, items, onSelectItem, onCreateAt }: CalendarTimeGridProps) {
  const scrollRef = useRef<HTMLDivElement>(null)

  // Opens near the working day rather than at midnight, which is empty on every calendar.
  useEffect(() => {
    const container = scrollRef.current
    if (container) {
      container.scrollTop = (7 / 24) * container.scrollHeight
    }
  }, [days.length])

  const allDayByDay = days.map((day) => itemsOnDay(items, day).filter((item) => item.allDay))
  const hasAllDay = allDayByDay.some((dayItems) => dayItems.length > 0)

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-border">
      <div className="flex shrink-0 border-b border-border bg-muted/40">
        <div className="w-14 shrink-0" />
        {days.map((day) => (
          <div key={day.toISOString()} className="flex-1 px-2 py-1.5 text-center">
            <div className="text-xs text-muted-foreground">
              {format(day, 'EEEEEE', { locale: vi })}
            </div>
            <div
              className={cn(
                'mx-auto mt-0.5 flex h-6 w-6 items-center justify-center rounded-full text-sm tabular-nums',
                isToday(day) && 'bg-primary font-semibold text-primary-foreground',
              )}
            >
              {day.getDate()}
            </div>
          </div>
        ))}
      </div>

      {hasAllDay && (
        <div className="flex shrink-0 border-b border-border">
          <div className="w-14 shrink-0 px-2 py-1 text-right text-[11px] text-muted-foreground">
            Cả ngày
          </div>
          {days.map((day, index) => (
            <div key={day.toISOString()} className="flex-1 space-y-0.5 border-l border-border p-1">
              {(allDayByDay[index] ?? []).map((item) => (
                <CalendarItemChip
                  key={`${item.eventId}-${item.occurrenceStart}`}
                  item={item}
                  onSelect={onSelectItem}
                  dense
                />
              ))}
            </div>
          ))}
        </div>
      )}

      <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto">
        <div className="flex" style={{ height: `${HOURS.length * HOUR_HEIGHT_REM}rem` }}>
          <div className="w-14 shrink-0">
            {HOURS.map((hour) => (
              <div
                key={hour}
                className="relative border-b border-border/50 pr-2 text-right text-[11px] text-muted-foreground"
                style={{ height: `${HOUR_HEIGHT_REM}rem` }}
              >
                <span className="absolute right-2 -top-1.5 tabular-nums">
                  {hour === 0 ? '' : `${String(hour).padStart(2, '0')}:00`}
                </span>
              </div>
            ))}
          </div>

          {days.map((day) => {
            const timed = itemsOnDay(items, day).filter((item) => !item.allDay)

            return (
              <div
                key={day.toISOString()}
                className="relative flex-1 border-l border-border"
                onDoubleClick={(event) => onCreateAt(hourFromClick(event, day))}
              >
                {HOURS.map((hour) => (
                  <div
                    key={hour}
                    className="border-b border-border/50"
                    style={{ height: `${HOUR_HEIGHT_REM}rem` }}
                  />
                ))}

                {timed.map((item) => {
                  const { top, height } = blockPosition(item, day)
                  return (
                    <div
                      key={`${item.eventId ?? item.linkedTaskId}-${item.occurrenceStart}`}
                      className="absolute inset-x-0.5"
                      style={{ top: `${top * 100}%`, height: `${height * 100}%` }}
                    >
                      <CalendarItemChip item={item} onSelect={onSelectItem} />
                    </div>
                  )
                })}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

/** Turns a double click inside a day column into the hour it landed on. */
function hourFromClick(event: React.MouseEvent<HTMLDivElement>, day: Date): Date {
  const bounds = event.currentTarget.getBoundingClientRect()
  const fraction = (event.clientY - bounds.top) / bounds.height
  const hour = Math.min(23, Math.max(0, Math.floor(fraction * 24)))
  const at = startOfDay(day)
  at.setHours(hour)
  return at
}
