/** Wire types for the calendar module (06-API-SPEC.md sections 5 and 6). */

import type { Priority, TaskStatus } from '@/features/tasks/types'

/** How far an edit to one occurrence of a series reaches (FR-CAL-04). */
export const EDIT_SCOPES = ['THIS_ONLY', 'THIS_AND_FOLLOWING', 'ALL'] as const
export type EditScope = (typeof EDIT_SCOPES)[number]

export const EDIT_SCOPE_LABELS: Record<EditScope, string> = {
  THIS_ONLY: 'Chỉ lần này',
  THIS_AND_FOLLOWING: 'Lần này và các lần sau',
  ALL: 'Tất cả các lần',
}

/** The six intervals FR-CAL-06 allows, in minutes before the event. */
export const REMINDER_OFFSETS = [0, 5, 15, 30, 60, 1440] as const
export type ReminderOffset = (typeof REMINDER_OFFSETS)[number]

export const REMINDER_OFFSET_LABELS: Record<number, string> = {
  0: 'Đúng giờ',
  5: '5 phút trước',
  15: '15 phút trước',
  30: '30 phút trước',
  60: '1 giờ trước',
  1440: '1 ngày trước',
}

export type ReminderStatus = 'PENDING' | 'FIRED' | 'SNOOZED' | 'DISMISSED' | 'EXPIRED'

export interface ReminderRef {
  id: string
  offsetMinutes: number
}

/**
 * One item on the calendar grid.
 *
 * `eventId` plus `occurrenceStart` identify an instance of a series. `occurrenceStart` is the slot
 * the recurrence rule produced and stays the anchor even after an exception moved the instance, so
 * it can differ from `startAt` — both are needed to edit or delete a single instance.
 *
 * Task deadlines arrive in the same list with `kind: 'TASK'` and no `eventId` (FR-CAL-10).
 */
export interface CalendarItem {
  kind: 'EVENT' | 'TASK'
  eventId?: string
  occurrenceStart: string
  title: string
  description?: string
  location?: string
  startAt: string
  endAt: string
  allDay: boolean
  isRecurring: boolean
  isException: boolean
  hasConflict: boolean
  linkedTaskId?: string
  reminders: ReminderRef[]
  taskStatus?: TaskStatus
  taskPriority?: Priority
  isOverdue?: boolean
}

/** The master row behind a series, which is what the edit form loads. */
export interface CalendarEvent {
  id: string
  title: string
  description?: string
  location?: string
  startAt: string
  endAt: string
  allDay: boolean
  rrule?: string
  timezone: string
  linkedTaskId?: string
  linkedTaskTitle?: string
  reminderOffsets: number[]
  createdAt: string
  updatedAt: string
}

export interface Reminder {
  id: string
  title: string
  body: string
  refType: 'EVENT' | 'TASK'
  refId: string
  triggerAt: string
  occurrenceStart: string
  offsetMinutes: number
  status: ReminderStatus
  firedAt?: string
}

export interface EventInput {
  title: string
  description?: string | null
  location?: string | null
  startAt: string
  endAt: string
  allDay: boolean
  rrule?: string | null
  timezone?: string
  taskId?: string | null
  reminderOffsets: number[]
}

/** Body of PATCH /events/{id}/occurrences/{occurrenceStart}. */
export interface OccurrenceInput {
  scope: EditScope
  title?: string
  startAt?: string
  endAt?: string
  description?: string
  location?: string
  rrule?: string
  reminderOffsets?: number[]
}

export type CalendarViewMode = 'month' | 'week' | 'day'
