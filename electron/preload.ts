import { contextBridge, ipcRenderer } from 'electron'

/**
 * The only bridge between the renderer and Node.
 *
 * contextIsolation is on and nodeIntegration is off, so the renderer sees exactly the two
 * functions below and never the raw ipcRenderer (04-ARCHITECTURE.md §6.3).
 */

export interface BackendInfo {
  port: number
  token: string
}

contextBridge.exposeInMainWorld('lifehub', {
  /** Port and shared token for the running backend. */
  getBackendInfo: (): Promise<BackendInfo> => ipcRenderer.invoke('app:get-backend-info'),

  /** Notifies the renderer that the backend came back on a new port; returns an unsubscribe fn. */
  onBackendRestarted: (listener: (info: BackendInfo) => void): (() => void) => {
    const handler = (_event: unknown, info: BackendInfo) => listener(info)
    ipcRenderer.on('app:backend-restarted', handler)
    return () => ipcRenderer.removeListener('app:backend-restarted', handler)
  },
})
