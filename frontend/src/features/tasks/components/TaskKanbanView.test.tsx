import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useChangeTaskStatus } from '../hooks'
import type { Task } from '../types'
import { resolveStatusChange } from './TaskKanbanView'

/**
 * T1-14 — dropping a Kanban card must hit the dedicated status endpoint.
 *
 * <p>Covered in two halves rather than by driving a pointer gesture through jsdom, which cannot
 * produce the layout measurements dnd-kit needs and would prove nothing about the request anyway.
 * The first half pins the rule that decides whether a drop changes anything; the second pins the
 * exact request that decision produces.
 */

function task(id: string, status: Task['status']): Task {
  return {
    id,
    title: `Task ${id}`,
    status,
    priority: 'MEDIUM',
    isOverdue: false,
    tags: [],
    subtaskCount: 0,
    completedSubtaskCount: 0,
    sortOrder: 0,
    createdAt: '2026-09-08T10:00:00+07:00',
    updatedAt: '2026-09-08T10:00:00+07:00',
  }
}

describe('resolveStatusChange — quyết định khi thả card', () => {
  const tasks = [task('a', 'TODO'), task('b', 'DONE')]

  it('T1-14 — thả sang cột khác thì đổi trạng thái theo cột đích', () => {
    expect(resolveStatusChange(tasks, 'a', 'IN_PROGRESS')).toEqual({
      id: 'a',
      status: 'IN_PROGRESS',
    })
  })

  it('Thả lại đúng cột cũ thì không làm gì', () => {
    expect(resolveStatusChange(tasks, 'a', 'TODO')).toBeNull()
  })

  it('Thả ra ngoài mọi cột thì không làm gì', () => {
    expect(resolveStatusChange(tasks, 'a', undefined)).toBeNull()
    expect(resolveStatusChange(tasks, 'a', null)).toBeNull()
  })

  it('Cột đích không phải trạng thái hợp lệ thì không làm gì', () => {
    expect(resolveStatusChange(tasks, 'a', 'KHONG_PHAI_TRANG_THAI')).toBeNull()
  })

  it('Task không có trong danh sách thì không làm gì', () => {
    expect(resolveStatusChange(tasks, 'khong-ton-tai', 'DONE')).toBeNull()
  })
})

describe('useChangeTaskStatus — request thực tế gửi đi', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, data: task('a', 'IN_PROGRESS') }),
    })
    vi.stubGlobal('fetch', fetchMock)
    window.lifehub = {
      getBackendInfo: vi.fn().mockResolvedValue({ port: 51234, token: 'test-token' }),
      onBackendRestarted: vi.fn().mockReturnValue(() => {}),
    }
  })

  function wrapper({ children }: { children: ReactNode }) {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }

  it('T1-14 — gọi PATCH /tasks/{id}/status với đúng trạng thái mới', async () => {
    const { result } = renderHook(() => useChangeTaskStatus(), { wrapper })

    result.current.mutate({ id: 'a', status: 'IN_PROGRESS' })

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())

    const [url, init] = fetchMock.mock.calls[0]!
    expect(url).toBe('http://127.0.0.1:51234/api/v1/tasks/a/status')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ status: 'IN_PROGRESS' })
    expect(init.headers['X-App-Token']).toBe('test-token')
  })

  it('Chỉ gửi trạng thái, không gửi kèm cả task — endpoint riêng tồn tại vì lý do này', async () => {
    const { result } = renderHook(() => useChangeTaskStatus(), { wrapper })

    result.current.mutate({ id: 'a', status: 'DONE' })
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())

    expect(Object.keys(JSON.parse(fetchMock.mock.calls[0]![1].body))).toEqual(['status'])
  })
})
