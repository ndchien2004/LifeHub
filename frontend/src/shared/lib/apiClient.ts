import type { BackendInfo } from './electron'

/**
 * Thin fetch wrapper for the backend REST API.
 *
 * The port is random and the token is regenerated on every launch (NFR-SEC-03, NFR-SEC-04),
 * so neither can be baked into the bundle. Both arrive from the Electron main process over
 * IPC, and every request carries the token in the X-App-Token header (04-ARCHITECTURE.md §6.1).
 *
 * When the page is opened in a plain browser — `npm run dev` in frontend/ alone, without the
 * Electron shell — the bridge is absent and the Vite env vars are used instead, so the UI can
 * still be worked on against a manually started backend.
 */

export interface ApiError {
  code: string
  message: string
  field?: string
  traceId?: string
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: ApiError
}

/** Thrown for any non-2xx response, carrying the structured backend error. */
export class ApiRequestError extends Error {
  readonly code: string
  readonly field?: string
  readonly traceId?: string
  readonly status: number

  constructor(status: number, error: ApiError) {
    super(error.message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = error.code
    this.field = error.field
    this.traceId = error.traceId
  }
}

let cachedBackendInfo: BackendInfo | null = null

/** Discards the cached connection details, e.g. after the backend was respawned on a new port. */
export function resetBackendInfo(): void {
  cachedBackendInfo = null
}

async function resolveBackendInfo(): Promise<BackendInfo> {
  if (cachedBackendInfo) {
    return cachedBackendInfo
  }

  if (window.lifehub) {
    cachedBackendInfo = await window.lifehub.getBackendInfo()
    return cachedBackendInfo
  }

  const port = Number(import.meta.env.VITE_BACKEND_PORT ?? 8080)
  const token = import.meta.env.VITE_APP_TOKEN ?? ''
  cachedBackendInfo = { port, token }
  return cachedBackendInfo
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const { port, token } = await resolveBackendInfo()

  const response = await fetch(`http://127.0.0.1:${port}/api/v1${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json; charset=UTF-8',
      'X-App-Token': token,
      ...init.headers,
    },
  })

  const body = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (!response.ok || !body?.success) {
    throw new ApiRequestError(
      response.status,
      body?.error ?? {
        code: 'INTERNAL_ERROR',
        message: 'Không kết nối được với dịch vụ nền. Hãy thử khởi động lại ứng dụng.',
      },
    )
  }

  return body.data as T
}
