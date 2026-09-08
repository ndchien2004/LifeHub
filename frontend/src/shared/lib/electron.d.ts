/** Surface exposed by electron/preload.ts through contextBridge. Nothing else is reachable. */
export interface BackendInfo {
  port: number
  token: string
}

export interface LifeHubBridge {
  getBackendInfo(): Promise<BackendInfo>
  onBackendRestarted(listener: (info: BackendInfo) => void): () => void
}

declare global {
  interface Window {
    lifehub?: LifeHubBridge
  }
}

export {}
