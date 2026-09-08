import { apiFetch } from '@/shared/lib/apiClient'
import type { BootstrapData } from './types'

export const bootstrapQueryKey = ['bootstrap'] as const

export function fetchBootstrap(): Promise<BootstrapData> {
  return apiFetch<BootstrapData>('/bootstrap')
}
