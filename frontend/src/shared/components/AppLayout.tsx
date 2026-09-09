import type { ReactNode } from 'react'
import { CalendarDays, FolderKanban, LayoutDashboard, ListTodo, Settings, Wallet } from 'lucide-react'
import { ThemeToggle } from '@/shared/components/ThemeToggle'
import { useNavStore, type Route } from '@/shared/stores/navStore'
import { cn } from '@/shared/lib/utils'

/**
 * Application shell: fixed sidebar plus main area.
 *
 * <p>Sections not yet built are disabled and labelled with the phase that delivers them, so the
 * sidebar doubles as a visible map of what is finished.
 */
const NAV_ITEMS: {
  route: Route
  label: string
  Icon: typeof LayoutDashboard
  enabled: boolean
  phase: number | null
}[] = [
  { route: 'dashboard', label: 'Tổng quan', Icon: LayoutDashboard, enabled: true, phase: null },
  { route: 'tasks', label: 'Công việc', Icon: ListTodo, enabled: true, phase: null },
  { route: 'projects', label: 'Dự án & Nhãn', Icon: FolderKanban, enabled: true, phase: null },
  { route: 'calendar', label: 'Lịch', Icon: CalendarDays, enabled: true, phase: null },
  { route: 'finance', label: 'Tài chính', Icon: Wallet, enabled: true, phase: null },
  { route: 'settings', label: 'Cài đặt', Icon: Settings, enabled: true, phase: null },
]

export function AppLayout({ children }: { children: ReactNode }) {
  const route = useNavStore((state) => state.route)
  const navigate = useNavStore((state) => state.navigate)

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <aside className="flex w-60 shrink-0 flex-col border-r border-border bg-card">
        <div className="flex h-14 items-center gap-2 border-b border-border px-5">
          <span className="text-lg font-semibold tracking-tight">LifeHub</span>
        </div>

        <nav className="flex-1 space-y-1 p-3" aria-label="Điều hướng chính">
          {NAV_ITEMS.map(({ route: target, label, Icon, enabled, phase }) => {
            const active = route === target
            return (
              <button
                key={target}
                type="button"
                disabled={!enabled}
                aria-current={active ? 'page' : undefined}
                title={enabled ? label : `${label} — có từ Phase ${phase}`}
                onClick={() => navigate(target)}
                className={cn(
                  'flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                  !enabled && 'cursor-not-allowed text-muted-foreground/60',
                  enabled && active && 'bg-accent font-medium text-accent-foreground',
                  enabled && !active && 'text-foreground hover:bg-accent/60',
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
            )
          })}
        </nav>

        <div className="border-t border-border p-3">
          <ThemeToggle />
        </div>
      </aside>

      <main className="min-w-0 flex-1 overflow-y-auto">{children}</main>
    </div>
  )
}
