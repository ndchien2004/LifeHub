import { apiFetch } from '@/shared/lib/apiClient'
import type {
  AiStatus,
  CategorySuggestion,
  PagedAiLogs,
  ParseResult,
  SuggestCategoryInput,
} from './types'

/** REST calls for the AI module (06-API-SPEC.md section 8). */

export const aiKeys = {
  all: ['ai'] as const,
  status: ['ai', 'status'] as const,
  logs: (page: number) => ['ai', 'logs', page] as const,
  suggestion: (note: string, type?: string) => ['ai', 'suggest-category', note, type] as const,
}

export function parseText(text: string): Promise<ParseResult> {
  return apiFetch<ParseResult>('/ai/parse', {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}

export function suggestCategory(input: SuggestCategoryInput): Promise<CategorySuggestion[]> {
  return apiFetch<{ suggestions: CategorySuggestion[] }>('/ai/suggest-category', {
    method: 'POST',
    body: JSON.stringify(input),
  }).then((response) => response.suggestions)
}

export function fetchAiStatus(): Promise<AiStatus> {
  return apiFetch<AiStatus>('/ai/status')
}

/** Spends a real API call, so this is only ever fired by the button in Settings. */
export function testConnection(): Promise<{ ok: boolean; model: string }> {
  return apiFetch<{ ok: boolean; model: string }>('/ai/test-connection', { method: 'POST' })
}

export function fetchAiLogs(page: number, size = 25): Promise<PagedAiLogs> {
  return apiFetch<PagedAiLogs>(`/ai/logs?page=${page}&size=${size}`)
}
