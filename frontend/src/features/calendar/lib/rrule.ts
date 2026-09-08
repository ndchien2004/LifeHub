/**
 * A visual RRULE builder, and its Vietnamese rendering (FR-CAL-03).
 *
 * RFC 5545 is far larger than anything a person would set from a form, so this covers the subset
 * the SRS asks for — FREQ, INTERVAL, BYDAY, and one of COUNT or UNTIL — and passes anything it does
 * not recognise through untouched. The backend, not this module, is the authority on what a rule
 * means; the point here is to keep the user from having to type `FREQ=WEEKLY;BYDAY=MO,WE`.
 */

export const FREQUENCIES = ['NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'] as const
export type Frequency = (typeof FREQUENCIES)[number]

export const WEEKDAYS = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'] as const
export type Weekday = (typeof WEEKDAYS)[number]

/** Short labels for the weekday toggles, in the Vietnamese week order (Monday first). */
export const WEEKDAY_LABELS: Record<Weekday, string> = {
  MO: 'T2',
  TU: 'T3',
  WE: 'T4',
  TH: 'T5',
  FR: 'T6',
  SA: 'T7',
  SU: 'CN',
}

const WEEKDAY_WORDS: Record<Weekday, string> = {
  MO: 'thứ 2',
  TU: 'thứ 3',
  WE: 'thứ 4',
  TH: 'thứ 5',
  FR: 'thứ 6',
  SA: 'thứ 7',
  SU: 'chủ nhật',
}

const FREQUENCY_NOUNS: Record<Exclude<Frequency, 'NONE'>, string> = {
  DAILY: 'ngày',
  WEEKLY: 'tuần',
  MONTHLY: 'tháng',
  YEARLY: 'năm',
}

const EVERY_LABELS: Record<Exclude<Frequency, 'NONE'>, string> = {
  DAILY: 'Hằng ngày',
  WEEKLY: 'Hằng tuần',
  MONTHLY: 'Hằng tháng',
  YEARLY: 'Hằng năm',
}

export type RecurrenceEnd =
  | { type: 'NEVER' }
  | { type: 'COUNT'; count: number }
  /** `yyyy-MM-dd`, as a date input produces it. */
  | { type: 'UNTIL'; until: string }

export interface Recurrence {
  freq: Frequency
  interval: number
  /** Only meaningful for WEEKLY; empty means "the weekday the event starts on". */
  byDay: Weekday[]
  end: RecurrenceEnd
}

export const NO_RECURRENCE: Recurrence = { freq: 'NONE', interval: 1, byDay: [], end: { type: 'NEVER' } }

/** Splits `FREQ=WEEKLY;BYDAY=MO` into its parts, tolerating the `RRULE:` property prefix. */
function partsOf(rrule: string): Map<string, string> {
  const value = rrule.trim().replace(/^RRULE:/i, '')
  const parts = new Map<string, string>()
  for (const chunk of value.split(';')) {
    const [key, ...rest] = chunk.split('=')
    if (key && rest.length > 0) {
      parts.set(key.trim().toUpperCase(), rest.join('=').trim())
    }
  }
  return parts
}

/** Turns the form state into an RRULE, or null when the event does not repeat. */
export function buildRrule(recurrence: Recurrence): string | null {
  if (recurrence.freq === 'NONE') {
    return null
  }

  const parts = [`FREQ=${recurrence.freq}`]
  if (recurrence.interval > 1) {
    parts.push(`INTERVAL=${recurrence.interval}`)
  }
  if (recurrence.freq === 'WEEKLY' && recurrence.byDay.length > 0) {
    // Kept in calendar order rather than click order, so the same selection always
    // produces the same string and an untouched form never looks edited.
    const ordered = WEEKDAYS.filter((day) => recurrence.byDay.includes(day))
    parts.push(`BYDAY=${ordered.join(',')}`)
  }
  if (recurrence.end.type === 'COUNT' && recurrence.end.count > 0) {
    parts.push(`COUNT=${recurrence.end.count}`)
  }
  if (recurrence.end.type === 'UNTIL' && recurrence.end.until) {
    parts.push(`UNTIL=${toUntilStamp(recurrence.end.until)}`)
  }
  return parts.join(';')
}

