import { Notification } from 'electron'
import http from 'node:http'
import type { BackendInfo } from './backendManager'
import type { ReminderNotification } from './reminderStream'

/**
 * Turns a fired reminder into a native OS notification with its two action buttons
 * (FR-CAL-07, FR-CAL-08, UC-04).
 *
 * The buttons post straight back to the backend from the main process rather than routing through
 * the renderer, because the window may be hidden in the tray when the notification appears - which
 * is exactly the case FR-CAL-07 exists for.
 *
 * `actions` only render on macOS. On Windows they need a registered AppUserModelID and a toast XML
 * template, neither of which exists before the app is packaged, so on that platform the buttons are
 * absent and clicking the body opens the app instead. The endpoints behind them are fully
 * implemented either way; Phase 6 revisits the packaging side (PROGRESS.md, decision B-5).
 */

export interface ReminderNotifierOptions {
  /** Brings the window forward and navigates to the event or task. */
  onActivate: (notification: ReminderNotification) => void
  /** Mirrors every reminder into the renderer for the in-app fallback (UC-04 exception E1). */
  onFired: (notification: ReminderNotification, shownNatively: boolean) => void
}

export class ReminderNotifier {
  private info: BackendInfo | null = null

  constructor(private readonly options: ReminderNotifierOptions) {}

  setBackendInfo(info: BackendInfo): void {
    this.info = info
  }

  /** Whether the OS will actually display notifications for this app (UC-04 exception E1). */
  isSupported(): boolean {
    return Notification.isSupported()
  }

  show(reminder: ReminderNotification): void {
    if (!this.isSupported()) {
      // No native channel: the renderer shows an in-app toast so the reminder is not simply lost.
      this.options.onFired(reminder, false)
      return
    }

    const notification = new Notification({
      title: reminder.title,
      body: reminder.body,
      actions: [
        { type: 'button', text: 'Hoãn 10 phút' },
        { type: 'button', text: 'Đã xong' },
      ],
      closeButtonText: 'Đóng',
    })

    notification.on('click', () => this.options.onActivate(reminder))
    notification.on('action', (_event, index) => {
      if (index === 0) {
        void this.post(`/api/v1/reminders/${reminder.reminderId}/snooze`, { minutes: 10 })
      } else {
        void this.post(`/api/v1/reminders/${reminder.reminderId}/dismiss`)
      }
    })

    notification.show()
    this.options.onFired(reminder, true)
  }

  /**
   * Fire-and-forget call to the backend.
   *
   * Failures are logged rather than surfaced: the notification is already gone from the screen by
   * the time a response arrives, so there is nowhere to report an error to, and the reminder stays
   * in a state the user can resolve from the calendar.
   */
  private post(path: string, body?: unknown): Promise<void> {
    return new Promise((resolve) => {
      if (!this.info) {
        resolve()
        return
      }
      const payload = body ? JSON.stringify(body) : ''
      const request = http.request(
        {
          host: '127.0.0.1',
          port: this.info.port,
          path,
          method: 'POST',
          headers: {
            'Content-Type': 'application/json; charset=UTF-8',
            'Content-Length': Buffer.byteLength(payload),
            'X-App-Token': this.info.token,
          },
        },
        (response) => {
          response.resume()
          response.on('end', () => resolve())
        },
      )
      request.on('error', (error) => {
        console.warn(`[reminders] không gọi được ${path}: ${error.message}`)
        resolve()
      })
      request.end(payload)
    })
  }
}
