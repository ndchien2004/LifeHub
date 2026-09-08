import { format } from 'date-fns'
import { vi } from 'date-fns/locale'
import { CalendarPlus, ChevronLeft, ChevronRight } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button } from '@/shared/components/ui/button'
import { cn } from '@/shared/lib/utils'
import { CalendarMonthView } from './components/CalendarMonthView'
import { CalendarTimeGrid } from './components/CalendarTimeGrid'
import { EditScopeDialog } from './components/EditScopeDialog'
import { EventDetailDialog } from './components/EventDetailDialog'
import { EventFormDialog } from './components/EventFormDialog'
import { useCalendarRange, useDeleteEvent, useDeleteOccurrence, useEvent } from './hooks'
import { rangeFor, shiftAnchor, weekGrid } from './lib/grid'
import type { CalendarItem, CalendarViewMode, EditScope } from './types'

/**
 * The calendar screen (FR-CAL-02).
 *
 * Holds the three things the child views all need: which window is on screen, which item is
 * selected, and the scope prompt. The scope prompt is modelled as a promise the form awaits rather
 * than as another piece of state each caller has to thread, so "ask, then continue or abandon"
 * reads as one flow at the call site (FR-CAL-04).
 */

const VIEW_LABELS: Record<CalendarViewMode, string> = {
  month: 'Tháng',
  week: 'Tuần',
  day: 'Ngày',
}