/**
 * Reads an RRULE back into form state.
 *
 * Anything unrecognised degrades to the closest representable rule rather than throwing — the form
 * has to open even on a rule this builder did not write.
 */
export function parseRrule(rrule?: string | null): Recurrence {
  if (!rrule?.trim()) {
    return NO_RECURRENCE
  }
  const parts = partsOf(rrule)
  const freq = parts.get('FREQ') as Frequency | undefined
  if (!freq || !FREQUENCIES.includes(freq) || freq === 'NONE') {
    return NO_RECURRENCE
  }

  const interval = Number(parts.get('INTERVAL') ?? 1)
  const byDay = (parts.get('BYDAY') ?? '')
    .split(',')
    .map((day) => day.trim().toUpperCase())
    .filter((day): day is Weekday => (WEEKDAYS as readonly string[]).includes(day))

  let end: RecurrenceEnd = { type: 'NEVER' }
  const count = Number(parts.get('COUNT') ?? 0)
  const until = parts.get('UNTIL')
  if (count > 0) {
    end = { type: 'COUNT', count }
  } else if (until) {
    end = { type: 'UNTIL', until: fromUntilStamp(until) }
  }

  return {
    freq,
    interval: Number.isFinite(interval) && interval > 0 ? interval : 1,
    byDay,
    end,
  }
}

/**
 * The rule in plain Vietnamese, for the line under the recurrence controls.
 *
 * The form shows this instead of the raw rule because "Hằng tuần vào thứ 2, thứ 4" is checkable at
 * a glance and `FREQ=WEEKLY;BYDAY=MO,WE` is not.
 */
export function describeRrule(rrule?: string | null): string {
  const recurrence = parseRrule(rrule)
  if (recurrence.freq === 'NONE') {
    return 'Không lặp lại'
  }

  const noun = FREQUENCY_NOUNS[recurrence.freq]
  let text =
    recurrence.interval > 1
      ? `Mỗi ${recurrence.interval} ${noun}`
      : EVERY_LABELS[recurrence.freq]

  if (recurrence.freq === 'WEEKLY' && recurrence.byDay.length > 0) {
    const days = WEEKDAYS.filter((day) => recurrence.byDay.includes(day))
      .map((day) => WEEKDAY_WORDS[day])
      .join(', ')
    text += ` vào ${days}`
  }

  if (recurrence.end.type === 'COUNT') {
    text += `, ${recurrence.end.count} lần`
  } else if (recurrence.end.type === 'UNTIL' && recurrence.end.until) {
    text += `, đến ${formatUntil(recurrence.end.until)}`
  }
  return text
}

/**
 * `yyyy-MM-dd` to the RFC 5545 UTC stamp.
 *
 * Anchored to the last second of the local day, so "until 15/09" includes the fifteenth rather than
 * stopping at midnight before it.
 */
function toUntilStamp(until: string): string {
  const localEndOfDay = new Date(`${until}T23:59:59`)
  if (Number.isNaN(localEndOfDay.getTime())) {
    return until
  }
  return localEndOfDay.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
}

/** The RFC 5545 UTC stamp back to `yyyy-MM-dd` in the local zone. */
function fromUntilStamp(stamp: string): string {
  const match = /^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})Z?)?$/.exec(stamp.trim())
  if (!match) {
    return ''
  }
  const [, year, month, day, hour = '00', minute = '00', second = '00'] = match
  const utc = new Date(
    Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second)),
  )
  const local = new Date(utc.getTime() - utc.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

function formatUntil(until: string): string {
  const [year, month, day] = until.split('-')
  return day && month && year ? `${day}/${month}/${year}` : until
}
