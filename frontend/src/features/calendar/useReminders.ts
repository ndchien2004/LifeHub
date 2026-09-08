import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { toast } from '@/shared/components/ui/toast'
import { useNavStore } from '@/shared/stores/navStore'
import { calendarKeys, reminderKeys } from './api'

/**
 * The renderer half of reminder delivery (UC-04).
 *
 * The Electron main process owns the SSE connection and the native notification, because a reminder
 * has to fire while the window is hidden in the tray (FR-CAL-07) and a renderer that is not on
 * screen cannot show anything. This hook handles what is left: refreshing the calendar when a
 * reminder fires, an in-app toast when the OS refused to display one (exception E1), and following
 * a click on a notification to the right screen.
 *
 * Everything degrades quietly when the bridge is absent - the page opened in a plain browser during
 * frontend-only development still works, just without notifications.
 */
export function useReminders() {
  const queryClient = useQueryClient()
  const navigate = useNavStore((state) => state.navigate)
  const [notificationsBlocked, setNotificationsBlocked] = useState(false)

  useEffect(() => {
    const bridge = window.lifehub
    if (!bridge) {
      return
    }

    void bridge
      .getNotificationPermission()
      .then((permission) => setNotificationsBlocked(permission !== 'granted'))
      .catch(() => setNotificationsBlocked(true))

    const stopReminders = bridge.onReminderFired(({ reminder, shownNatively }) => {
      void queryClient.invalidateQueries({ queryKey: calendarKeys.all })
      void queryClient.invalidateQueries({ queryKey: reminderKeys.all })

      if (!shownNatively) {
        // The only place the reminder can still reach the user.
        setNotificationsBlocked(true)
        toast.success(`${reminder.title} — ${reminder.body}`)
      }
    })

    const stopNavigate = bridge.onNavigate(({ route }) => navigate(route))

    return () => {
      stopReminders()
      stopNavigate()
    }
  }, [queryClient, navigate])

  return { notificationsBlocked }
}
