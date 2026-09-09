import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
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
import { cn } from '@/shared/lib/utils'
import { AiFieldHint, aiFieldClass } from '@/features/ai/components/AiFieldHint'
import type { FieldConfidence } from '@/features/ai/types'
import { useCreateTask, useProjects, useTags, useUpdateTask } from '../hooks'
import { PRIORITIES, PRIORITY_LABELS, type Task, type TaskInput } from '../types'

/**
 * Create and edit form for a task (FR-TSK-01, FR-TSK-02).
 *
 * <p>Validated with Zod through React Hook Form, so an empty title is caught before any request
 * leaves the renderer (T1-13, UC-01 exception E1). A past due date only warns, matching UC-01
 * exception E2 - it is a plausible thing to want, so it must not be blocked.
 */

const taskSchema = z.object({
  title: z.string().trim().min(1, 'Tiêu đề không được để trống').max(255, 'Tiêu đề tối đa 255 ký tự'),
  description: z.string().optional(),
  priority: z.enum(PRIORITIES),
  dueAt: z.string().optional(),
  projectId: z.string().optional(),
  estimateMinutes: z
    .string()
    .optional()
    .refine((value) => !value || Number(value) > 0, 'Ước lượng thời gian phải lớn hơn 0'),
  tagIds: z.array(z.string()).optional(),
})

type TaskFormValues = z.infer<typeof taskSchema>

interface TaskFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present when editing; absent when creating. */
  task?: Task | null
  /** Set when creating a subtask of an existing task (FR-TSK-11). */
  parentId?: string | null
  /** Prefilled values for a new task, e.g. from the AI command palette (FR-AI-06). */
  defaults?: Partial<TaskFormValues>
  /** Per-field AI certainty, which draws the badges beside the labels (UC-09 step 10). */
  aiConfidence?: FieldConfidence
}

