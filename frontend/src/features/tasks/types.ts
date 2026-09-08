/** Wire types for the task module (06-API-SPEC.md sections 3 and 4). */

export const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'] as const
export type TaskStatus = (typeof TASK_STATUSES)[number]

export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const
export type Priority = (typeof PRIORITIES)[number]

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'

export interface ProjectRef {
  id: string
  name: string
  color: string
}

export interface Tag {
  id: string
  name: string
  color: string
  usageCount: number
}

export interface Task {
  id: string
  title: string
  description?: string
  status: TaskStatus
  priority: Priority
  dueAt?: string
  isOverdue: boolean
  project?: ProjectRef
  tags: Tag[]
  parentId?: string
  subtaskCount: number
  completedSubtaskCount: number
  subtasks?: Task[]
  estimateMinutes?: number
  sortOrder: number
  completedAt?: string
  deletedAt?: string
  createdAt: string
  updatedAt: string
}

export interface Project {
  id: string
  name: string
  color: string
  description?: string
  status: ProjectStatus
  taskCount: number
  completedTaskCount: number
  progressPercent: number
  createdAt: string
}

export interface PagedTasks {
  items: Task[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

/** Everything the filter bar can constrain on (FR-TSK-08, FR-TSK-09, FR-TSK-10). */
export interface TaskQuery {
  projectId?: string
  tagIds?: string[]
  status?: TaskStatus[]
  priority?: Priority[]
  dueFrom?: string
  dueTo?: string
  q?: string
  topLevelOnly?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface TaskInput {
  title: string
  description?: string
  priority: Priority
  dueAt?: string | null
  projectId?: string | null
  parentId?: string | null
  tagIds?: string[]
  estimateMinutes?: number | null
}

export const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'Cần làm',
  IN_PROGRESS: 'Đang làm',
  DONE: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
}

export const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: 'Thấp',
  MEDIUM: 'Trung bình',
  HIGH: 'Cao',
  URGENT: 'Khẩn cấp',
}

/** Colour per priority, used consistently by both the list and the Kanban card. */
export const PRIORITY_CLASSES: Record<Priority, string> = {
  LOW: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  MEDIUM: 'bg-sky-100 text-sky-800 dark:bg-sky-900/50 dark:text-sky-300',
  HIGH: 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-300',
  URGENT: 'bg-red-100 text-red-800 dark:bg-red-900/50 dark:text-red-300',
}
