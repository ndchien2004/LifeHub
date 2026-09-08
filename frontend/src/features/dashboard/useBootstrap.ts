import { useQuery } from '@tanstack/react-query'
import { bootstrapQueryKey, fetchBootstrap } from './api'

/**
 * Loads the startup payload.
 *
 * Retries are disabled: Electron has already waited for the health check to pass before the
 * renderer is told the port, so a failure here is a real problem the user should see at once
 * rather than something to sit through a backoff for.
 */
export function useBootstrap() {
  return useQuery({
    queryKey: bootstrapQueryKey,
    queryFn: fetchBootstrap,
    retry: false,
    staleTime: Infinity,
  })
}
