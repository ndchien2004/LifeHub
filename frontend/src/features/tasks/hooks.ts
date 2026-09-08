import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/components/ui/toast'
import { ApiRequestError } from '@/shared/lib/apiClient'
import * as api from './api'
import { projectKeys, tagKeys, taskKeys } from './api'
import type { Project, ProjectStatus, Tag, Task, TaskInput, TaskQuery, TaskStatus } from './types'

/**
 * Data hooks for the task module.
 *
 * <p>Every mutation invalidates the task list plus whatever else its result can change - project
 * progress and tag usage counts are both derived from tasks, so they go stale together.
 */

function invalidateEverything(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: taskKeys.all })
  void queryClient.invalidateQueries({ queryKey: projectKeys.all })
  void queryClient.invalidateQueries({ queryKey: tagKeys.all })
}

/** Turns a backend error into the Vietnamese message the user should see (NFR-USE-03). */
function reportError(error: unknown, fallback: string) {
  toast.error(error instanceof ApiRequestError ? error.message : fallback)
}

export function useTasks(query: TaskQuery) {
  return useQuery({
    queryKey: taskKeys.list(query),
    queryFn: () => api.fetchTasks(query),
  })
}

export function useTask(id: string | null) {
  return useQuery({
    queryKey: taskKeys.detail(id ?? ''),
    queryFn: () => api.fetchTask(id as string),
    enabled: Boolean(id),
  })
}

export function useProjects() {
  return useQuery({ queryKey: projectKeys.all, queryFn: api.fetchProjects })
}

export function useTags() {
  return useQuery({ queryKey: tagKeys.all, queryFn: api.fetchTags })
}

export function useCreateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: TaskInput) => api.createTask(input),
    onSuccess: () => {
      invalidateEverything(queryClient)
      toast.success('Đã tạo task')
    },
    onError: (error) => reportError(error, 'Không tạo được task'),
  })
}

export function useUpdateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: Partial<TaskInput> }) =>
      api.updateTask(id, input),
    onSuccess: () => {
      invalidateEverything(queryClient)
      toast.success('Đã lưu thay đổi')
    },
    onError: (error) => reportError(error, 'Không lưu được thay đổi'),
  })
}

/**
 * Status change with an optimistic update.
 *
 * <p>A Kanban card has to follow the pointer immediately; waiting for the round trip would make
 * every drag feel broken. The cache is rolled back if the request fails.
 */
export function useChangeTaskStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: TaskStatus }) =>
      api.changeTaskStatus(id, status),
    onMutate: async ({ id, status }) => {
      await queryClient.cancelQueries({ queryKey: taskKeys.all })
      const snapshot = queryClient.getQueriesData({ queryKey: taskKeys.all })

      queryClient.setQueriesData<{ items: Task[] }>({ queryKey: taskKeys.all }, (old) => {
        if (!old?.items) {
          return old
        }
        return {
          ...old,
          items: old.items.map((task) => (task.id === id ? { ...task, status } : task)),
        }
      })

      return { snapshot }
    },
    onError: (error, _variables, context) => {
      context?.snapshot.forEach(([key, value]) => queryClient.setQueryData(key, value))
      reportError(error, 'Không đổi được trạng thái')
    },
    onSettled: () => invalidateEverything(queryClient),
  })
}

/**
 * Soft delete with a five second undo (NFR-USE-04, FR-TSK-03).
 *
 * <p>The undo toast is not decoration: the backend keeps the row, so the button genuinely brings
 * the task back rather than recreating a copy with a new id.
 */
export function useDeleteTask() {
  const queryClient = useQueryClient()
  const restore = useRestoreTask()

  return useMutation({
    mutationFn: (task: Task) => api.deleteTask(task.id).then(() => task),
    onSuccess: (task) => {
      invalidateEverything(queryClient)
      toast.undoable(`Đã xóa "${task.title}".`, () => restore.mutate(task.id))
    },
    onError: (error) => reportError(error, 'Không xóa được task'),
  })
}

export function useRestoreTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.restoreTask(id),
    onSuccess: () => {
      invalidateEverything(queryClient)
      toast.success('Đã khôi phục task')
    },
    onError: (error) => reportError(error, 'Không khôi phục được task'),
  })
}

export function useCreateProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { name: string; color?: string; description?: string }) =>
      api.createProject(input),
    onSuccess: () => {
      invalidateEverything(queryClient)
      toast.success('Đã tạo dự án')
    },
    onError: (error) => reportError(error, 'Không tạo được dự án'),
  })
}

export function useUpdateProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      input,
    }: {
      id: string
      input: { name?: string; color?: string; description?: string; status?: ProjectStatus }
    }) => api.updateProject(id, input),
    onSuccess: () => {
      invalidateEverything(queryClient)
      toast.success('Đã lưu dự án')
    },
    onError: (error) => reportError(error, 'Không lưu được dự án'),
  })
}

export function useDeleteProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (project: Project) => api.deleteProject(project.id),
    onSuccess: (_result, project) => {
      invalidateEverything(queryClient)
      toast.success(`Đã xóa dự án "${project.name}". Các task bên trong vẫn còn.`)
    },
    onError: (error) => reportError(error, 'Không xóa được dự án'),
  })
}

export function useCreateTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { name: string; color?: string }) => api.createTag(input),
    onSuccess: () => {
      invalidateEverything(queryClient)
      toast.success('Đã tạo nhãn')
    },
    onError: (error) => reportError(error, 'Không tạo được nhãn'),
  })
}

export function useUpdateTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: { name?: string; color?: string } }) =>
      api.updateTag(id, input),
    onSuccess: () => {
      invalidateEverything(queryClient)
      toast.success('Đã lưu nhãn')
    },
    onError: (error) => reportError(error, 'Không lưu được nhãn'),
  })
}

export function useDeleteTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tag: Tag) => api.deleteTag(tag.id),
    onSuccess: (_result, tag) => {
      invalidateEverything(queryClient)
      toast.success(`Đã xóa nhãn "${tag.name}"`)
    },
    onError: (error) => reportError(error, 'Không xóa được nhãn'),
  })
}
