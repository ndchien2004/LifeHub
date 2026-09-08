import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { useState } from 'react'
import { TaskCard } from './TaskCard'
import { useChangeTaskStatus } from '../hooks'
import { STATUS_LABELS, TASK_STATUSES, type Task, type TaskStatus } from '../types'
import { cn } from '@/shared/lib/utils'

interface TaskKanbanViewProps {
  tasks: Task[]
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
}

/**
 * Decides what a drop should change, if anything.
 *
 * <p>Extracted from the drag handler so the rule is testable without driving a real pointer
 * gesture through jsdom: a drop outside any column, onto an unknown task, or back into the column
 * the card came from must all issue no request at all (T1-14).
 */
export function resolveStatusChange(
  tasks: Task[],
  activeId: string,
  overId: string | number | undefined | null,
): { id: string; status: TaskStatus } | null {
  if (!overId) {
    return null
  }
  const targetStatus = String(overId) as TaskStatus
  if (!TASK_STATUSES.includes(targetStatus)) {
    return null
  }

  const task = tasks.find((candidate) => candidate.id === activeId)
  if (!task || task.status === targetStatus) {
    return null
  }
  return { id: activeId, status: targetStatus }
}

/**
 * Kanban board, one column per status (FR-TSK-07).
 *
 * <p>Dropping a card issues {@code PATCH /tasks/{id}/status} and nothing else - the dedicated
 * endpoint exists precisely so a drag does not have to send a whole task back (T1-14).
 */
export function TaskKanbanView({ tasks, onEdit, onDelete }: TaskKanbanViewProps) {
  const changeStatus = useChangeTaskStatus()
  const [draggingTask, setDraggingTask] = useState<Task | null>(null)

  // A small activation distance keeps a plain click on the card from starting a drag.
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

  function handleDragStart(event: DragStartEvent) {
    setDraggingTask(tasks.find((task) => task.id === event.active.id) ?? null)
  }

  function handleDragEnd(event: DragEndEvent) {
    setDraggingTask(null)

    const change = resolveStatusChange(tasks, String(event.active.id), event.over?.id)
    if (change) {
      changeStatus.mutate(change)
    }
  }

  return (
    <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {TASK_STATUSES.map((status) => (
          <KanbanColumn
            key={status}
            status={status}
            tasks={tasks.filter((task) => task.status === status)}
            onEdit={onEdit}
            onDelete={onDelete}
          />
        ))}
      </div>

      <DragOverlay>
        {draggingTask && (
          <div className="w-72 rotate-2 opacity-90">
            <TaskCard task={draggingTask} onEdit={onEdit} onDelete={onDelete} compact />
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}

function KanbanColumn({
  status,
  tasks,
  onEdit,
  onDelete,
}: {
  status: TaskStatus
  tasks: Task[]
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
}) {
  const { setNodeRef, isOver } = useDroppable({ id: status })

  return (
    <section
      ref={setNodeRef}
      aria-label={STATUS_LABELS[status]}
      data-status={status}
      className={cn(
        'flex min-h-[200px] flex-col gap-2 rounded-lg border border-dashed p-3 transition-colors',
        isOver ? 'border-primary bg-accent' : 'border-border bg-muted/30',
      )}
    >
      <header className="flex items-center justify-between px-1">
        <h3 className="text-sm font-semibold">{STATUS_LABELS[status]}</h3>
        <span className="rounded bg-muted px-1.5 py-0.5 text-xs tabular-nums text-muted-foreground">
          {tasks.length}
        </span>
      </header>

      <div className="flex flex-col gap-2">
        {tasks.map((task) => (
          <DraggableTask key={task.id} task={task} onEdit={onEdit} onDelete={onDelete} />
        ))}
        {tasks.length === 0 && (
          <p className="px-1 py-6 text-center text-xs text-muted-foreground">Kéo task vào đây</p>
        )}
      </div>
    </section>
  )
}

function DraggableTask({
  task,
  onEdit,
  onDelete,
}: {
  task: Task
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: task.id })

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      data-testid={`kanban-task-${task.id}`}
      className={cn('cursor-grab active:cursor-grabbing', isDragging && 'opacity-40')}
    >
      <TaskCard task={task} onEdit={onEdit} onDelete={onDelete} compact />
    </div>
  )
}
