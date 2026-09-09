import { zodResolver } from '@hookform/resolvers/zod'
import { AlertTriangle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/shared/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/components/ui/dialog'
import { FieldError, Input, Label, Select, Textarea } from '@/shared/components/ui/input'
import { useDebounce } from '@/shared/hooks/useDebounce'
import { formatDateTime } from '@/shared/lib/dateUtils'
import { cn } from '@/shared/lib/utils'
import { useTasks } from '@/features/tasks/hooks'
import { useConflicts, useCreateEvent, useUpdateEvent, useUpdateOccurrence } from '../hooks'
import { buildRrule, NO_RECURRENCE, parseRrule, type Recurrence } from '../lib/rrule'
import {
  REMINDER_OFFSETS,
  REMINDER_OFFSET_LABELS,
  type CalendarEvent,
  type EditScope,
  type EventInput,
} from '../types'
import { AiFieldHint, aiFieldClass } from '@/features/ai/components/AiFieldHint'
import type { FieldConfidence } from '@/features/ai/types'
import { RecurrenceBuilder } from './RecurrenceBuilder'

/**
 * Create and edit form for an event (FR-CAL-01, FR-CAL-03, FR-CAL-05, FR-CAL-06).
 *
 * Two things here are more than plumbing. The conflict banner (FR-CAL-11) warns but never blocks —
 * a double booking is often deliberate, so refusing to save would be wrong. And when the event
 * repeats, saving does not write the master directly: it asks the caller for a scope first, because
 * silently applying an edit to every occurrence is the one mistake a calendar cannot undo for you.
 */

const eventSchema = z
  .object({
    title: z.string().trim().min(1, 'Tiêu đề không được để trống').max(255, 'Tiêu đề tối đa 255 ký tự'),
    description: z.string().optional(),
    location: z.string().max(255, 'Địa điểm tối đa 255 ký tự').optional(),
    allDay: z.boolean(),
    startAt: z.string().min(1, 'Thiếu thời gian bắt đầu'),
    endAt: z.string().min(1, 'Thiếu thời gian kết thúc'),
    taskId: z.string().optional(),
  })
  .refine((values) => new Date(values.endAt) > new Date(values.startAt), {
    message: 'Thời gian kết thúc phải sau thời gian bắt đầu',
    path: ['endAt'],
  })

type EventFormValues = z.infer<typeof eventSchema>

interface EventFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present when editing; absent when creating. */
  event?: CalendarEvent | null
  /** The instance being edited, when the user opened this from one occurrence of a series. */
  occurrenceStart?: string | null
  /** Prefilled start for a new event, from a click on the grid. */
  defaultStart?: Date | null
  /**
   * Asks the user how far the edit reaches. Resolves to null when they back out (FR-CAL-04).
   * Only called for a repeating event.
   */
  requestScope: () => Promise<EditScope | null>
  /** Prefilled values for a new event, e.g. from the AI command palette (FR-AI-06). */
  defaults?: Partial<EventFormValues>
  /** Reminder intervals to preselect alongside {@code defaults}. */
  defaultReminderOffsets?: number[]
  /** Per-field AI certainty, which draws the badges beside the labels (UC-09 step 10). */
  aiConfidence?: FieldConfidence
}

