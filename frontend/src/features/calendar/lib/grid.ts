import {
  addDays,
  addMonths,
  addWeeks,
  eachDayOfInterval,
  endOfDay,
  endOfMonth,
  endOfWeek,
  isSameDay,
  startOfDay,
  startOfMonth,
  startOfWeek,
} from 'date-fns'
import type { CalendarItem, CalendarViewMode } from '../types'

/**
 * Date arithmetic behind the three calendar views (FR-CAL-02).
 *
 * The week starts on Monday throughout, matching the `app.week_start` default in
 * 03-DATA-MODEL.md §2.11. Phase 4 makes that a setting; until then it is one constant here rather
 * than a literal scattered across the view components.
 */

const WEEK_OPTIONS = { weekStartsOn: 1 } as const

/** Column headings for the month and week grids. */
export const WEEKDAY_HEADINGS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']

/**
 * The window the grid needs from the API.
 *
 * A month view shows leading and trailing days of the neighbouring months, so the request has to
 * cover the whole rendered grid rather than the calendar month — otherwise those cells would be
 * silently empty.
 */
export function rangeFor(mode: CalendarViewMode, anchor: Date): { from: Date; to: Date } {
  if (mode === 'day') {
    return { from: startOfDay(anchor), to: endOfDay(anchor) }
  }
  if (mode === 'week') {
    return { from: startOfWeek(anchor, WEEK_OPTIONS), to: endOfWeek(anchor, WEEK_OPTIONS) }
  }
  return {
    from: startOfWeek(startOfMonth(anchor), WEEK_OPTIONS),
    to: endOfWeek(endOfMonth(anchor), WEEK_OPTIONS),
  }
}

/** Every day cell of the month grid, including the neighbouring days that fill the first and last rows. */
export function monthGrid(anchor: Date): Date[] {
  const { from, to } = rangeFor('month', anchor)
  return eachDayOfInterval({ start: from, end: to })
}

export function weekGrid(anchor: Date): Date[] {
  const { from } = rangeFor('week', anchor)
  return Array.from({ length: 7 }, (_, index) => addDays(from, index))
}

/** Moves the anchor one view forward or back. */
export function shiftAnchor(mode: CalendarViewMode, anchor: Date, direction: 1 | -1): Date {
  if (mode === 'day') {
    return addDays(anchor, direction)
  }
  if (mode === 'week') {
    return addWeeks(anchor, direction)
  }
  return addMonths(anchor, direction)
}

/**
 * The items to draw in one day cell.
 *
 * An item counts as belonging to a day when it overlaps it, not merely when it starts on it, so a
 * multi-day event appears on every day it spans.
 */
export function itemsOnDay(items: CalendarItem[], day: Date): CalendarItem[] {
  const dayStart = startOfDay(day).getTime()
  const dayEnd = endOfDay(day).getTime()

  return items.filter((item) => {
    const start = new Date(item.startAt).getTime()
    const end = new Date(item.endAt).getTime()
    if (start === end) {
      // A task deadline is a moment rather than a span, so overlap would never match it.
      return isSameDay(new Date(item.startAt), day)
    }
    return start <= dayEnd && end > dayStart
  })
}

/**
 * Where a timed item sits on a 24 hour column, as fractions of the day.
 *
 * Clamped to the column so an event running past midnight is drawn to the bottom edge rather than
 * overflowing the next day's, and given a floor of fifteen minutes so a very short event stays
 * clickable.
 */
export function blockPosition(item: CalendarItem, day: Date): { top: number; height: number } {
  const dayStart = startOfDay(day).getTime()
  const dayLength = 24 * 60 * 60 * 1000
  const start = new Date(item.startAt).getTime()
  const end = new Date(item.endAt).getTime()

  const top = Math.min(Math.max((start - dayStart) / dayLength, 0), 1)
  const rawHeight = (Math.max(end, start + 15 * 60 * 1000) - dayStart) / dayLength - top
  return { top, height: Math.min(Math.max(rawHeight, 0.01), 1 - top) }
}
