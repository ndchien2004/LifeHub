import { apiFetch } from '@/shared/lib/apiClient'
import type { SettingsResponse } from './types'

/** REST calls for the settings module. */

export const settingsQueryKey = ['settings'] as const

export function fetchSettings(): Promise<SettingsResponse> {
  return apiFetch<SettingsResponse>('/settings')
}

/** Sends only the keys that changed; the response carries the whole set back. */
export function updateSettings(updates: Record<string, string>): Promise<SettingsResponse> {
  return apiFetch<SettingsResponse>('/settings', {
    method: 'PUT',
    body: JSON.stringify(updates),
  })
}
