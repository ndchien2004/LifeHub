import type { ReactNode } from 'react'
import { CalendarDays, LayoutDashboard, ListTodo, Settings, Wallet } from 'lucide-react'
import { ThemeToggle } from '@/shared/components/ThemeToggle'
import { cn } from '@/shared/lib/utils'

/**
 * Application shell: fixed sidebar plus main area.
 *
 * Every section other than Dashboard is disabled — each one lights up in the phase that
 * builds it, so the sidebar doubles as a visible map of what is finished.
 */
const NAV_ITEMS = [
  { label: 'Tổng quan', Icon: LayoutDashboard, enabled: true, phase: null },
  { label: 'Công việc', Icon: ListTodo, enabled: false, phase: 1 },
  { label: 'Lịch', Icon: CalendarDays, enabled: false, phase: 2 },
  { label: 'Tài chính', Icon: Wallet, enabled: false, phase: 3 },
  { label: 'Cài đặt', Icon: Settings, enabled: false, phase: 4 },
] as const

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <aside className="flex w-60 shrink-0 flex-col border-r border-border bg-card">
        <div className="flex h-14 items-center gap-2 border-b border-border px-5">
          <span className="text-lg font-semibold tracking-tight">LifeHub</span>
        </div>

        <nav className="flex-1 space-y-1 p-3" aria-label="Điều hướng chính">
          {NAV_ITEMS.map(({ label, Icon, enabled, phase }) => (
            <button
              key={label}
              type="button"
              disabled={!enabled}
              title={enabled ? label : `${label} — có từ Phase ${phase}`}
              className={cn(
                'flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                enabled
                  ? 'bg-accent font-medium text-accent-foreground'
                  : 'cursor-not-allowed text-muted-foreground/60',
              )}
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden />
              <span className="flex-1 text-left">{label}</span>
              {!enabled && (
                <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-medium tabular-nums">
                  P{phase}
                </span>
              )}
            </button>
          ))}
        </nav>

        <div className="border-t border-border p-3">
          <ThemeToggle />
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto">{children}</main>
    </div>
  )
}
