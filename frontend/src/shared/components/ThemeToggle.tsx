import { Monitor, Moon, Sun } from 'lucide-react'
import { useTheme } from '@/shared/hooks/useTheme'
import type { Theme } from '@/shared/stores/themeStore'
import { cn } from '@/shared/lib/utils'

const OPTIONS: { value: Theme; label: string; Icon: typeof Sun }[] = [
  { value: 'LIGHT', label: 'Sáng', Icon: Sun },
  { value: 'DARK', label: 'Tối', Icon: Moon },
  { value: 'SYSTEM', label: 'Theo hệ thống', Icon: Monitor },
]

/** Three-way theme switch (FR-SYS-06). */
export function ThemeToggle() {
  const { theme, setTheme } = useTheme()

  return (
    <div
      role="radiogroup"
      aria-label="Giao diện"
      className="inline-flex items-center gap-1 rounded-md border border-border bg-background p-1"
    >
      {OPTIONS.map(({ value, label, Icon }) => (
        <button
          key={value}
          type="button"
          role="radio"
          aria-checked={theme === value}
          aria-label={label}
          title={label}
          onClick={() => setTheme(value)}
          className={cn(
            'inline-flex h-7 w-7 items-center justify-center rounded transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            theme === value
              ? 'bg-primary text-primary-foreground'
              : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
          )}
        >
          <Icon className="h-4 w-4" aria-hidden />
        </button>
      ))}
    </div>
  )
}
