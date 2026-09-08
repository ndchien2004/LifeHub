import { Input, Label, Select } from '@/shared/components/ui/input'
import { cn } from '@/shared/lib/utils'
import {
  describeRrule,
  WEEKDAYS,
  WEEKDAY_LABELS,
  type Frequency,
  type Recurrence,
  type Weekday,
} from '../lib/rrule'

/**
 * Visual RRULE builder (FR-CAL-03).
 *
 * The rule the controls produce is echoed back in Vietnamese underneath. Nobody can verify
 * `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE` at a glance, and getting a repetition rule subtly wrong is
 * the kind of mistake that only surfaces weeks later, once the wrong dates are already on the
 * calendar.
 */

const FREQUENCY_OPTIONS: { value: Frequency; label: string }[] = [
  { value: 'NONE', label: 'Không lặp lại' },
  { value: 'DAILY', label: 'Hằng ngày' },
  { value: 'WEEKLY', label: 'Hằng tuần' },
  { value: 'MONTHLY', label: 'Hằng tháng' },
  { value: 'YEARLY', label: 'Hằng năm' },
]

const INTERVAL_NOUNS: Record<Exclude<Frequency, 'NONE'>, string> = {
  DAILY: 'ngày',
  WEEKLY: 'tuần',
  MONTHLY: 'tháng',
  YEARLY: 'năm',
}

interface RecurrenceBuilderProps {
  value: Recurrence
  onChange: (next: Recurrence) => void
  /** Preview text, computed by the caller from the rule it is about to send. */
  rrule: string | null
  disabled?: boolean
}

export function RecurrenceBuilder({ value, onChange, rrule, disabled }: RecurrenceBuilderProps) {
  const repeats = value.freq !== 'NONE'

  const toggleWeekday = (day: Weekday) => {
    const byDay = value.byDay.includes(day)
      ? value.byDay.filter((existing) => existing !== day)
      : [...value.byDay, day]
    onChange({ ...value, byDay })
  }

  return (
    <div className="space-y-3 rounded-md border border-border p-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <Label htmlFor="event-freq">Lặp lại</Label>
          <Select
            id="event-freq"
            value={value.freq}
            disabled={disabled}
            onChange={(event) =>
              onChange({ ...value, freq: event.target.value as Frequency, byDay: [] })
            }
          >
            {FREQUENCY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>

        {repeats && (
          <div className="space-y-1">
            <Label htmlFor="event-interval">Khoảng cách</Label>
            <div className="flex items-center gap-2">
              <Input
                id="event-interval"
                type="number"
                min={1}
                max={99}
                className="w-20"
                disabled={disabled}
                value={value.interval}
                onChange={(event) =>
                  onChange({ ...value, interval: Math.max(1, Number(event.target.value) || 1) })
                }
              />
              <span className="text-sm text-muted-foreground">
                {INTERVAL_NOUNS[value.freq as Exclude<Frequency, 'NONE'>]} một lần
              </span>
            </div>
          </div>
        )}
      </div>

      {value.freq === 'WEEKLY' && (
        <div className="space-y-1">
          <Label>Vào các ngày</Label>
          <div className="flex flex-wrap gap-1">
            {WEEKDAYS.map((day) => (
              <button
                key={day}
                type="button"
                disabled={disabled}
                aria-pressed={value.byDay.includes(day)}
                onClick={() => toggleWeekday(day)}
                className={cn(
                  'h-8 w-10 rounded-md border text-xs transition-colors',
                  value.byDay.includes(day)
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'border-border hover:bg-accent',
                )}
              >
                {WEEKDAY_LABELS[day]}
              </button>
            ))}
          </div>
          <p className="text-xs text-muted-foreground">
            Không chọn ngày nào thì lặp theo đúng thứ của ngày bắt đầu.
          </p>
        </div>
      )}

      {repeats && (
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="event-end-type">Kết thúc</Label>
            <Select
              id="event-end-type"
              value={value.end.type}
              disabled={disabled}
              onChange={(event) => {
                const type = event.target.value as Recurrence['end']['type']
                if (type === 'COUNT') {
                  onChange({ ...value, end: { type: 'COUNT', count: 10 } })
                } else if (type === 'UNTIL') {
                  onChange({ ...value, end: { type: 'UNTIL', until: '' } })
                } else {
                  onChange({ ...value, end: { type: 'NEVER' } })
                }
              }}
            >
              <option value="NEVER">Không bao giờ</option>
              <option value="COUNT">Sau số lần</option>
              <option value="UNTIL">Đến ngày</option>
            </Select>
          </div>

          {value.end.type === 'COUNT' && (
            <div className="space-y-1">
              <Label htmlFor="event-end-count">Số lần</Label>
              <Input
                id="event-end-count"
                type="number"
                min={1}
                max={999}
                disabled={disabled}
                value={value.end.count}
                onChange={(event) =>
                  onChange({
                    ...value,
                    end: { type: 'COUNT', count: Math.max(1, Number(event.target.value) || 1) },
                  })
                }
              />
            </div>
          )}

          {value.end.type === 'UNTIL' && (
            <div className="space-y-1">
              <Label htmlFor="event-end-until">Đến ngày</Label>
              <Input
                id="event-end-until"
                type="date"
                disabled={disabled}
                value={value.end.until}
                onChange={(event) =>
                  onChange({ ...value, end: { type: 'UNTIL', until: event.target.value } })
                }
              />
            </div>
          )}
        </div>
      )}

      <p className="text-sm text-muted-foreground" aria-live="polite">
        {describeRrule(rrule)}
      </p>
    </div>
  )
}
