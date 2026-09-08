import { app, Menu, nativeImage, Tray, type BrowserWindow } from 'electron'

/**
 * System tray icon and the close-to-tray behaviour (FR-SYS-07).
 *
 * Closing the window has to keep the app alive, otherwise reminders stop arriving the moment the
 * user tidies their desktop - and a reminder that only fires while the window is open is not a
 * reminder. Quitting is still reachable, from the tray menu and from the usual quit shortcut.
 */

/**
 * A minimal calendar glyph, inlined as an SVG data URI.
 *
 * Kept in code rather than as an asset file so it survives asar packaging without a path lookup;
 * Phase 6 replaces it with the real application icon.
 */
const ICON_DATA_URI =
  'data:image/svg+xml;base64,' +
  Buffer.from(
    `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
       <rect x="3" y="6" width="26" height="23" rx="4" fill="none" stroke="#111" stroke-width="2.5"/>
       <path d="M3 13h26" stroke="#111" stroke-width="2.5"/>
       <path d="M10 3v6M22 3v6" stroke="#111" stroke-width="2.5" stroke-linecap="round"/>
       <circle cx="16" cy="21" r="3" fill="#111"/>
     </svg>`,
  ).toString('base64')

export interface TrayController {
  /** True once a real quit is under way, so the close handler stops intercepting. */
  isQuitting(): boolean
  /** Records that the app is quitting without starting one - for an OS initiated quit. */
  markQuitting(): void
  /** Records the intent and quits. */
  beginQuit(): void
  destroy(): void
}

export function createTray(getWindow: () => BrowserWindow | null): TrayController {
  let quitting = false

  const icon = nativeImage.createFromDataURL(ICON_DATA_URI)
  const tray = new Tray(icon.isEmpty() ? nativeImage.createEmpty() : icon)
  tray.setToolTip('LifeHub')

  const show = () => {
    const window = getWindow()
    if (!window) {
      return
    }
    if (window.isMinimized()) {
      window.restore()
    }
    window.show()
    window.focus()
  }

  const markQuitting = () => {
    quitting = true
  }

  const beginQuit = () => {
    markQuitting()
    app.quit()
  }

  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: 'Mở LifeHub', click: show },
      { type: 'separator' },
      { label: 'Thoát', click: beginQuit },
    ]),
  )

  // Single click is the Windows convention; double click covers the habit from other platforms.
  tray.on('click', show)
  tray.on('double-click', show)

  return {
    isQuitting: () => quitting,
    markQuitting,
    beginQuit,
    destroy: () => tray.destroy(),
  }
}
