import { apiFetch } from '@/shared/lib/apiClient'
import type {
  CalendarEvent,
  CalendarItem,
  EventInput,
  OccurrenceInput,
  Reminder,
} from './types'

/** REST calls for the calendar module. Query keys live next to them so invalidation stays honest. */

export const calendarKeys = {
  all: ['calendar'] as const,
  range: (from: string, to: string, includeTasks: boolean) =>
    ['calendar', 'range', from, to, includeTasks] as const,
  event: (id: string) => ['calendar', 'event', id] as const,
  conflicts: (from: string, to: string, excludeEventId?: string) =>
    ['calendar', 'conflicts', from, to, excludeEventId ?? ''] as const,
}

export const reminderKeys = {
  all: ['reminders'] as const,
  missed: ['reminders', 'missed'] as const,
}

/**
 * The occurrence anchor is a path segment, not a query parameter.
 *
 * Its `+07:00` offset has to survive the round trip: encoded as a path segment the plus sign stays
 * a plus, whereas form encoding would turn it into a space and the backend would read a different
 * moment.
 */
function occurrencePath(eventId: string, occurrenceStart: string): string {
  return `/events/${encodeURIComponent(eventId)}/occurrences/${encodeURIComponent(occurrenceStart)}`
}

export function fetchCalendar(
  from: string,
  to: string,
  includeTasks: boolean,
): Promise<CalendarItem[]> {
  const params = new URLSearchParams({ from, to, includeTasks: String(includeTasks) })
  return apiFetch<CalendarItem[]>(`/events?${params.toString()}`)
}

export function fetchEvent(id: string): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>(`/events/${id}`)
}

export function createEvent(input: EventInput): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>('/events', { method: 'POST', body: JSON.stringify(input) })
}

/** Updates the master row, so the change reaches every occurrence of a series. */
export function updateEvent(id: string, input: Partial<EventInput>): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>(`/events/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

/** Edits one instance at the requested scope; a split answers with the new series (FR-CAL-04). */
export function updateOccurrence(
  eventId: string,
  occurrenceStart: string,
  input: OccurrenceInput,
): Promise<CalendarEvent> {
  return apiFetch<CalendarEvent>(occurrencePath(eventId, occurrenceStart), {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteEvent(id: string): Promise<void> {
  return apiFetch<void>(`/events/${id}`, { method: 'DELETE' })
}

/** Removes a single instance by recording a cancellation, leaving the series intact. */
export function deleteOccurrence(eventId: string, occurrenceStart: string): Promise<void> {
  return apiFetch<void>(occurrencePath(eventId, occurrenceStart), { method: 'DELETE' })
}

export function fetchConflicts(
  from: string,
  to: string,
  excludeEventId?: string,
): Promise<CalendarItem[]> {
  const params = new URLSearchParams({ from, to })
  if (excludeEventId) {
    params.set('excludeEventId', excludeEventId)
  }
  return apiFetch<CalendarItem[]>(`/events/conflicts?${params.toString()}`)
}

export function fetchMissedReminders(): Promise<Reminder[]> {
  return apiFetch<Reminder[]>('/reminders/missed')
}

export function snoozeReminder(id: string, minutes: number): Promise<Reminder> {
  return apiFetch<Reminder>(`/reminders/${id}/snooze`, {
    method: 'POST',
    body: JSON.stringify({ minutes }),
  })
}

export function dismissReminder(id: string): Promise<Reminder> {
  return apiFetch<Reminder>(`/reminders/${id}/dismiss`, { method: 'POST' })
}

export function dismissAllReminders(): Promise<{ dismissed: number }> {
  return apiFetch<{ dismissed: number }>('/reminders/dismiss-all', { method: 'POST' })
}
