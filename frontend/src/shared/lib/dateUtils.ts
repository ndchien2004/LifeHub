import { format } from 'date-fns'
import { vi } from 'date-fns/locale'

/**
 * Date helpers.
 *
 * The backend stores and returns UTC (AGENTS.md §3.2); everything shown to the user is
 * rendered in the machine local zone, which for this project is Asia/Ho_Chi_Minh.
 */

/** "04/09/2026" */
export function formatDate(value: Date | string): string {
  return format(toDate(value), 'dd/MM/yyyy', { locale: vi })
}

/** "04/09/2026 14:30" */
export function formatDateTime(value: Date | string): string {
  return format(toDate(value), 'dd/MM/yyyy HH:mm', { locale: vi })
}

/** "14:30" */
export function formatTime(value: Date | string): string {
  return format(toDate(value), 'HH:mm', { locale: vi })
}

function toDate(value: Date | string): Date {
  return value instanceof Date ? value : new Date(value)
}