export function TaskFormDialog({
  open,
  onOpenChange,
  task,
  parentId,
  defaults,
  aiConfidence,
}: TaskFormDialogProps) {
  const isEditing = Boolean(task)
  const { data: projects = [] } = useProjects()
  const { data: tags = [] } = useTags()
  const createTask = useCreateTask()
  const updateTask = useUpdateTask()

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<TaskFormValues>({
    resolver: zodResolver(taskSchema),
    defaultValues: emptyValues(),
  })

  useEffect(() => {
    if (open) {
      reset(task ? valuesFrom(task) : { ...emptyValues(), ...defaults })
    }
    // `defaults` is a fresh object on every render of the caller, so it is deliberately not a
    // dependency: including it would reset the form under the user mid-edit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, task, reset])

  const selectedTagIds = watch('tagIds') ?? []
  const dueAt = watch('dueAt')
  const isDueInThePast = Boolean(dueAt) && new Date(dueAt as string) < new Date()

  const onSubmit = handleSubmit(async (values) => {
    const input: TaskInput = {
      title: values.title.trim(),
      description: values.description?.trim() || undefined,
      priority: values.priority,
      dueAt: values.dueAt ? new Date(values.dueAt).toISOString() : null,
      projectId: values.projectId || null,
      tagIds: values.tagIds ?? [],
      estimateMinutes: values.estimateMinutes ? Number(values.estimateMinutes) : null,
    }

    if (isEditing && task) {
      await updateTask.mutateAsync({ id: task.id, input })
    } else {
      await createTask.mutateAsync({ ...input, parentId: parentId ?? null })
    }
    onOpenChange(false)
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-xl"
        // Ctrl+Enter saves from anywhere in the form; Esc is handled by Radix.
        onKeyDown={(event) => {
          if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault()
            void onSubmit()
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>
            {isEditing ? 'Sửa task' : parentId ? 'Thêm subtask' : 'Thêm task'}
          </DialogTitle>
          <DialogDescription>
            Lưu bằng <kbd className="rounded border border-border px-1 text-xs">Ctrl</kbd> +{' '}
            <kbd className="rounded border border-border px-1 text-xs">Enter</kbd>, đóng bằng{' '}
            <kbd className="rounded border border-border px-1 text-xs">Esc</kbd>.
          </DialogDescription>
        </DialogHeader>

        {/*
          noValidate: the browser's own constraint validation would otherwise block submit before
          Zod runs, replacing our inline Vietnamese messages with a native tooltip in the browser
          UI language (NFR-USE-02, NFR-USE-03). Attributes like min stay as spinner affordances.
        */}
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="title" className="flex items-center gap-2">
              <span>
                Tiêu đề <span className="text-destructive">*</span>
              </span>
              <AiFieldHint confidence={aiConfidence?.title} />
            </Label>
            <Input
              id="title"
              autoFocus
              aria-invalid={Boolean(errors.title)}
              placeholder="Việc cần làm là gì?"
              className={aiFieldClass(aiConfidence?.title)}
              {...register('title')}
            />
            <FieldError>{errors.title?.message}</FieldError>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="description">Mô tả</Label>
            <Textarea id="description" placeholder="Chi tiết thêm (không bắt buộc)" {...register('description')} />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="priority" className="flex items-center gap-2">
                Độ ưu tiên
                <AiFieldHint confidence={aiConfidence?.priority} />
              </Label>
              <Select
                id="priority"
                className={aiFieldClass(aiConfidence?.priority)}
                {...register('priority')}
              >
                {PRIORITIES.map((value) => (
                  <option key={value} value={value}>
                    {PRIORITY_LABELS[value]}
                  </option>
                ))}
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="dueAt" className="flex items-center gap-2">
                Ngày đến hạn
                <AiFieldHint confidence={aiConfidence?.dueAt} />
              </Label>
              <Input
                id="dueAt"
                type="datetime-local"
                className={aiFieldClass(aiConfidence?.dueAt)}
                {...register('dueAt')}
              />
              {isDueInThePast && (
                <p className="text-xs text-amber-600 dark:text-amber-400">
                  Ngày đến hạn đã qua, bạn có chắc không?
                </p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="projectId">Dự án</Label>
              <Select id="projectId" {...register('projectId')}>
                <option value="">Không thuộc dự án nào</option>
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="estimateMinutes">Ước lượng (phút)</Label>
              <Input
                id="estimateMinutes"
                type="number"
                min={1}
                placeholder="180"
                aria-invalid={Boolean(errors.estimateMinutes)}
                {...register('estimateMinutes')}
              />
              <FieldError>{errors.estimateMinutes?.message}</FieldError>
            </div>
          </div>

          {tags.length > 0 && (
            <div className="space-y-1.5">
              <Label>Nhãn</Label>
              <div className="flex flex-wrap gap-1.5">
                {tags.map((tag) => {
                  const selected = selectedTagIds.includes(tag.id)
                  return (
                    <button
                      key={tag.id}
                      type="button"
                      aria-pressed={selected}
                      onClick={() =>
                        setValue(
                          'tagIds',
                          selected
                            ? selectedTagIds.filter((id) => id !== tag.id)
                            : [...selectedTagIds, tag.id],
                          { shouldDirty: true },
                        )
                      }
                      className={cn(
                        'rounded-full border px-2.5 py-0.5 text-xs transition-colors',
                        selected
                          ? 'border-transparent text-white'
                          : 'border-border text-muted-foreground hover:bg-accent',
                      )}
                      style={selected ? { backgroundColor: tag.color } : undefined}
                    >
                      {tag.name}
                    </button>
                  )
                })}
              </div>
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Hủy
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Đang lưu…' : 'Lưu'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function emptyValues(): TaskFormValues {
  return {
    title: '',
    description: '',
    priority: 'MEDIUM',
    dueAt: '',
    projectId: '',
    estimateMinutes: '',
    tagIds: [],
  }
}

function valuesFrom(task: Task): TaskFormValues {
  return {
    title: task.title,
    description: task.description ?? '',
    priority: task.priority,
    // datetime-local wants "YYYY-MM-DDTHH:mm" with no zone; the API sends a full offset timestamp.
    dueAt: task.dueAt ? task.dueAt.slice(0, 16) : '',
    projectId: task.project?.id ?? '',
    estimateMinutes: task.estimateMinutes ? String(task.estimateMinutes) : '',
    tagIds: task.tags.map((tag) => tag.id),
  }
}
