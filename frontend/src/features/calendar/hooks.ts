import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/components/ui/toast'
import { ApiRequestError } from '@/shared/lib/apiClient'
import { taskKeys } from '@/features/tasks/api'
import * as api from './api'
import { calendarKeys, reminderKeys } from './api'
import type { EventInput, OccurrenceInput } from './types'

/**
 * Data hooks for the calendar module.
 *
 * Every mutation invalidates the whole calendar rather than one range. A repeating series reaches
 * across windows, and a split creates a second event, so patching a single cached range would leave
 * the neighbouring months showing the previous rule.
 */

function invalidateCalendar(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: calendarKeys.all })
  void queryClient.invalidateQueries({ queryKey: reminderKeys.all })
  // A task linked to an event, or a deadline drawn on the grid, can change with it.
  void queryClient.invalidateQueries({ queryKey: taskKeys.all })
}

/** Turns a backend error into the Vietnamese message the user should see (NFR-USE-03). */
function reportError(error: unknown, fallback: string) {
  toast.error(error instanceof ApiRequestError ? error.message : fallback)
}

export function useCalendarRange(from: Date, to: Date, includeTasks: boolean) {
  const fromIso = from.toISOString()
  const toIso = to.toISOString()

  return useQuery({
    queryKey: calendarKeys.range(fromIso, toIso, includeTasks),
    queryFn: () => api.fetchCalendar(fromIso, toIso, includeTasks),
    // The grid is re-requested constantly while paging through months; keeping the previous
    // result on screen is what stops every arrow click from flashing an empty calendar.
    placeholderData: (previous) => previous,
  })
}

export function useEvent(id: string | null) {
  return useQuery({
    queryKey: calendarKeys.event(id ?? ''),
    queryFn: () => api.fetchEvent(id as string),
    enabled: Boolean(id),
  })
}

/**
 * Events already occupying a proposed slot (FR-CAL-11).
 *
 * Only enabled once both ends of the slot are known, so an incomplete form does not fire a request
 * on every keystroke.
 */
export function useConflicts(from: string | null, to: string | null, excludeEventId?: string) {
  return useQuery({
    queryKey: calendarKeys.conflicts(from ?? '', to ?? '', excludeEventId),
    queryFn: () => api.fetchConflicts(from as string, to as string, excludeEventId),
    enabled: Boolean(from && to && new Date(to as string) > new Date(from as string)),
  })
}

export function useCreateEvent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: EventInput) => api.createEvent(input),
    onSuccess: () => {
      invalidateCalendar(queryClient)
      toast.success('Đã tạo sự kiện')
    },
    onError: (error) => reportError(error, 'Không tạo được sự kiện'),
  })
}

export function useUpdateEvent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: Partial<EventInput> }) =>
      api.updateEvent(id, input),
    onSuccess: () => {
      invalidateCalendar(queryClient)
      toast.success('Đã lưu thay đổi')
    },
    onError: (error) => reportError(error, 'Không lưu được thay đổi'),
  })
}

/** Edits one instance of a series at the chosen scope (FR-CAL-04, SD-05). */
export function useUpdateOccurrence() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      eventId,
      occurrenceStart,
      input,
    }: {
      eventId: string
      occurrenceStart: string
      input: OccurrenceInput
    }) => api.updateOccurrence(eventId, occurrenceStart, input),
    onSuccess: () => {
      invalidateCalendar(queryClient)
      toast.success('Đã lưu thay đổi')
    },
    onError: (error) => reportError(error, 'Không lưu được thay đổi'),
  })
}

export function useDeleteEvent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.deleteEvent(id),
    onSuccess: () => {
      invalidateCalendar(queryClient)
      toast.success('Đã xóa sự kiện')
    },
    onError: (error) => reportError(error, 'Không xóa được sự kiện'),
  })
}

export function useDeleteOccurrence() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ eventId, occurrenceStart }: { eventId: string; occurrenceStart: string }) =>
      api.deleteOccurrence(eventId, occurrenceStart),
    onSuccess: () => {
      invalidateCalendar(queryClient)
      toast.success('Đã xóa lần này khỏi chuỗi lặp')
    },
    onError: (error) => reportError(error, 'Không xóa được lần này'),
  })
}

export function useMissedReminders(enabled = true) {
  return useQuery({
    queryKey: reminderKeys.missed,
    queryFn: api.fetchMissedReminders,
    enabled,
  })
}

export function useSnoozeReminder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, minutes }: { id: string; minutes: number }) =>
      api.snoozeReminder(id, minutes),
    onSuccess: () => {
      invalidateCalendar(queryClient)
      toast.success('Đã hoãn nhắc hẹn')
    },
    onError: (error) => reportError(error, 'Không hoãn được nhắc hẹn'),
  })
}

export function useDismissReminder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.dismissReminder(id),
    onSuccess: () => invalidateCalendar(queryClient),
    onError: (error) => reportError(error, 'Không tắt được nhắc hẹn'),
  })
}

export function useDismissAllReminders() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.dismissAllReminders,
    onSuccess: () => invalidateCalendar(queryClient),
    onError: (error) => reportError(error, 'Không tắt được danh sách nhắc hẹn'),
  })
}
