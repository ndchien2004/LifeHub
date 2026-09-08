import { ChevronDown, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { Select } from '@/shared/components/ui/input'
import { TaskCard } from './TaskCard'
import { useChangeTaskStatus } from '../hooks'
import { STATUS_LABELS, TASK_STATUSES, type Task, type TaskStatus } from '../types'

interface TaskListViewProps {
  tasks: Task[]
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
  onAddSubtask: (task: Task) => void
}

/**
 * Flat list view (FR-TSK-07).
 *
 * <p>Subtasks are nested under their parent rather than listed alongside it, so the one level of
 * hierarchy FR-TSK-11 allows is actually visible. Each row carries a status dropdown, which is how
 * the status gets changed without a drag when the pointer is not the fastest route.
 */
export function TaskListView({ tasks, onEdit, onDelete, onAddSubtask }: TaskListViewProps) {
  const topLevel = tasks.filter((task) => !task.parentId)
  const subtasksByParent = new Map<string, Task[]>()

  for (const task of tasks) {
    if (task.parentId) {
      subtasksByParent.set(task.parentId, [...(subtasksByParent.get(task.parentId) ?? []), task])
    }
  }

  if (topLevel.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        Không có task nào khớp bộ lọc hiện tại.
      </p>
    )
  }

  return (
    <ul className="flex flex-col gap-2">
      {topLevel.map((task) => (
        <TaskRow
          key={task.id}
          task={task}
          subtasks={subtasksByParent.get(task.id) ?? []}
          onEdit={onEdit}
          onDelete={onDelete}
          onAddSubtask={onAddSubtask}
        />
      ))}
    </ul>
  )
}

function TaskRow({
  task,
  subtasks,
  onEdit,
  onDelete,
  onAddSubtask,
}: {
  task: Task
  subtasks: Task[]
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
  onAddSubtask: (task: Task) => void
}) {
  const [expanded, setExpanded] = useState(true)
  const changeStatus = useChangeTaskStatus()

  return (
    <li>
      <div className="flex items-start gap-2">
        {subtasks.length > 0 ? (
          <button
            type="button"
            onClick={() => setExpanded((value) => !value)}
            aria-expanded={expanded}
            aria-label={expanded ? 'Thu gọn subtask' : 'Mở rộng subtask'}
            className="mt-3 rounded p-0.5 text-muted-foreground hover:bg-accent"
          >
            {expanded ? (
              <ChevronDown className="h-4 w-4" aria-hidden />
            ) : (
              <ChevronRight className="h-4 w-4" aria-hidden />
            )}
          </button>
        ) : (
          <span className="mt-3 w-5" aria-hidden />
        )}

        <div className="flex-1">
          <TaskCard task={task} onEdit={onEdit} onDelete={onDelete} onAddSubtask={onAddSubtask} />
        </div>

        <Select
          className="mt-1 w-36 shrink-0"
          aria-label={`Trạng thái của ${task.title}`}
          value={task.status}
          onChange={(event) =>
            changeStatus.mutate({ id: task.id, status: event.target.value as TaskStatus })
          }
        >
          {TASK_STATUSES.map((status) => (
            <option key={status} value={status}>
              {STATUS_LABELS[status]}
            </option>
          ))}
        </Select>
      </div>

      {expanded && subtasks.length > 0 && (
        <ul className="ml-8 mt-2 flex flex-col gap-2 border-l border-border pl-4">
          {subtasks.map((subtask) => (
            <li key={subtask.id} className="flex items-start gap-2">
              <div className="flex-1">
                <TaskCard task={subtask} onEdit={onEdit} onDelete={onDelete} />
              </div>
              <Select
                className="mt-1 w-36 shrink-0"
                aria-label={`Trạng thái của ${subtask.title}`}
                value={subtask.status}
                onChange={(event) =>
                  changeStatus.mutate({ id: subtask.id, status: event.target.value as TaskStatus })
                }
              >
                {TASK_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {STATUS_LABELS[status]}
                  </option>
                ))}
              </Select>
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
