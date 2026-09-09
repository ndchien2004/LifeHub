/** Surface exposed by electron/preload.ts through contextBridge. Nothing else is reachable. */
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

export interface LifeHubBridge {
  getBackendInfo(): Promise<BackendInfo>
  onBackendRestarted(listener: (info: BackendInfo) => void): () => void
  getNotificationPermission(): Promise<'granted' | 'denied'>
  onReminderFired(listener: (payload: ReminderFiredPayload) => void): () => void
  onNavigate(listener: (request: NavigateRequest) => void): () => void
  setApiKey(key: string): Promise<SetApiKeyResult>
  hasApiKey(): Promise<ApiKeyState>
  clearApiKey(): Promise<{ hasKey: boolean }>
  restartBackend(): Promise<BackendInfo>
}

declare global {
  interface Window {
    lifehub?: LifeHubBridge
  }
}

export {}
