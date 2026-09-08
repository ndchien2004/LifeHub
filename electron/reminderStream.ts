import http from 'node:http'
import { EventEmitter } from 'node:events'
import type { BackendInfo } from './backendManager'

/**
 * Keeps a Server-Sent Events connection to the backend open and emits fired reminders
 * (04-ARCHITECTURE.md §6.2).
 *
 * Uses a raw http request rather than EventSource because the channel is protected by the
 * `X-App-Token` header (NFR-SEC-04), and EventSource has no way to set request headers.
 *
 * The connection is expected to end regularly: the backend caps a subscription's lifetime, the
 * machine sleeps, the backend restarts on a new port. Reconnecting with a growing backoff is
 * therefore the normal path, not error handling.
 */

export interface ReminderNotification {
  reminderId: string
  title: string
  body: string
  refType: 'EVENT' | 'TASK'
  refId: string
}

const INITIAL_BACKOFF_MS = 1_000
const MAX_BACKOFF_MS = 30_000

export class ReminderStream extends EventEmitter {
  private request: http.ClientRequest | null = null
  private retryTimer: NodeJS.Timeout | null = null
  private backoffMs = INITIAL_BACKOFF_MS
  private info: BackendInfo | null = null
  private stopped = true

  /** Connects, or reconnects against new details after the backend was respawned. */
  start(info: BackendInfo): void {
    this.info = info
    this.stopped = false
    this.backoffMs = INITIAL_BACKOFF_MS
    this.connect()
  }

  stop(): void {
    this.stopped = true
    if (this.retryTimer) {
      clearTimeout(this.retryTimer)
      this.retryTimer = null
    }
    this.request?.destroy()
    this.request = null
  }

  private connect(): void {
    if (this.stopped || !this.info) {
      return
    }
    this.request?.destroy()

    this.request = http.request(
      {
        host: '127.0.0.1',
        port: this.info.port,
        path: '/api/v1/events/stream',
        method: 'GET',
        headers: { Accept: 'text/event-stream', 'X-App-Token': this.info.token },
      },
      (response) => {
        if (response.statusCode !== 200) {
          response.resume()
          this.scheduleReconnect()
          return
        }
        // A live connection is proof the details are current, so the backoff can reset.
        this.backoffMs = INITIAL_BACKOFF_MS
        this.emit('connected')
        this.readEvents(response)
      },
    )

    this.request.on('error', () => this.scheduleReconnect())
    this.request.end()
  }

  /**
   * Parses the SSE framing: blank-line separated blocks of `event:` and `data:` lines.
   *
   * A chunk can split a block anywhere, so the tail is carried over rather than parsed on its own.
   */
  private readEvents(response: http.IncomingMessage): void {
    response.setEncoding('utf8')
    let buffer = ''

    response.on('data', (chunk: string) => {
      buffer += chunk
      let separator = buffer.indexOf('\n\n')
      while (separator !== -1) {
        this.handleBlock(buffer.slice(0, separator))
        buffer = buffer.slice(separator + 2)
        separator = buffer.indexOf('\n\n')
      }
    })

    response.on('end', () => this.scheduleReconnect())
    response.on('error', () => this.scheduleReconnect())
  }

  private handleBlock(block: string): void {
    let name = 'message'
    const dataLines: string[] = []

    for (const line of block.split('\n')) {
      if (line.startsWith(':')) {
        continue // A comment; the backend sends one on connect to flush the headers.
      }
      if (line.startsWith('event:')) {
        name = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }

    if (name !== 'reminder.fired' || dataLines.length === 0) {
      return
    }
    try {
      this.emit('reminder', JSON.parse(dataLines.join('\n')) as ReminderNotification)
    } catch {
      // A malformed frame is not worth tearing the connection down for; the reminder is already
      // recorded as fired in the database and will show up in the calendar either way.
      console.warn('[reminders] bỏ qua một sự kiện SSE không đọc được')
    }
  }

  private scheduleReconnect(): void {
    if (this.stopped || this.retryTimer) {
      return
    }
    const delay = this.backoffMs
    this.backoffMs = Math.min(this.backoffMs * 2, MAX_BACKOFF_MS)
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null
      this.connect()
    }, delay)
  }
}
