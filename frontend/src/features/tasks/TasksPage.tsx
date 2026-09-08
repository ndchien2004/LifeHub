import { KanbanSquare, List, Loader2, Plus } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@/shared/components/ui/button'
import { useDebounce } from '@/shared/hooks/useDebounce'
import { cn } from '@/shared/lib/utils'
import { TaskFilterBar } from './components/TaskFilterBar'
import { TaskFormDialog } from './components/TaskFormDialog'
import { TaskKanbanView } from './components/TaskKanbanView'
import { TaskListView } from './components/TaskListView'
import { useDeleteTask, useTasks } from './hooks'
import type { Task, TaskQuery } from './types'

type ViewMode = 'list' | 'kanban'

/**
 * Task workspace (FR-TSK-07 to FR-TSK-10).
 *
 * <p>List and Kanban read the same query, so switching view never changes which tasks are in
 * scope - only how they are arranged. Kanban asks for top level tasks only, because a subtask
 * sitting in a column beside its parent reads as a duplicate.
 */
export function TasksPage() {
  const [view, setView] = useState<ViewMode>('list')
  const [query, setQuery] = useState<TaskQuery>({ sort: 'createdAt,desc' })
  const [search, setSearch] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Task | null>(null)
  const [parentId, setParentId] = useState<string | null>(null)

  const searchRef = useRef<HTMLInputElement>(null)
  const debouncedSearch = useDebounce(search, 300)
  const deleteTask = useDeleteTask()

  const effectiveQuery = useMemo<TaskQuery>(
    () => ({ ...query, q: debouncedSearch || undefined, topLevelOnly: view === 'kanban' }),
    [query, debouncedSearch, view],
  )

  const { data, isPending, isFetching } = useTasks(effectiveQuery)
  const tasks = data?.items ?? []

  useKeyboardShortcuts({
    onCreate: () => openCreate(null),
    onFocusSearch: () => searchRef.current?.focus(),
  })

  function openCreate(parent: string | null) {
    setEditing(null)
    setParentId(parent)
    setFormOpen(true)
  }

  function openEdit(task: Task) {
    setEditing(task)
    setParentId(null)
    setFormOpen(true)
  }

  return (
    <div className="flex h-full flex-col gap-4 p-6">
      <header className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Công việc</h1>
          <p className="text-sm text-muted-foreground">
            {isPending ? 'Đang tải…' : `${data?.totalItems ?? 0} task khớp bộ lọc`}
            {isFetching && !isPending && ' · đang cập nhật'}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex rounded-md border border-border p-0.5" role="group" aria-label="Chế độ hiển thị">
            <ViewButton active={view === 'list'} onClick={() => setView('list')} label="Danh sách">
              <List className="h-4 w-4" aria-hidden />
            </ViewButton>
            <ViewButton active={view === 'kanban'} onClick={() => setView('kanban')} label="Kanban">
              <KanbanSquare className="h-4 w-4" aria-hidden />
            </ViewButton>
          </div>

          <Button onClick={() => openCreate(null)}>
            <Plus className="h-4 w-4" aria-hidden />
            Thêm task
            <kbd className="ml-1 rounded border border-primary-foreground/30 px-1 text-[10px]">N</kbd>
          </Button>
        </div>
      </header>

      <TaskFilterBar ref={searchRef} query={query} onChange={setQuery} search={search} onSearchChange={setSearch} />

      <div className="min-h-0 flex-1 overflow-y-auto pb-4">
        {isPending ? (
          <div className="flex items-center justify-center gap-2 p-10 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            Đang tải danh sách task…
          </div>
        ) : view === 'list' ? (
          <TaskListView
            tasks={tasks}
            onEdit={openEdit}
            onDelete={(task) => deleteTask.mutate(task)}
            onAddSubtask={(task) => openCreate(task.id)}
          />
        ) : (
          <TaskKanbanView tasks={tasks} onEdit={openEdit} onDelete={(task) => deleteTask.mutate(task)} />
        )}
      </div>

      <TaskFormDialog open={formOpen} onOpenChange={setFormOpen} task={editing} parentId={parentId} />
    </div>
  )
}

function ViewButton({
  active,
  onClick,
  label,
  children,
}: {
  active: boolean
  onClick: () => void
  label: string
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      title={label}
      className={cn(
        'inline-flex h-7 items-center gap-1.5 rounded px-2.5 text-xs font-medium transition-colors',
        active ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent',
      )}
    >
      {children}
      {label}
    </button>
  )
}

/**
 * Global shortcuts for this screen: N creates, / focuses search.
 *
 * <p>Both are suppressed while the user is typing in a field or a dialog is open, otherwise
 * typing the letter n into the title box would open a second form.
 */
function useKeyboardShortcuts({
  onCreate,
  onFocusSearch,
}: {
  onCreate: () => void
  onFocusSearch: () => void
}) {
  useEffect(() => {
    function handler(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const typing =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.tagName === 'SELECT' ||
        target?.isContentEditable
      if (typing || event.ctrlKey || event.metaKey || event.altKey) {
        return
      }
      if (document.querySelector('[role="dialog"]')) {
        return
      }

      if (event.key === 'n' || event.key === 'N') {
        event.preventDefault()
        onCreate()
      } else if (event.key === '/') {
        event.preventDefault()
        onFocusSearch()
      }
    }

    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onCreate, onFocusSearch])
}
