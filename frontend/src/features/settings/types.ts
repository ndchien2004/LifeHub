/** Wire types for the settings module (06-API-SPEC.md section 2, FR-SYS-09). */

export interface SettingsResponse {
  settings: Record<string, string>
  /**
   * True when something in the update is only read at startup, so the backend has to be
   * restarted before it takes effect.
   */
  requiresRestart: boolean
}

export const SETTING_KEYS = {
  theme: 'app.theme',
  timezone: 'app.timezone',
  weekStart: 'app.week_start',
  currency: 'app.currency',
  aiEnabled: 'ai.enabled',
  aiModel: 'ai.model',
  weeklyInsightCron: 'ai.weekly_insight_cron',
  backupDir: 'backup.dir',
  backupKeepCount: 'backup.keep_count',
} as const

export type WeekStart = 'MONDAY' | 'SUNDAY'

export const WEEK_START_LABELS: Record<WeekStart, string> = {
  MONDAY: 'Thứ 2',
  SUNDAY: 'Chủ nhật',
}

/**
 * Timezones offered in the picker.
 *
 * <p>A short, curated list rather than the full IANA database: this is a personal app used from one
 * place at a time, and a searchable list of six hundred zones would be a worse experience than six
 * relevant ones plus whatever the machine reports.
 */
export const TIMEZONE_OPTIONS = [
  'Asia/Ho_Chi_Minh',
  'Asia/Bangkok',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Europe/London',
  'Europe/Berlin',
  'America/New_York',
  'UTC',
]

/**
 * Models offered in the picker.
 *
 * <p>Ordered by capability. The default is the most capable one - picking a cheaper model is the
 * user's call to make, not a default to impose on them - and the faster options are there because
 * natural language parsing runs under a five second budget (04-ARCHITECTURE.md 7) and a slow model
 * on a slow connection will land in the offline fallback more often.
 */
export const AI_MODEL_OPTIONS: { id: string; label: string; hint: string }[] = [
  { id: 'claude-opus-5', label: 'Claude Opus 5', hint: 'Chính xác nhất (mặc định)' },
  { id: 'claude-sonnet-5', label: 'Claude Sonnet 5', hint: 'Cân bằng tốc độ và chi phí' },
  { id: 'claude-haiku-4-5', label: 'Claude Haiku 4.5', hint: 'Nhanh và rẻ nhất' },
]

export const CURRENCY_OPTIONS = ['VND', 'USD', 'EUR', 'JPY']
