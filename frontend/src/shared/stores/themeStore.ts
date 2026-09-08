import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * Theme preference (FR-SYS-06).
 *
 * Phase 0 keeps the choice in localStorage. It moves to the `app.theme` setting in Phase 4,
 * once PUT /settings exists — an approved deviation recorded in PROGRESS.md (C-2).
 */
export type Theme = 'LIGHT' | 'DARK' | 'SYSTEM'

export const THEME_STORAGE_KEY = 'lifehub.theme'

interface ThemeState {
  theme: Theme
  setTheme: (theme: Theme) => void
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      theme: 'SYSTEM',
      setTheme: (theme) => set({ theme }),
    }),
    { name: THEME_STORAGE_KEY },
  ),
)

/** Resolves SYSTEM against the OS preference; LIGHT and DARK pass through. */
export function resolveTheme(theme: Theme): 'light' | 'dark' {
  if (theme === 'SYSTEM') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return theme === 'DARK' ? 'dark' : 'light'
}
