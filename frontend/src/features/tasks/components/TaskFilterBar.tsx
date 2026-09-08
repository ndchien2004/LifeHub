import { Search, X } from 'lucide-react'
import { forwardRef } from 'react'
import { Button } from '@/shared/components/ui/button'
import { Input, Label, Select } from '@/shared/components/ui/input'
import { cn } from '@/shared/lib/utils'
import { useProjects, useTags } from '../hooks'
import {
  PRIORITIES,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TASK_STATUSES,
  type Priority,
  type TaskQuery,
  type TaskStatus,
} from '../types'

interface TaskFilterBarProps {
  query: TaskQuery
  onChange: (query: TaskQuery) => void
  search: string
  onSearchChange: (value: string) => void
}

/**
 * Filter and search controls (FR-TSK-08, FR-TSK-09, FR-TSK-10).
 *
 * <p>Criteria combine with AND on the backend, so the visible count of active filters is the
 * user's main clue about why a list looks empty. Hence the explicit "Xóa bộ lọc" button.
 */
export const TaskFilterBar = forwardRef<HTMLInputElement, TaskFilterBarProps>(
  ({ query, onChange, search, onSearchChange }, searchRef) => {
    const { data: projects = [] } = useProjects()
    const { data: tags = [] } = useTags()

    const activeCount =
      (query.projectId ? 1 : 0) +
      (query.status?.length ? 1 : 0) +
      (query.priority?.length ? 1 : 0) +
      (query.tagIds?.length ? 1 : 0) +
      (query.dueFrom || query.dueTo ? 1 : 0) +
      (search ? 1 : 0)

    function patch(changes: Partial<TaskQuery>) {
      onChange({ ...query, ...changes, page: 0 })
    }

    return (
      <div className="space-y-3 rounded-lg border border-border bg-card p-3">
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[220px] flex-1 space-y-1.5">
            <Label htmlFor="task-search">Tìm kiếm</Label>
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground"
                aria-hidden
              />
              <Input
                id="task-search"
                ref={searchRef}
                value={search}
                placeholder="Tìm trong tiêu đề và mô tả…  ( / )"
                onChange={(event) => onSearchChange(event.target.value)}
                className="pl-8"
              />
            </div>
          </div>

          <div className="w-44 space-y-1.5">
            <Label htmlFor="filter-project">Dự án</Label>
            <Select
              id="filter-project"
              value={query.projectId ?? ''}
              onChange={(event) => patch({ projectId: event.target.value || undefined })}
            >
              <option value="">Tất cả dự án</option>
              {projects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </Select>
          </div>

          <div className="w-40 space-y-1.5">
            <Label htmlFor="filter-due-from">Đến hạn từ</Label>
            <Input
              id="filter-due-from"
              type="date"
              value={query.dueFrom?.slice(0, 10) ?? ''}
              onChange={(event) =>
                patch({
                  dueFrom: event.target.value
                    ? new Date(`${event.target.value}T00:00:00`).toISOString()
                    : undefined,
                })
              }
            />
          </div>

          <div className="w-40 space-y-1.5">
            <Label htmlFor="filter-due-to">Đến hạn đến</Label>
            <Input
              id="filter-due-to"
              type="date"
              value={query.dueTo?.slice(0, 10) ?? ''}
              onChange={(event) =>
                patch({
                  dueTo: event.target.value
                    ? new Date(`${event.target.value}T23:59:59`).toISOString()
                    : undefined,
                })
              }
            />
          </div>

          <div className="w-44 space-y-1.5">
            <Label htmlFor="filter-sort">Sắp xếp</Label>
            <Select
              id="filter-sort"
              value={query.sort ?? 'createdAt,desc'}
              onChange={(event) => patch({ sort: event.target.value })}
            >
              <option value="createdAt,desc">Mới tạo trước</option>
              <option value="dueAt,asc">Ngày đến hạn gần nhất</option>
              <option value="priority,desc">Độ ưu tiên cao trước</option>
              <option value="title,asc">Tiêu đề A→Z</option>
            </Select>
          </div>

          {activeCount > 0 && (
            <Button
              variant="ghost"
              onClick={() => {
                onSearchChange('')
                onChange({ topLevelOnly: query.topLevelOnly, sort: query.sort, page: 0 })
              }}
            >
              <X className="h-4 w-4" aria-hidden />
              Xóa bộ lọc ({activeCount})
            </Button>
          )}
        </div>

        <div className="flex flex-wrap gap-4">
          <ToggleGroup
            legend="Trạng thái"
            options={TASK_STATUSES.map((value) => ({ value, label: STATUS_LABELS[value] }))}
            selected={query.status ?? []}
            onToggle={(value) => patch({ status: toggle(query.status, value as TaskStatus) })}
          />

          <ToggleGroup
            legend="Độ ưu tiên"
            options={PRIORITIES.map((value) => ({ value, label: PRIORITY_LABELS[value] }))}
            selected={query.priority ?? []}
            onToggle={(value) => patch({ priority: toggle(query.priority, value as Priority) })}
          />

          {tags.length > 0 && (
            <ToggleGroup
              legend="Nhãn"
              options={tags.map((tag) => ({ value: tag.id, label: tag.name, color: tag.color }))}
              selected={query.tagIds ?? []}
              onToggle={(value) => patch({ tagIds: toggle(query.tagIds, value) })}
            />
          )}
        </div>
      </div>
    )
  },
)
TaskFilterBar.displayName = 'TaskFilterBar'

function ToggleGroup({
  legend,
  options,
  selected,
  onToggle,
}: {
  legend: string
  options: { value: string; label: string; color?: string }[]
  selected: string[]
  onToggle: (value: string) => void
}) {
  return (
    <fieldset className="space-y-1.5">
      <legend className="text-xs font-medium text-muted-foreground">{legend}</legend>
      <div className="flex flex-wrap gap-1.5">
        {options.map((option) => {
          const active = selected.includes(option.value)
          return (
            <button
              key={option.value}
              type="button"
              aria-pressed={active}
              onClick={() => onToggle(option.value)}
              className={cn(
                'rounded-full border px-2.5 py-0.5 text-xs transition-colors',
                active
                  ? 'border-transparent bg-primary text-primary-foreground'
                  : 'border-border text-muted-foreground hover:bg-accent',
              )}
              style={active && option.color ? { backgroundColor: option.color, color: '#fff' } : undefined}
            >
              {option.label}
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}

/** Adds or removes a value, returning undefined when the list empties so the param is dropped. */
function toggle<T extends string>(current: T[] | undefined, value: T): T[] | undefined {
  const list = current ?? []
  const next = list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
  return next.length > 0 ? next : undefined
}
