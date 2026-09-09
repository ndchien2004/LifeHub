import { useEffect, useRef } from 'react'
import { useBootstrap } from '@/features/dashboard/useBootstrap'
import { useThemeStore, type Theme } from '@/shared/stores/themeStore'
import { updateSettings } from './api'
import { SETTING_KEYS } from './types'

/**
 * Moves the theme preference from browser storage to `setting/app.theme` (PROGRESS.md C-2).
 *
 * <p>Phase 0 kept the choice in localStorage because `/settings` did not exist yet. Now it does, so
 * the setting is authoritative and the preference follows the data file rather than the browser
 * profile - which is what makes it survive a reinstall and match every other preference.
 *
 * <p>The store stays in place as the live value. Repainting has to be instant, and waiting for a
 * round trip before the class changes would make every toggle feel broken. The write goes out
 * afterwards, and a failure is silent: the user can see the theme they asked for, and a toast about
 * a key/value row would explain nothing they could act on.
 */
export function useThemeSync() {
  const { data: bootstrap } = useBootstrap()
  const theme = useThemeStore((state) => state.theme)
  const setTheme = useThemeStore((state) => state.setTheme)

  /** Set once the stored value has been applied, so hydration is not echoed straight back. */
  const hydrated = useRef(false)
  const lastPersisted = useRef<Theme | null>(null)

  useEffect(() => {
    if (hydrated.current || !bootstrap) {
      return
    }
    hydrated.current = true

    const stored = bootstrap.settings[SETTING_KEYS.theme]
    if (stored === 'LIGHT' || stored === 'DARK' || stored === 'SYSTEM') {
      lastPersisted.current = stored
      setTheme(stored)
    }
  }, [bootstrap, setTheme])

  useEffect(() => {
    if (!hydrated.current || lastPersisted.current === theme) {
      return
    }
    lastPersisted.current = theme
    void updateSettings({ [SETTING_KEYS.theme]: theme }).catch(() => {
      // The theme is already applied; a failed write is not worth interrupting the user over.
    })
  }, [theme])
}
