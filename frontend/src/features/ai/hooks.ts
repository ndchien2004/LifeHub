import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/components/ui/toast'
import { ApiRequestError } from '@/shared/lib/apiClient'
import { useDebounce } from '@/shared/hooks/useDebounce'
import type { CategoryType } from '@/features/finance/types'
import * as api from './api'
import { aiKeys } from './api'

/** Data hooks for the AI module. */

/** UC-10 step 2: wait for a pause in typing before asking anything. */
const SUGGESTION_DEBOUNCE_MS = 800

export function useAiStatus() {
  return useQuery({ queryKey: aiKeys.status, queryFn: api.fetchAiStatus })
}

/**
 * Runs one natural language parse.
 *
 * <p>Errors are reported but not thrown onward: the backend already falls back to the offline
 * parser for anything it can recover from, so an error here means the request itself failed and the
 * palette should say so rather than hang.
 */
export function useParseText() {
  return useMutation({
    mutationFn: (text: string) => api.parseText(text),
    onError: (error) =>
      toast.error(
        error instanceof ApiRequestError
          ? error.message
          : 'Không phân tích được câu vừa nhập. Hãy thử lại.',
      ),
  })
}

/**
 * Category suggestions for a note (FR-AI-07, UC-10).
 *
 * <p>Debounced here rather than in the caller so every consumer gets the same 800 ms pause, and
 * disabled entirely for short notes - two characters cannot suggest anything and would still cost a
 * request. Failures are silent by design (UC-10 exception E1): these are optional chips under a
 * field, and an error toast would interrupt someone in the middle of typing.
 */
export function useCategorySuggestions(note: string, type: CategoryType, enabled: boolean) {
  const debounced = useDebounce(note.trim(), SUGGESTION_DEBOUNCE_MS)

  return useQuery({
    queryKey: aiKeys.suggestion(debounced, type),
    queryFn: () => api.suggestCategory({ note: debounced, type }),
    enabled: enabled && debounced.length >= 3,
    // The same note maps to the same category all day; re-asking is spending money for nothing.
    staleTime: 10 * 60 * 1000,
    retry: false,
  })
}

export function useAiLogs(page: number) {
  return useQuery({ queryKey: aiKeys.logs(page), queryFn: () => api.fetchAiLogs(page) })
}

export function useTestConnection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.testConnection,
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: aiKeys.status })
      toast.success(`Kết nối thành công. Model đang dùng: ${result.model}`)
    },
    onError: (error) =>
      toast.error(
        error instanceof ApiRequestError
          ? error.message
          : 'Không kiểm tra được kết nối tới dịch vụ AI.',
      ),
  })
}
