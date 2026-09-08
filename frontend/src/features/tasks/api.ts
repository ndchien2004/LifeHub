import { apiFetch } from '@/shared/lib/apiClient'
import type {
  PagedTasks,
  Project,
  ProjectStatus,
  Tag,
  Task,
  TaskInput,
  TaskQuery,
  TaskStatus,
} from './types'

/** REST calls for the task module. Query keys live next to them so invalidation stays honest. */

export const taskKeys = {
  all: ['tasks'] as const,
  list: (query: TaskQuery) => ['tasks', 'list', query] as const,
  detail: (id: string) => ['tasks', 'detail', id] as const,
}

export const projectKeys = { all: ['projects'] as const }
export const tagKeys = { all: ['tags'] as const }

/**
 * Builds the query string for GET /tasks.
 *
 * <p>URLSearchParams percent-encodes values, which matters for the ISO timestamps: a raw
 * {@code +07:00} offset in a query string decodes to a space and the backend would reject it.
 */
function buildQuery(query: TaskQuery): string {
  const params = new URLSearchParams()

  if (query.projectId) params.set('projectId', query.projectId)
  if (query.tagIds?.length) params.set('tagIds', query.tagIds.join(','))
  if (query.status?.length) params.set('status', query.status.join(','))
  if (query.priority?.length) params.set('priority', query.priority.join(','))
  if (query.dueFrom) params.set('dueFrom', query.dueFrom)
  if (query.dueTo) params.set('dueTo', query.dueTo)
  if (query.q?.trim()) params.set('q', query.q.trim())
  if (query.topLevelOnly) params.set('topLevelOnly', 'true')
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 100))
  if (query.sort) params.set('sort', query.sort)

  return params.toString()
}

export function fetchTasks(query: TaskQuery): Promise<PagedTasks> {
  return apiFetch<PagedTasks>(`/tasks?${buildQuery(query)}`)
}

export function fetchTask(id: string): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}`)
}

export function createTask(input: TaskInput): Promise<Task> {
  return apiFetch<Task>('/tasks', { method: 'POST', body: JSON.stringify(input) })
}

export function updateTask(id: string, input: Partial<TaskInput>): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

/** Dedicated endpoint so a Kanban drag sends one small request rather than a whole task. */
export function changeTaskStatus(id: string, status: TaskStatus): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function deleteTask(id: string): Promise<void> {
  return apiFetch<void>(`/tasks/${id}`, { method: 'DELETE' })
}

export function restoreTask(id: string): Promise<Task> {
  return apiFetch<Task>(`/tasks/${id}/restore`, { method: 'POST' })
}

export function fetchProjects(): Promise<Project[]> {
  return apiFetch<Project[]>('/projects')
}

export function createProject(input: {
  name: string
  color?: string
  description?: string
}): Promise<Project> {
  return apiFetch<Project>('/projects', { method: 'POST', body: JSON.stringify(input) })
}

export function updateProject(
  id: string,
  input: { name?: string; color?: string; description?: string; status?: ProjectStatus },
): Promise<Project> {
  return apiFetch<Project>(`/projects/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function deleteProject(id: string): Promise<void> {
  return apiFetch<void>(`/projects/${id}`, { method: 'DELETE' })
}

export function fetchTags(): Promise<Tag[]> {
  return apiFetch<Tag[]>('/tags')
}

export function createTag(input: { name: string; color?: string }): Promise<Tag> {
  return apiFetch<Tag>('/tags', { method: 'POST', body: JSON.stringify(input) })
}

export function updateTag(id: string, input: { name?: string; color?: string }): Promise<Tag> {
  return apiFetch<Tag>(`/tags/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function deleteTag(id: string): Promise<void> {
  return apiFetch<void>(`/tags/${id}`, { method: 'DELETE' })
}
