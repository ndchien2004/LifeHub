import { create } from 'zustand'
import { useEffect } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/shared/components/ui/button'
import { cn } from '@/shared/lib/utils'

/**
 * Toast notifications, including the undo affordance required by NFR-USE-04.
 *
 * <p>A destructive action stays reversible for five seconds. The toast is the only place that
 * window is visible, so its timing is part of the requirement rather than a cosmetic detail.
 */

export const UNDO_WINDOW_MS = 5000

export interface Toast {
  id: number
  message: string
  variant: 'default' | 'success' | 'error'
  /** Present only for reversible actions; rendering the button is what makes the undo reachable. */
  onUndo?: () => void
  durationMs: number
}

interface ToastState {
  toasts: Toast[]
  push: (toast: Omit<Toast, 'id' | 'durationMs'> & { durationMs?: number }) => number
  dismiss: (id: number) => void
}

let nextId = 1

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: ({ durationMs, ...toast }) => {
    const id = nextId++
    set((state) => ({
      toasts: [...state.toasts, { ...toast, id, durationMs: durationMs ?? 4000 }],
    }))
    return id
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}))

/** Convenience helpers so callers do not reach into the store shape directly. */
export const toast = {
  success: (message: string) => useToastStore.getState().push({ message, variant: 'success' }),
  error: (message: string) => useToastStore.getState().push({ message, variant: 'error' }),
  /** Shows a message with an "Hoàn tác" button for the full five second window. */
  undoable: (message: string, onUndo: () => void) =>
    useToastStore.getState().push({ message, variant: 'default', onUndo, durationMs: UNDO_WINDOW_MS }),
}

export function Toaster() {
  const toasts = useToastStore((state) => state.toasts)

  return (
    <div
      className="pointer-events-none fixed bottom-4 right-4 z-[100] flex w-full max-w-sm flex-col gap-2"
      role="region"
      aria-label="Thông báo"
    >
      {toasts.map((item) => (
        <ToastItem key={item.id} toast={item} />
      ))}
    </div>
  )
}

function ToastItem({ toast: item }: { toast: Toast }) {
  const dismiss = useToastStore((state) => state.dismiss)

  useEffect(() => {
    const timer = setTimeout(() => dismiss(item.id), item.durationMs)
    return () => clearTimeout(timer)
  }, [item.id, item.durationMs, dismiss])

  return (
    <div
      role="status"
      className={cn(
        'pointer-events-auto flex items-center gap-3 rounded-md border px-4 py-3 shadow-lg',
        'animate-in slide-in-from-bottom-2',
        item.variant === 'error'
          ? 'border-destructive/40 bg-destructive text-destructive-foreground'
          : 'border-border bg-card text-card-foreground',
      )}
    >
      <span className="flex-1 text-sm">{item.message}</span>

      {item.onUndo && (
        <Button
          size="sm"
          variant="outline"
          onClick={() => {
            item.onUndo?.()
            dismiss(item.id)
          }}
        >
          Hoàn tác
        </Button>
      )}

      <button
        type="button"
        aria-label="Đóng thông báo"
        onClick={() => dismiss(item.id)}
        className="rounded-sm opacity-60 transition-opacity hover:opacity-100"
      >
        <X className="h-4 w-4" aria-hidden />
      </button>
    </div>
  )
}
