import { contextBridge, ipcRenderer } from 'electron'

/**
 * The only bridge between the renderer and Node.
 *
 * contextIsolation is on and nodeIntegration is off, so the renderer sees exactly the functions
 * below and never the raw ipcRenderer (04-ARCHITECTURE.md §6.3). Each listener returns its own
 * unsubscribe function, which is what lets a React effect clean up without reaching for
 * removeAllListeners and tearing down someone else's subscription.
 */

export interface BackendInfo {
  port: number
  token: string
}

export interface ReminderNotification {
  reminderId: string
  title: string
  body: string
  refType: 'EVENT' | 'TASK'
  refId: string
}

export interface ReminderFiredPayload {
  reminder: ReminderNotification
  /** False when the OS refused to show it, so the renderer falls back to an in-app toast. */
  shownNatively: boolean
}

export interface NavigateRequest {
  route: 'calendar' | 'tasks'
  id: string
}

export interface ApiKeyState {
  hasKey: boolean
  /** False when the OS has no credential store, so a key cannot outlive the session. */
  encryptionAvailable: boolean
}

export interface SetApiKeyResult {
  /** False when the key works now but could not be written to the credential store. */
  persisted: boolean
  hasKey: boolean
}

/** Subscribes to an IPC channel and hands back the matching unsubscribe. */
function subscribe<T>(channel: string, listener: (payload: T) => void): () => void {
  const handler = (_event: unknown, payload: T) => listener(payload)
  ipcRenderer.on(channel, handler)
  return () => ipcRenderer.removeListener(channel, handler)
}

contextBridge.exposeInMainWorld('lifehub', {
  /** Port and shared token for the running backend. */
  getBackendInfo: (): Promise<BackendInfo> => ipcRenderer.invoke('app:get-backend-info'),

  /** Notifies the renderer that the backend came back on a new port; returns an unsubscribe fn. */
  onBackendRestarted: (listener: (info: BackendInfo) => void): (() => void) =>
    subscribe('app:backend-restarted', listener),

  /** Whether the OS will display native notifications (UC-04 exception E1). */
  getNotificationPermission: (): Promise<'granted' | 'denied'> =>
    ipcRenderer.invoke('notification:permission'),

  /** Fires for every reminder the scheduler pushes, whether or not the OS displayed it. */
  onReminderFired: (listener: (payload: ReminderFiredPayload) => void): (() => void) =>
    subscribe('app:reminder-fired', listener),

  /** The user clicked a notification; take them to the event or task it points at. */
  onNavigate: (listener: (request: NavigateRequest) => void): (() => void) =>
    subscribe('app:navigate', listener),

  /**
   * Stores the AI API key in the OS credential store and restarts the backend (FR-AI-09).
   *
   * The key crosses this bridge once, on its way to safeStorage. It is never read back out — the
   * renderer can ask whether one exists, never what it is.
   */
  setApiKey: (key: string): Promise<SetApiKeyResult> =>
    ipcRenderer.invoke('secure:set-api-key', key),

  /** Whether a key is stored, and whether this machine can store one at all. */
  hasApiKey: (): Promise<ApiKeyState> => ipcRenderer.invoke('secure:has-api-key'),

  clearApiKey: (): Promise<{ hasKey: boolean }> => ipcRenderer.invoke('secure:clear-api-key'),

  /** Restarts the backend so settings it only reads at startup take effect. */
  restartBackend: (): Promise<BackendInfo> => ipcRenderer.invoke('app:restart-backend'),
})
