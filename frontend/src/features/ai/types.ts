/** Wire types for the AI module (06-API-SPEC.md section 8). */

import type { Priority } from '@/features/tasks/types'
import type { CategoryType, TransactionType } from '@/features/finance/types'

export type ParseIntent = 'TRANSACTION' | 'TASK' | 'EVENT' | 'UNKNOWN'

/** Which parser answered. `RULE` is what raises the offline banner (FR-AI-08). */
export type ParseSource = 'AI' | 'RULE'

export type AiErrorCode = 'TIMEOUT' | 'INVALID_JSON' | 'AUTH' | 'RATE_LIMIT' | 'UNKNOWN'

/**
 * Below this, a field is left blank and highlighted instead of prefilled.
 *
 * <p>UC-09 alternate flow 10a. The point is that a wrong value the user does not notice is worse
 * than an empty one they have to fill in: an empty field asks a question, a wrong one tells a lie.
 */
export const CONFIDENCE_THRESHOLD = 0.6

/** Per-field certainty, keyed by the field name in the draft. */
export type FieldConfidence = Record<string, number>

export interface TransactionDraft {
  type: TransactionType
  amount?: number
  categoryId?: string
  categoryName?: string
  walletId?: string
  walletName?: string
  note?: string
  occurredAt?: string
  fieldConfidence: FieldConfidence
}

export interface TaskDraft {
  title: string
  priority: Priority
  dueAt?: string
  projectId?: string
  projectName?: string
  fieldConfidence: FieldConfidence
}

export interface EventDraft {
  title: string
  startAt: string
  endAt: string
  location?: string
  reminderOffsetMinutes: number[]
  fieldConfidence: FieldConfidence
}

export interface ParseResult {
  intent: ParseIntent
  confidence: number
  source: ParseSource
  transaction: TransactionDraft | null
  task: TaskDraft | null
  event: EventDraft | null
  warning: AiErrorCode | null
}

export interface CategorySuggestion {
  categoryId: string
  categoryName: string
  confidence: number
}

export interface AiStatus {
  enabled: boolean
  configured: boolean
  model: string
  /** Both of the above. False means every parse goes to the offline parser. */
  available: boolean
}

export interface AiLogEntry {
  id: string
  requestType: string
  inputText?: string
  outputJson?: string
  intent?: ParseIntent
  success: boolean
  errorCode?: AiErrorCode
  latencyMs?: number
  tokenInput?: number
  tokenOutput?: number
  model?: string
  createdAt: string
}

export interface PagedAiLogs {
  items: AiLogEntry[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface SuggestCategoryInput {
  note: string
  type?: CategoryType
}

/** Why the offline parser answered, in words the user can act on. */
export const WARNING_LABELS: Record<AiErrorCode, string> = {
  TIMEOUT: 'Không kết nối được dịch vụ AI',
  INVALID_JSON: 'AI trả về dữ liệu không đọc được',
  AUTH: 'API key không hợp lệ hoặc chưa được cấu hình',
  RATE_LIMIT: 'Đã vượt hạn mức gọi AI',
  UNKNOWN: 'Dịch vụ AI đang gặp sự cố',
}

export const INTENT_LABELS: Record<ParseIntent, string> = {
  TRANSACTION: 'Giao dịch',
  TASK: 'Công việc',
  EVENT: 'Sự kiện',
  UNKNOWN: 'Chưa rõ',
}
