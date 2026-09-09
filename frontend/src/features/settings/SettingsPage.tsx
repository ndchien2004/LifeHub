import { useEffect, useState } from 'react'
import { Eye, EyeOff, KeyRound, Loader2, ShieldAlert, Sparkles } from 'lucide-react'
import { AiLogsPanel } from '@/features/ai/components/AiLogsPanel'
import { useAiStatus, useTestConnection } from '@/features/ai/hooks'
import { Button } from '@/shared/components/ui/button'
import { Input, Label, Select } from '@/shared/components/ui/input'
import { useThemeStore, type Theme } from '@/shared/stores/themeStore'
import { cn } from '@/shared/lib/utils'
import {
  useApiKeyState,
  useClearApiKey,
  useSaveApiKey,
  useSettings,
  useUpdateSettings,
} from './hooks'
import {
  AI_MODEL_OPTIONS,
  CURRENCY_OPTIONS,
  SETTING_KEYS,
  TIMEZONE_OPTIONS,
  WEEK_START_LABELS,
  type WeekStart,
} from './types'

type Tab = 'general' | 'ai' | 'logs'

/** Falls back to the first offered model when neither the setting nor the backend names one. */
const DEFAULT_MODEL_ID = AI_MODEL_OPTIONS[0]?.id ?? 'claude-opus-5'

const TABS: { id: Tab; label: string }[] = [
  { id: 'general', label: 'Chung' },
  { id: 'ai', label: 'AI' },
  { id: 'logs', label: 'Nhật ký AI' },
]

const THEME_LABELS: Record<Theme, string> = {
  LIGHT: 'Sáng',
  DARK: 'Tối',
  SYSTEM: 'Theo hệ điều hành',
}

/**
 * The settings screen (FR-SYS-09, FR-AI-09, FR-AI-12).
 *
 * <p>The API key is handled apart from every other setting, and deliberately so: it goes to the
 * operating system credential store through the Electron bridge rather than to {@code PUT
 * /settings}, so it never touches the database, a backup, or a log file (NFR-SEC-01). The screen
 * can ask whether a key exists; it can never read one back.
 */
export function SettingsPage() {
  const [tab, setTab] = useState<Tab>('general')

  return (
    <div className="flex h-full flex-col">
      <header className="border-b border-border px-6 py-4">
        <h1 className="text-xl font-semibold tracking-tight">Cài đặt</h1>
        <p className="text-sm text-muted-foreground">
          Giao diện, múi giờ, tiền tệ và tính năng AI.
        </p>
      </header>

      <nav className="flex gap-1 border-b border-border px-6" aria-label="Mục cài đặt">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-current={tab === item.id ? 'page' : undefined}
            onClick={() => setTab(item.id)}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              tab === item.id
                ? 'border-primary text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {item.label}
          </button>
        ))}
      </nav>

      <div className="flex-1 overflow-y-auto p-6">
        {tab === 'general' && <GeneralSettings />}
        {tab === 'ai' && <AiSettingsPanel />}
        {tab === 'logs' && <AiLogsPanel />}
      </div>
    </div>
  )
}

function GeneralSettings() {
  const { data, isLoading } = useSettings()
  const update = useUpdateSettings()
  const theme = useThemeStore((state) => state.theme)
  const setTheme = useThemeStore((state) => state.setTheme)

  const settings = data?.settings ?? {}

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Đang tải cài đặt…</p>
  }

  const save = (key: string, value: string) => update.mutate({ [key]: value })

  return (
    <div className="max-w-xl space-y-6">
      <Section title="Giao diện" description="Áp dụng ngay, và được nhớ cho lần mở sau.">
        <div className="space-y-1.5">
          <Label htmlFor="setting-theme">Chủ đề</Label>
          <Select
            id="setting-theme"
            value={theme}
            onChange={(event) => setTheme(event.target.value as Theme)}
          >
            {(Object.keys(THEME_LABELS) as Theme[]).map((value) => (
              <option key={value} value={value}>
                {THEME_LABELS[value]}
              </option>
            ))}
          </Select>
        </div>
      </Section>

      <Section
        title="Khu vực"
        description="Múi giờ quyết định cách mọi mốc thời gian được hiển thị, nên đổi nó sẽ khởi động lại dịch vụ nền."
      >
        <div className="space-y-1.5">
          <Label htmlFor="setting-timezone">Múi giờ</Label>
          <Select
            id="setting-timezone"
            value={settings[SETTING_KEYS.timezone] ?? 'Asia/Ho_Chi_Minh'}
            onChange={(event) => save(SETTING_KEYS.timezone, event.target.value)}
          >
            {TIMEZONE_OPTIONS.map((zone) => (
              <option key={zone} value={zone}>
                {zone}
              </option>
            ))}
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="setting-week-start">Ngày bắt đầu tuần</Label>
          <Select
            id="setting-week-start"
            value={settings[SETTING_KEYS.weekStart] ?? 'MONDAY'}
            onChange={(event) => save(SETTING_KEYS.weekStart, event.target.value)}
          >
            {(Object.keys(WEEK_START_LABELS) as WeekStart[]).map((value) => (
              <option key={value} value={value}>
                {WEEK_START_LABELS[value]}
              </option>
            ))}
          </Select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="setting-currency">Tiền tệ</Label>
          <Select
            id="setting-currency"
            value={settings[SETTING_KEYS.currency] ?? 'VND'}
            onChange={(event) => save(SETTING_KEYS.currency, event.target.value)}
          >
            {CURRENCY_OPTIONS.map((code) => (
              <option key={code} value={code}>
                {code}
              </option>
            ))}
          </Select>
        </div>
      </Section>
    </div>
  )
}

