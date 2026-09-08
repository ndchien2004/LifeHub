import { Archive, ArchiveRestore, Loader2, Plus, Tag as TagIcon, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/shared/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card'
import { FieldError, Input, Label } from '@/shared/components/ui/input'
import { cn } from '@/shared/lib/utils'
import {
  useCreateProject,
  useCreateTag,
  useDeleteProject,
  useDeleteTag,
  useProjects,
  useTags,
  useUpdateProject,
  useUpdateTag,
} from './hooks'
import type { Project, Tag } from './types'

/** Palette offered by the colour picker. Distinct hues that stay legible in both themes. */
const PALETTE = [
  '#6366f1', '#0ea5e9', '#10b981', '#f59e0b',
  '#ef4444', '#ec4899', '#8b5cf6', '#64748b',
]

/** Projects and tags management (FR-PRJ-01 to FR-PRJ-04). */
export function ProjectsPage() {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Dự án & Nhãn</h1>
        <p className="text-sm text-muted-foreground">
          Nhóm task theo dự án và gắn nhãn để lọc nhanh hơn.
        </p>
      </header>

      <ProjectSection />
      <TagSection />
    </div>
  )
}

function ProjectSection() {
  const { data: projects = [], isPending } = useProjects()
  const createProject = useCreateProject()
  const updateProject = useUpdateProject()
  const deleteProject = useDeleteProject()

  const [name, setName] = useState('')
  const [color, setColor] = useState(PALETTE[0] as string)
  const [error, setError] = useState<string | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!name.trim()) {
      setError('Tên dự án không được để trống')
      return
    }
    setError(null)
    createProject.mutate({ name: name.trim(), color }, { onSuccess: () => setName('') })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Dự án</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
          <div className="min-w-[240px] flex-1 space-y-1.5">
            <Label htmlFor="project-name">Tên dự án mới</Label>
            <Input
              id="project-name"
              value={name}
              aria-invalid={Boolean(error)}
              placeholder="Ví dụ: LifeHub"
              onChange={(event) => setName(event.target.value)}
            />
            <FieldError>{error ?? undefined}</FieldError>
          </div>

          <ColorPicker label="Màu dự án" value={color} onChange={setColor} />

          <Button type="submit" disabled={createProject.isPending}>
            <Plus className="h-4 w-4" aria-hidden />
            Thêm dự án
          </Button>
        </form>

        {isPending ? (
          <Loading />
        ) : projects.length === 0 ? (
          <Empty>Chưa có dự án nào.</Empty>
        ) : (
          <ul className="divide-y divide-border rounded-md border border-border">
            {projects.map((project) => (
              <ProjectRow
                key={project.id}
                project={project}
                onArchiveToggle={() =>
                  updateProject.mutate({
                    id: project.id,
                    input: { status: project.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE' },
                  })
                }
                onDelete={() => {
                  const message =
                    project.taskCount > 0
                      ? `Xóa dự án "${project.name}"? ${project.taskCount} task bên trong sẽ được giữ lại và chuyển về "không dự án".`
                      : `Xóa dự án "${project.name}"?`
                  if (window.confirm(message)) {
                    deleteProject.mutate(project)
                  }
                }}
              />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function ProjectRow({
  project,
  onArchiveToggle,
  onDelete,
}: {
  project: Project
  onArchiveToggle: () => void
  onDelete: () => void
}) {
  const archived = project.status === 'ARCHIVED'

  return (
    <li className={cn('flex items-center gap-4 p-3', archived && 'opacity-60')}>
      <span
        className="h-3 w-3 shrink-0 rounded-full"
        style={{ backgroundColor: project.color }}
        aria-hidden
      />

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate text-sm font-medium">{project.name}</span>
          {archived && (
            <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
              Đã lưu trữ
            </span>
          )}
        </div>

        {/* FR-PRJ-03: progress is completed over total, computed server side. */}
        <div className="mt-1.5 flex items-center gap-2">
          <div
            className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted"
            role="progressbar"
            aria-valuenow={project.progressPercent}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`Tiến độ ${project.name}`}
          >
            <div
              className="h-full rounded-full transition-all"
              style={{ width: `${project.progressPercent}%`, backgroundColor: project.color }}
            />
          </div>
          <span className="w-28 shrink-0 text-right text-xs tabular-nums text-muted-foreground">
            {project.completedTaskCount}/{project.taskCount} · {project.progressPercent}%
          </span>
        </div>
      </div>

      <Button
        size="icon"
        variant="ghost"
        onClick={onArchiveToggle}
        aria-label={archived ? `Bỏ lưu trữ ${project.name}` : `Lưu trữ ${project.name}`}
        title={archived ? 'Bỏ lưu trữ' : 'Lưu trữ'}
      >
        {archived ? (
          <ArchiveRestore className="h-4 w-4" aria-hidden />
        ) : (
          <Archive className="h-4 w-4" aria-hidden />
        )}
      </Button>

      <Button
        size="icon"
        variant="ghost"
        onClick={onDelete}
        aria-label={`Xóa dự án ${project.name}`}
        className="text-destructive hover:text-destructive"
      >
        <Trash2 className="h-4 w-4" aria-hidden />
      </Button>
    </li>
  )
}

function TagSection() {
  const { data: tags = [], isPending } = useTags()
  const createTag = useCreateTag()
  const updateTag = useUpdateTag()
  const deleteTag = useDeleteTag()

  const [name, setName] = useState('')
  const [color, setColor] = useState(PALETTE[2] as string)
  const [error, setError] = useState<string | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!name.trim()) {
      setError('Tên nhãn không được để trống')
      return
    }
    setError(null)
    createTag.mutate({ name: name.trim(), color }, { onSuccess: () => setName('') })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <TagIcon className="h-4 w-4" aria-hidden />
          Nhãn
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
          <div className="min-w-[240px] flex-1 space-y-1.5">
            <Label htmlFor="tag-name">Tên nhãn mới</Label>
            <Input
              id="tag-name"
              value={name}
              aria-invalid={Boolean(error)}
              placeholder="Ví dụ: gấp"
              onChange={(event) => setName(event.target.value)}
            />
            <FieldError>{error ?? undefined}</FieldError>
          </div>

          <ColorPicker label="Màu nhãn" value={color} onChange={setColor} />

          <Button type="submit" disabled={createTag.isPending}>
            <Plus className="h-4 w-4" aria-hidden />
            Thêm nhãn
          </Button>
        </form>

        {isPending ? (
          <Loading />
        ) : tags.length === 0 ? (
          <Empty>Chưa có nhãn nào.</Empty>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <TagChip
                key={tag.id}
                tag={tag}
                onRecolor={(next) => updateTag.mutate({ id: tag.id, input: { color: next } })}
                onDelete={() => {
                  const message =
                    tag.usageCount > 0
                      ? `Xóa nhãn "${tag.name}"? Nhãn sẽ bị gỡ khỏi ${tag.usageCount} task, các task vẫn còn.`
                      : `Xóa nhãn "${tag.name}"?`
                  if (window.confirm(message)) {
                    deleteTag.mutate(tag)
                  }
                }}
              />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function TagChip({
  tag,
  onRecolor,
  onDelete,
}: {
  tag: Tag
  onRecolor: (color: string) => void
  onDelete: () => void
}) {
  return (
    <li className="flex items-center gap-2 rounded-full border border-border py-1 pl-1 pr-2">
      <input
        type="color"
        value={tag.color}
        onChange={(event) => onRecolor(event.target.value)}
        aria-label={`Đổi màu nhãn ${tag.name}`}
        className="h-6 w-6 cursor-pointer rounded-full border-0 bg-transparent p-0"
      />
      <span className="text-sm">{tag.name}</span>
      <span className="text-xs tabular-nums text-muted-foreground">{tag.usageCount}</span>
      <button
        type="button"
        onClick={onDelete}
        aria-label={`Xóa nhãn ${tag.name}`}
        className="rounded-full p-0.5 text-muted-foreground transition-colors hover:bg-destructive hover:text-destructive-foreground"
      >
        <Trash2 className="h-3.5 w-3.5" aria-hidden />
      </button>
    </li>
  )
}

function ColorPicker({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (color: string) => void
}) {
  return (
    <fieldset className="space-y-1.5">
      <legend className="text-sm font-medium">{label}</legend>
      <div className="flex gap-1">
        {PALETTE.map((option) => (
          <button
            key={option}
            type="button"
            aria-label={`Chọn màu ${option}`}
            aria-pressed={value === option}
            onClick={() => onChange(option)}
            className={cn(
              'h-7 w-7 rounded-full border-2 transition-transform',
              value === option ? 'border-foreground scale-110' : 'border-transparent',
            )}
            style={{ backgroundColor: option }}
          />
        ))}
      </div>
    </fieldset>
  )
}

function Loading() {
  return (
    <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      Đang tải…
    </div>
  )
}

function Empty({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
      {children}
    </p>
  )
}
