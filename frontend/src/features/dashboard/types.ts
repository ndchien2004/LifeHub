import type { Reminder } from '@/features/calendar/types'
import type { BudgetAlert } from '@/features/finance/types'

/** Payload of GET /api/v1/bootstrap (06-API-SPEC.md §2). */
export interface BootstrapData {
  settings: Record<string, string>
  aiConfigured: boolean
  /** Reminders that came due while the app was closed, shown once at startup (UC-05). */
  missedReminders: Reminder[]
  /** Filled from Phase 3 (FR-SYS-01); null if the figures could not be aggregated. */
  dashboard: DashboardSummary | null
}

export interface DashboardSummary {
  todayTasks: number
  overdueTasks: number
  upcomingEvents: number
  monthExpense: number
  monthIncome: number
  budgetAlerts: BudgetAlert[]
}