function AiSettingsPanel() {
  const { data: settings } = useSettings()
  const { data: status } = useAiStatus()
  const { data: keyState } = useApiKeyState()
  const update = useUpdateSettings()
  const saveKey = useSaveApiKey()
  const clearKey = useClearApiKey()
  const testConnection = useTestConnection()

  const [apiKey, setApiKey] = useState('')
  const [revealed, setRevealed] = useState(false)

  // Clears the field once the key is safely in the credential store, so it is not left on screen.
  useEffect(() => {
    if (saveKey.isSuccess) {
      setApiKey('')
    }
  }, [saveKey.isSuccess])

  const values = settings?.settings ?? {}
  const enabled = values[SETTING_KEYS.aiEnabled] !== 'false'
  const model = values[SETTING_KEYS.aiModel] || status?.model || DEFAULT_MODEL_ID
  const inDesktopApp = typeof window !== 'undefined' && Boolean(window.lifehub)

  return (
    <div className="max-w-xl space-y-6">
      <Section
        title="Tính năng AI"
        description="Tắt AI thì ô nhập nhanh vẫn hoạt động, nhưng chỉ dùng bộ luật ngoại tuyến."
      >
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-input"
            checked={enabled}
            onChange={(event) =>
              update.mutate({ [SETTING_KEYS.aiEnabled]: String(event.target.checked) })
            }
          />
          Bật phân tích bằng AI
        </label>

        <div className="space-y-1.5">
          <Label htmlFor="setting-ai-model">Model</Label>
          <Select
            id="setting-ai-model"
            value={model}
            onChange={(event) => update.mutate({ [SETTING_KEYS.aiModel]: event.target.value })}
          >
            {AI_MODEL_OPTIONS.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label} — {option.hint}
              </option>
            ))}
          </Select>
          <p className="text-xs text-muted-foreground">
            Mỗi lần phân tích có 5 giây để trả lời; quá hạn thì tự chuyển sang chế độ ngoại tuyến.
            Model nhanh hơn sẽ ít rơi vào trường hợp đó hơn.
          </p>
        </div>
      </Section>

      <Section
        title="API key"
        description="Key được mã hóa bằng kho bảo mật của hệ điều hành, không bao giờ ghi vào cơ sở dữ liệu hay file log."
      >
        {!inDesktopApp && (
          <p className="flex items-start gap-2 rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">
            <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
            Chỉ lưu được API key khi chạy trong ứng dụng desktop.
          </p>
        )}

        {keyState && !keyState.encryptionAvailable && inDesktopApp && (
          <p className="flex items-start gap-2 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:bg-amber-950 dark:text-amber-200">
            <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
            Máy này không có kho bảo mật khả dụng. Key sẽ chỉ dùng được cho tới khi bạn đóng ứng
            dụng — LifeHub không ghi key ra file dưới dạng thường.
          </p>
        )}

        <div className="space-y-1.5">
          <Label htmlFor="setting-api-key">
            {keyState?.hasKey ? 'Thay API key' : 'Nhập API key'}
          </Label>
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Input
                id="setting-api-key"
                type={revealed ? 'text' : 'password'}
                autoComplete="off"
                spellCheck={false}
                placeholder={keyState?.hasKey ? '•••••••• (đã lưu)' : 'sk-ant-…'}
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                disabled={!inDesktopApp}
                className="pr-9"
              />
              <button
                type="button"
                onClick={() => setRevealed((current) => !current)}
                aria-label={revealed ? 'Ẩn API key' : 'Hiện API key'}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {revealed ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            <Button
              type="button"
              disabled={!inDesktopApp || apiKey.trim() === '' || saveKey.isPending}
              onClick={() => saveKey.mutate(apiKey.trim())}
            >
              {saveKey.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
              ) : (
                <KeyRound className="h-4 w-4" aria-hidden />
              )}
              Lưu
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            Lưu key sẽ khởi động lại dịch vụ nền — đó là cách duy nhất key đến được backend mà không
            phải đi qua đĩa.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={!status?.configured || testConnection.isPending}
            onClick={() => testConnection.mutate()}
          >
            {testConnection.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            ) : (
              <Sparkles className="h-4 w-4" aria-hidden />
            )}
            Kiểm tra kết nối
          </Button>

          {keyState?.hasKey && (
            <Button
              type="button"
              variant="ghost"
              disabled={clearKey.isPending}
              onClick={() => clearKey.mutate()}
            >
              Xóa key
            </Button>
          )}
        </div>

        <StatusLine
          configured={status?.configured ?? false}
          enabled={status?.enabled ?? false}
          model={status?.model}
        />
      </Section>
    </div>
  )
}

function StatusLine({
  configured,
  enabled,
  model,
}: {
  configured: boolean
  enabled: boolean
  model?: string
}) {
  const tone = configured && enabled ? 'text-emerald-600 dark:text-emerald-400' : 'text-muted-foreground'
  const text = !enabled
    ? 'AI đang tắt — mọi câu nhập nhanh dùng bộ luật ngoại tuyến.'
    : configured
      ? `Sẵn sàng, đang dùng ${model}.`
      : 'Chưa có API key — đang chạy ở chế độ ngoại tuyến.'

  return <p className={cn('text-sm', tone)}>{text}</p>
}

function Section({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children: React.ReactNode
}) {
  return (
    <section className="space-y-3 rounded-lg border border-border bg-card p-4">
      <div>
        <h2 className="text-sm font-semibold">{title}</h2>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>
      {children}
    </section>
  )
}
