import { useEffect } from 'react'
import { resolveTheme, useThemeStore, type Theme } from '@/shared/stores/themeStore'

/**
 * Keeps the `dark` class on <html> in sync with the stored preference.
 *
 * While the preference is SYSTEM the OS setting is followed live, so switching the
 * operating system to dark mode repaints the app without a restart.
 */
export function useTheme() {
  const theme = useThemeStore((state) => state.theme)
  const setTheme = useThemeStore((state) => state.setTheme)

  useEffect(() => {
    const apply = () => {
      document.documentElement.classList.toggle('dark', resolveTheme(theme) === 'dark')
    }
    apply()

    if (theme !== 'SYSTEM') {
      return
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [theme])

  return { theme, setTheme, resolved: resolveTheme(theme) }
}

export type { Theme }