export function CalendarPage() {
  const [mode, setMode] = useState<CalendarViewMode>('month')
  const [anchor, setAnchor] = useState(() => new Date())
  const [includeTasks, setIncludeTasks] = useState(true)

  const [selected, setSelected] = useState<CalendarItem | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<CalendarItem | null>(null)
  const [defaultStart, setDefaultStart] = useState<Date | null>(null)

  const [scopeRequest, setScopeRequest] = useState<{
    action: 'edit' | 'delete'
    resolve: (scope: EditScope | null) => void
  } | null>(null)

  const { from, to } = useMemo(() => rangeFor(mode, anchor), [mode, anchor])
  const { data: items = [], isLoading } = useCalendarRange(from, to, includeTasks)
  const { data: editingEvent } = useEvent(editing?.eventId ?? null)
  const { data: selectedEvent } = useEvent(selected?.eventId ?? null)

  const deleteEvent = useDeleteEvent()
  const deleteOccurrence = useDeleteOccurrence()

  /** Opens the scope dialog and resolves once the user picks or backs out. */
  const requestScope = useCallback(
    (action: 'edit' | 'delete') =>
      new Promise<EditScope | null>((resolve) => setScopeRequest({ action, resolve })),
    [],
  )

  const openCreate = useCallback((start?: Date | null) => {
    setEditing(null)
    setDefaultStart(start ?? null)
    setFormOpen(true)
  }, [])

  // `N` creates an event, matching the shortcut the task screen uses (Phase 1 convention).
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const typing =
        target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable
      if (typing || event.ctrlKey || event.metaKey || event.altKey) {
        return
      }
      if (event.key === 'n' || event.key === 'N') {
        event.preventDefault()
        openCreate(null)
      }
      if (event.key === 't' || event.key === 'T') {
        setAnchor(new Date())
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [openCreate])

  const handleSelectItem = (item: CalendarItem) => {
    setSelected(item)
    setDetailOpen(true)
  }

  const handleSelectDay = (day: Date) => {
    setAnchor(day)
    setMode('day')
  }

  const handleEdit = () => {
    setEditing(selected)
    setDetailOpen(false)
    setDefaultStart(null)
    setFormOpen(true)
  }

  const handleDelete = async () => {
    if (!selected?.eventId) {
      return
    }
    setDetailOpen(false)

    if (!selected.isRecurring) {
      await deleteEvent.mutateAsync(selected.eventId)
      return
    }

    const scope = await requestScope('delete')
    if (!scope) {
      return
    }
    if (scope === 'THIS_ONLY') {
      await deleteOccurrence.mutateAsync({
        eventId: selected.eventId,
        occurrenceStart: selected.occurrenceStart,
      })
    } else {
      // THIS_AND_FOLLOWING has no dedicated endpoint; removing the whole series is the closest
      // honest action, and the scope dialog already told the user what each option does.
      await deleteEvent.mutateAsync(selected.eventId)
    }
  }

  return (
    <div className="flex h-full flex-col gap-3 p-4">
      <header className="flex flex-wrap items-center gap-2">
        <h1 className="mr-2 text-lg font-semibold">Lịch</h1>

        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="icon"
            aria-label="Kỳ trước"
            onClick={() => setAnchor((current) => shiftAnchor(mode, current, -1))}
          >
            <ChevronLeft className="h-4 w-4" aria-hidden />
          </Button>
          <Button variant="outline" size="sm" onClick={() => setAnchor(new Date())}>
            Hôm nay
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Kỳ sau"
            onClick={() => setAnchor((current) => shiftAnchor(mode, current, 1))}
          >
            <ChevronRight className="h-4 w-4" aria-hidden />
          </Button>
        </div>

        <span className="min-w-44 text-sm font-medium capitalize" aria-live="polite">
          {titleFor(mode, anchor)}
        </span>

        <div className="ml-auto flex items-center gap-2">
          <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <input
              type="checkbox"
              className="h-3.5 w-3.5 rounded border-border"
              checked={includeTasks}
              onChange={(event) => setIncludeTasks(event.target.checked)}
            />
            Hiện task đến hạn
          </label>

          <div className="flex rounded-md border border-border p-0.5">
            {(Object.keys(VIEW_LABELS) as CalendarViewMode[]).map((option) => (
              <button
                key={option}
                type="button"
                aria-pressed={mode === option}
                onClick={() => setMode(option)}
                className={cn(
                  'rounded px-2.5 py-1 text-xs transition-colors',
                  mode === option
                    ? 'bg-accent font-medium text-accent-foreground'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {VIEW_LABELS[option]}
              </button>
            ))}
          </div>

          <Button size="sm" onClick={() => openCreate(null)}>
            <CalendarPlus className="h-4 w-4" aria-hidden />
            Thêm sự kiện
          </Button>
        </div>
      </header>

      {isLoading && items.length === 0 ? (
        <div className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
          Đang tải lịch…
        </div>
      ) : mode === 'month' ? (
        <CalendarMonthView
          anchor={anchor}
          items={items}
          onSelectItem={handleSelectItem}
          onSelectDay={handleSelectDay}
          onCreateAt={openCreate}
        />
      ) : (
        <CalendarTimeGrid
          days={mode === 'week' ? weekGrid(anchor) : [anchor]}
          items={items}
          onSelectItem={handleSelectItem}
          onCreateAt={openCreate}
        />
      )}

      <EventDetailDialog
        open={detailOpen}
        onOpenChange={setDetailOpen}
        item={selected}
        event={selectedEvent}
        onEdit={handleEdit}
        onDelete={handleDelete}
      />

      <EventFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        event={editingEvent}
        occurrenceStart={editing?.occurrenceStart ?? null}
        defaultStart={defaultStart}
        requestScope={() => requestScope('edit')}
      />

      <EditScopeDialog
        open={scopeRequest !== null}
        action={scopeRequest?.action ?? 'edit'}
        onCancel={() => {
          scopeRequest?.resolve(null)
          setScopeRequest(null)
        }}
        onConfirm={(scope) => {
          scopeRequest?.resolve(scope)
          setScopeRequest(null)
        }}
      />
    </div>
  )
}

function titleFor(mode: CalendarViewMode, anchor: Date): string {
  if (mode === 'day') {
    return format(anchor, "EEEE, dd 'tháng' M yyyy", { locale: vi })
  }
  if (mode === 'week') {
    const days = weekGrid(anchor)
    return `${format(days[0]!, 'dd/MM')} – ${format(days[6]!, 'dd/MM/yyyy')}`
  }
  return format(anchor, "'Tháng' M yyyy", { locale: vi })
}