export function EventFormDialog({
  open,
  onOpenChange,
  event,
  occurrenceStart,
  defaultStart,
  requestScope,
  defaults,
  defaultReminderOffsets,
  aiConfidence,
}: EventFormDialogProps) {
  const isEditing = Boolean(event)
  const [recurrence, setRecurrence] = useState<Recurrence>(NO_RECURRENCE)
  const [reminderOffsets, setReminderOffsets] = useState<number[]>([15])

  const createEvent = useCreateEvent()
  const updateEvent = useUpdateEvent()
  const updateOccurrence = useUpdateOccurrence()
  const { data: taskPage } = useTasks({ status: ['TODO', 'IN_PROGRESS'], size: 100 })

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<EventFormValues>({
    resolver: zodResolver(eventSchema),
    defaultValues: emptyValues(defaultStart ?? null),
  })

  useEffect(() => {
    if (!open) {
      return
    }
    if (event) {
      reset(valuesFrom(event))
      setRecurrence(parseRrule(event.rrule))
      setReminderOffsets(event.reminderOffsets ?? [])
    } else {
      reset({ ...emptyValues(defaultStart ?? null), ...defaults })
      setRecurrence(NO_RECURRENCE)
      setReminderOffsets(defaultReminderOffsets ?? [15])
    }
    // `defaults` is a fresh object on every render of the caller, so it is deliberately not a
    // dependency: including it would reset the form under the user mid-edit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, event, defaultStart, reset])

  const allDay = watch('allDay')
  const startAt = watch('startAt')
  const endAt = watch('endAt')
  const rrule = buildRrule(recurrence)

  // Debounced so dragging through a time field does not fire a request per keystroke.
  const debouncedStart = useDebounce(startAt, 400)
  const debouncedEnd = useDebounce(endAt, 400)
  const { data: conflicts = [] } = useConflicts(
    toIso(debouncedStart),
    toIso(debouncedEnd),
    event?.id,
  )

  const toggleReminder = (offset: number) => {
    setReminderOffsets((current) =>
      current.includes(offset)
        ? current.filter((existing) => existing !== offset)
        : [...current, offset].sort((a, b) => a - b),
    )
  }

  const onSubmit = handleSubmit(async (values) => {
    const input: EventInput = {
      title: values.title.trim(),
      description: values.description?.trim() || null,
      location: values.location?.trim() || null,
      startAt: new Date(values.startAt).toISOString(),
      endAt: new Date(values.endAt).toISOString(),
      allDay: values.allDay,
      rrule,
      taskId: values.taskId || null,
      reminderOffsets,
    }

    if (!isEditing || !event) {
      await createEvent.mutateAsync(input)
      onOpenChange(false)
      return
    }

    // A one-off event has a single occurrence, so there is nothing to choose between.
    if (!event.rrule || !occurrenceStart) {
      await updateEvent.mutateAsync({ id: event.id, input })
      onOpenChange(false)
      return
    }

    const scope = await requestScope()
    if (!scope) {
      return
    }
    await updateOccurrence.mutateAsync({
      eventId: event.id,
      occurrenceStart,
      input: {
        scope,
        title: input.title,
        startAt: input.startAt,
        endAt: input.endAt,
        // THIS_ONLY can only carry a new time and title; the override table holds nothing else
        // (03-DATA-MODEL.md §6, item M-19), so the rest is sent only for the wider scopes.
        ...(scope === 'THIS_ONLY'
          ? {}
          : {
              description: input.description ?? undefined,
              location: input.location ?? undefined,
              rrule: rrule ?? undefined,
              reminderOffsets,
            }),
      },
    })
    onOpenChange(false)
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-h-[90vh] max-w-2xl overflow-y-auto"
        onKeyDown={(keyEvent) => {
          if (keyEvent.key === 'Enter' && (keyEvent.ctrlKey || keyEvent.metaKey)) {
            keyEvent.preventDefault()
            void onSubmit()
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Sửa sự kiện' : 'Thêm sự kiện'}</DialogTitle>
          <DialogDescription>
            Lưu bằng <kbd className="rounded border border-border px-1 text-xs">Ctrl</kbd> +{' '}
            <kbd className="rounded border border-border px-1 text-xs">Enter</kbd>, đóng bằng{' '}
            <kbd className="rounded border border-border px-1 text-xs">Esc</kbd>.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          <div className="space-y-1">
            <Label htmlFor="event-title" className="flex items-center gap-2">
              Tiêu đề
              <AiFieldHint confidence={aiConfidence?.title} />
            </Label>
            <Input
              id="event-title"
              autoFocus
              className={aiFieldClass(aiConfidence?.title)}
              {...register('title')}
            />
            <FieldError>{errors.title?.message}</FieldError>
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 rounded border-border" {...register('allDay')} />
            Sự kiện cả ngày
          </label>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="event-start" className="flex items-center gap-2">
                Bắt đầu
                <AiFieldHint confidence={aiConfidence?.startAt} />
              </Label>
              <Input
                id="event-start"
                type={allDay ? 'date' : 'datetime-local'}
                {...register('startAt')}
              />
              <FieldError>{errors.startAt?.message}</FieldError>
            </div>
            <div className="space-y-1">
              <Label htmlFor="event-end" className="flex items-center gap-2">
                Kết thúc
                <AiFieldHint confidence={aiConfidence?.endAt} />
              </Label>
              <Input id="event-end" type={allDay ? 'date' : 'datetime-local'} {...register('endAt')} />
              <FieldError>{errors.endAt?.message}</FieldError>
            </div>
          </div>

          {conflicts.length > 0 && (
            <div
              role="status"
              className="flex gap-2 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm"
            >
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" aria-hidden />
              <div>
                <p className="font-medium">Khoảng thời gian này đã có lịch khác</p>
                <ul className="mt-1 space-y-0.5 text-muted-foreground">
                  {conflicts.slice(0, 3).map((clash) => (
                    <li key={`${clash.eventId}-${clash.occurrenceStart}`}>
                      {clash.title} — {formatDateTime(clash.startAt)}
                    </li>
                  ))}
                </ul>
                <p className="mt-1 text-xs text-muted-foreground">
                  Bạn vẫn có thể lưu nếu trùng lịch là có chủ ý.
                </p>
              </div>
            </div>
          )}

          <div className="space-y-1">
            <Label htmlFor="event-location">Địa điểm</Label>
            <Input id="event-location" {...register('location')} />
            <FieldError>{errors.location?.message}</FieldError>
          </div>

          <div className="space-y-1">
            <Label htmlFor="event-description">Mô tả</Label>
            <Textarea id="event-description" rows={2} {...register('description')} />
          </div>

          <RecurrenceBuilder value={recurrence} onChange={setRecurrence} rrule={rrule} />

          <div className="space-y-1">
            <Label>Nhắc trước</Label>
            <div className="flex flex-wrap gap-1">
              {REMINDER_OFFSETS.map((offset) => (
                <button
                  key={offset}
                  type="button"
                  aria-pressed={reminderOffsets.includes(offset)}
                  onClick={() => toggleReminder(offset)}
                  className={cn(
                    'rounded-full border px-3 py-1 text-xs transition-colors',
                    reminderOffsets.includes(offset)
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-border hover:bg-accent',
                  )}
                >
                  {REMINDER_OFFSET_LABELS[offset]}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-1">
            <Label htmlFor="event-task">Liên kết với task</Label>
            <Select id="event-task" {...register('taskId')}>
              <option value="">Không liên kết</option>
              {(taskPage?.items ?? []).map((task) => (
                <option key={task.id} value={task.id}>
                  {task.title}
                </option>
              ))}
            </Select>
          </div>

          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isEditing ? 'Lưu thay đổi' : 'Tạo sự kiện'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** `datetime-local` needs a local wall-clock string with no zone, which toISOString cannot give. */
function toLocalInput(value: Date, dateOnly = false): string {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, dateOnly ? 10 : 16)
}

function toIso(value: string | undefined): string | null {
  if (!value) {
    return null
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}

function emptyValues(defaultStart: Date | null): EventFormValues {
  const start = defaultStart ?? nextHour()
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  return {
    title: '',
    description: '',
    location: '',
    allDay: false,
    startAt: toLocalInput(start),
    endAt: toLocalInput(end),
    taskId: '',
  }
}

function valuesFrom(event: CalendarEvent): EventFormValues {
  return {
    title: event.title,
    description: event.description ?? '',
    location: event.location ?? '',
    allDay: event.allDay,
    startAt: toLocalInput(new Date(event.startAt), event.allDay),
    endAt: toLocalInput(new Date(event.endAt), event.allDay),
    taskId: event.linkedTaskId ?? '',
  }
}

function nextHour(): Date {
  const at = new Date()
  at.setMinutes(0, 0, 0)
  at.setHours(at.getHours() + 1)
  return at
}
