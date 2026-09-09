import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { aiKeys } from '@/features/ai/api'
import { bootstrapQueryKey } from '@/features/dashboard/api'
import { toast } from '@/shared/components/ui/toast'
import { ApiRequestError } from '@/shared/lib/apiClient'
import { resetBackendInfo } from '@/shared/lib/apiClient'
import * as api from './api'
import { settingsQueryKey } from './api'

/** Data hooks for the settings module. */

export function useSettings() {
  return useQuery({ queryKey: settingsQueryKey, queryFn: api.fetchSettings })
}

/**
 * Saves settings, and offers a restart when one is needed.
 *
 * <p>The display timezone is resolved once when the backend starts, so changing it does nothing
 * until the process comes back. Rather than leave the user to work that out, the backend says so in
 * the response and this restarts it for them - the Electron bridge already has to do the same thing
 * for the API key.
 */
export function useUpdateSettings() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: api.updateSettings,
    onSuccess: async (result) => {
      queryClient.setQueryData(settingsQueryKey, result)
      void queryClient.invalidateQueries({ queryKey: aiKeys.all })
      void queryClient.invalidateQueries({ queryKey: bootstrapQueryKey })

      if (!result.requiresRestart) {
        toast.success('Đã lưu cài đặt')
        return
      }

      if (!window.lifehub) {
        toast.error('Đã lưu. Hãy khởi động lại ứng dụng để áp dụng múi giờ mới.')
        return
      }

      toast.success('Đã lưu. Đang khởi động lại dịch vụ nền để áp dụng…')
      await restartBackend(queryClient)
    },
    onError: (error) =>
      toast.error(
        error instanceof ApiRequestError ? error.message : 'Không lưu được cài đặt.',
      ),
  })
}

/** Stores the API key through Electron `safeStorage` and restarts the backend (FR-AI-09). */
export function useSaveApiKey() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (key: string) => {
      if (!window.lifehub) {
        throw new Error('Chức năng này chỉ hoạt động trong ứng dụng desktop.')
      }
      const result = await window.lifehub.setApiKey(key)
      // The backend restarted on a new port and token, so cached connection details are stale.
      resetBackendInfo()
      return result
    },
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: aiKeys.all })
      void queryClient.invalidateQueries({ queryKey: bootstrapQueryKey })
      if (result.persisted) {
        toast.success('Đã lưu API key vào kho bảo mật của hệ điều hành')
      } else {
        toast.error(
          'API key dùng được cho phiên này nhưng máy không có kho bảo mật, nên sẽ mất khi đóng ứng dụng.',
        )
      }
    },
    onError: (error) =>
      toast.error(error instanceof Error ? error.message : 'Không lưu được API key.'),
  })
}

export function useClearApiKey() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async () => {
      if (!window.lifehub) {
        throw new Error('Chức năng này chỉ hoạt động trong ứng dụng desktop.')
      }
      const result = await window.lifehub.clearApiKey()
      resetBackendInfo()
      return result
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: aiKeys.all })
      void queryClient.invalidateQueries({ queryKey: bootstrapQueryKey })
      toast.success('Đã xóa API key')
    },
    onError: (error) =>
      toast.error(error instanceof Error ? error.message : 'Không xóa được API key.'),
  })
}

/** Whether a key is stored, and whether this machine can store one at all. */
export function useApiKeyState() {
  return useQuery({
    queryKey: ['settings', 'api-key'],
    queryFn: async () => {
      if (!window.lifehub) {
        return { hasKey: false, encryptionAvailable: false }
      }
      return window.lifehub.hasApiKey()
    },
  })
}

async function restartBackend(queryClient: ReturnType<typeof useQueryClient>) {
  try {
    await window.lifehub?.restartBackend()
    resetBackendInfo()
    await queryClient.invalidateQueries()
  } catch {
    toast.error('Không khởi động lại được dịch vụ nền. Hãy đóng và mở lại ứng dụng.')
  }
}
