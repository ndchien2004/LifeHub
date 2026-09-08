import { AlertTriangle, CalendarClock, ListTree, Pencil, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/shared/components/ui/button'
import { formatDateTime } from '@/shared/lib/dateUtils'
import { cn } from '@/shared/lib/utils'
import { PRIORITY_CLASSES, PRIORITY_LABELS, type Task } from '../types'

interface TaskCardProps {
  task: Task
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
  onAddSubtask?: (task: Task) => void
  /** Kanban cards are draggable and drop the action row; list rows keep it. */
  compact?: boolean
}

/**
 * One task, rendered the same way in both views so the two never drift apart.
 *
 * <p>An overdue task gets a red border, red date text and a warning icon - three independent
 * signals, because colour alone is not a reliable channel (FR-TSK-12).
 */
export function TaskCard({ task, onEdit, onDelete, onAddSubtask, compact = false }: TaskCardProps) {
  const isDone = task.status === 'DONE'

  return (
    <div
      className={cn(
        'group rounded-lg border bg-card p-3 transition-colors',
        task.isOverdue ? 'border-destructive/60' : 'border-border',
        isDone && 'opacity-60',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <button
          type="button"
          onClick={() => onEdit(task)}
          className="flex-1 text-left text-sm font-medium leading-snug hover:underline"
          title="Mở để sửa"
        >
          <span className={cn(isDone && 'line-through')}>{task.title}</span>
        </button>

        {!compact && (
          <div className="flex shrink-0 gap-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
            {onAddSubtask && !task.parentId && (
              <Button
                size="icon"
                variant="ghost"
                aria-label={`Thêm subtask cho ${task.title}`}
                onClick={() => onAddSubtask(task)}
              >
                <Plus className="h-4 w-4" aria-hidden />
              </Button>
            )}
            <Button size="icon" variant="ghost" aria-label={`Sửa ${task.title}`} onClick={() => onEdit(task)}>
              <Pencil className="h-4 w-4" aria-hidden />
            </Button>
            <Button
              size="icon"
              variant="ghost"
              aria-label={`Xóa ${task.title}`}
              onClick={() => onDelete(task)}
              className="text-destructive hover:text-destructive"
            >
              <Trash2 className="h-4 w-4" aria-hidden />
            </Button>
          </div>
        )}
      </div>

      {task.description && !compact && (
        <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{task.description}</p>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
        <span className={cn('rounded px-1.5 py-0.5 font-medium', PRIORITY_CLASSES[task.priority])}>
          {PRIORITY_LABELS[task.priority]}
        </span>

        {task.project && (
          <span
            className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-muted-foreground"
            style={{ backgroundColor: `${task.project.color}22` }}
          >
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: task.project.color }} />
            {task.project.name}
          </span>
        )}

        {task.tags.map((tag) => (
          <span
            key={tag.id}
            className="rounded-full px-2 py-0.5 text-[11px] text-white"
            style={{ backgroundColor: tag.color }}
          >
            {tag.name}
          </span>
        ))}

        {task.subtaskCount > 0 && (
          <span className="inline-flex items-center gap-1 text-muted-foreground">
            <ListTree className="h-3 w-3" aria-hidden />
            {task.completedSubtaskCount}/{task.subtaskCount}
          </span>
        )}

        {task.dueAt && (
          <span
            className={cn(
              'inline-flex items-center gap-1',
              task.isOverdue ? 'font-medium text-destructive' : 'text-muted-foreground',
            )}
          >
            {task.isOverdue ? (
              <AlertTriangle className="h-3 w-3" aria-hidden />
            ) : (
              <CalendarClock className="h-3 w-3" aria-hidden />
            )}
            {formatDateTime(task.dueAt)}
            {task.isOverdue && <span className="sr-only">(quá hạn)</span>}
          </span>
        )}
      </div>
    </div>
  )
}
