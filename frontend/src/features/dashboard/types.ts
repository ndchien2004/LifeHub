import type { Reminder } from '@/features/calendar/types'

/** Payload of GET /api/v1/bootstrap (06-API-SPEC.md §2). */
export interface BootstrapData {
  settings: Record<string, string>
  aiConfigured: boolean
  /** Reminders that came due while the app was closed, shown once at startup (UC-05). */
  missedReminders: Reminder[]
  /** Filled from Phase 3 (FR-SYS-01); null until then. */
  dashboard: DashboardSummary | null
}

export interface DashboardSummary {
  todayTasks: number
  overdueTasks: number
  upcomingEvents: number
  monthExpense: number
  monthIncome: number
  budgetAlerts: { categoryName: string; usage: number }[]
}
