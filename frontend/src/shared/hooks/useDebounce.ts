import { useEffect, useState } from 'react'

/**
 * Delays a rapidly changing value.
 *
 * <p>Used by the task search box (FR-TSK-10) so typing does not fire a request per keystroke.
 */
export function useDebounce<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
