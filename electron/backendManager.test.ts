import { EventEmitter } from 'node:events'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Lifecycle tests for the backend process manager.
 *
 * <p>The one this file exists for is the restart path. Saving an API key, clearing one, and
 * changing the display timezone all call {@link BackendManager.restartNow}, and if that leaves the
 * old JVM running there are briefly two processes writing {@code data/lifehub.db} — the one failure
 * mode SQLite handles worst.
 *
 * <p>Everything the real manager touches is faked: {@code spawn}, the free-port probe, the health
 * check and the clock. That keeps the test deterministic and stops it from launching a real JVM.
 */

/** Every child ever spawned, in order, so a test can count what is still alive. */
let children: FakeChild[] = []

const spawnMock = vi.fn(() => {
  const child = new FakeChild()
  children.push(child)
  return child
})

vi.mock('node:child_process', () => ({
  spawn: (...args: unknown[]) => spawnMock(...(args as [])),
}))

vi.mock('node:fs', () => ({ existsSync: () => true }))

vi.mock('node:net', () => ({
  createServer: () => new FakeServer(),
}))

/**
 * Stands in for a spawned JVM.
 *
 * <p>The important detail is that {@code kill()} does not emit {@code exit} synchronously - it
 * schedules it, exactly as Node does. A manager that assumes the process is gone the moment
 * {@code kill()} returns passes against a synchronous fake and fails in production.
 *
 * <p>Exit code 1 rather than 0 is also deliberate: on Windows {@code kill()} is
 * {@code TerminateProcess}, and the terminated process reports a non-zero code. That is what makes
 * the restart policy mistake a deliberate kill for a crash.
 */
class FakeChild extends EventEmitter {
  killed = false
  exitCode: number | null = null
  signalCode: string | null = null
  readonly stdout = new EventEmitter()
  readonly stderr = new EventEmitter()

  /** Set by a test that wants a process which ignores the signal, like a wedged JVM. */
  ignoresKill = false

  kill(): boolean {
    this.killed = true
    if (this.ignoresKill) {
      return true
    }
    setTimeout(() => this.exit(1), 0)
    return true
  }

  /** Emits an exit the way the OS would, updating the state the manager reads. */
  exit(code: number): void {
    if (this.exitCode !== null) {
      return
    }
    this.exitCode = code
    this.emit('exit', code)
  }

  get alive(): boolean {
    return this.exitCode === null
  }
}

/** Stands in for the socket the manager binds to have the OS hand back a free port. */
class FakeServer {
  private static nextPort = 50_000

  private port = FakeServer.nextPort++

  unref(): void {}

  on(): void {}

  listen(_options: unknown, callback: () => void): void {
    callback()
  }

  address(): { port: number } {
    return { port: this.port }
  }

  close(callback?: () => void): void {
    callback?.()
  }
}

function aliveChildren(): FakeChild[] {
  return children.filter((child) => child.alive)
}

async function createManager(overrides: Record<string, unknown> = {}) {
  const { BackendManager } = await import('./backendManager')
  return new BackendManager({
    jarPath: 'C:/fake/lifehub-backend.jar',
    dataDir: 'C:/fake/data',
    logDir: 'C:/fake/logs',
    ...overrides,
  })
}

/** Runs pending timers and microtasks until {@code promise} settles. */
async function settle<T>(promise: Promise<T>): Promise<T> {
  const result = promise.then(
    (value) => ({ ok: true as const, value }),
    (error) => ({ ok: false as const, error }),
  )
  await vi.advanceTimersByTimeAsync(50)
  const outcome = await result
  if (!outcome.ok) {
    throw outcome.error
  }
  return outcome.value
}

describe('BackendManager', () => {
  beforeEach(() => {
    children = []
    spawnMock.mockClear()
    vi.useFakeTimers()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }),
    )
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('start() spawn đúng một tiến trình và chờ health check', async () => {
    const manager = await createManager()

    const info = await settle(manager.start())

    expect(spawnMock).toHaveBeenCalledTimes(1)
    expect(info.port).toBeGreaterThan(0)
    expect(info.token).toHaveLength(43)
  })

  it('API key được truyền qua biến môi trường của tiến trình con, không qua tham số dòng lệnh', async () => {
    const manager = await createManager({ getApiKey: () => 'sk-ant-secret' })

    await settle(manager.start())

    const [, args, options] = spawnMock.mock.calls[0] as unknown as [
      string,
      string[],
      { env: NodeJS.ProcessEnv },
    ]
    expect(options.env.AI_API_KEY).toBe('sk-ant-secret')
    expect(args.join(' ')).not.toContain('sk-ant-secret')
  })

  it('restartNow() chỉ để lại đúng MỘT tiến trình con', async () => {
    const manager = await createManager()
    await settle(manager.start())

    await settle(manager.restartNow())

    // The restart policy backs off 2^1 seconds before its first retry; go well past that so a
    // stray restart would have fired by now.
    await vi.advanceTimersByTimeAsync(10_000)

    expect(spawnMock).toHaveBeenCalledTimes(2)
    expect(aliveChildren()).toHaveLength(1)
    expect(children[0]!.killed).toBe(true)
  })

  it('restartNow() không coi lần kill có chủ đích là một lần crash', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const manager = await createManager()
    await settle(manager.start())

    await settle(manager.restartNow())
    await vi.advanceTimersByTimeAsync(10_000)

    expect(warn).not.toHaveBeenCalledWith(expect.stringContaining('restart attempt'))
    warn.mockRestore()
  })

  it('restartNow() cấp port và token mới, rồi báo cho renderer', async () => {
    const onRestarted = vi.fn()
    const manager = await createManager({ onRestarted })
    const first = await settle(manager.start())

    const second = await settle(manager.restartNow())

    expect(second.port).not.toBe(first.port)
    expect(second.token).not.toBe(first.token)
    expect(onRestarted).toHaveBeenCalledTimes(1)
    expect(onRestarted).toHaveBeenCalledWith(second)
  })

  it('restartNow() không treo khi tiến trình cũ phớt lờ tín hiệu dừng', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const manager = await createManager()
    await settle(manager.start())
    children[0]!.ignoresKill = true

    const restart = manager.restartNow()
    // Past the 5 second abandon-the-wait deadline.
    await vi.advanceTimersByTimeAsync(6_000)
    const info = await restart

    expect(info.port).toBeGreaterThan(0)
    expect(spawnMock).toHaveBeenCalledTimes(2)
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('không thoát'))
    warn.mockRestore()
  })

  it('NFR-REL-02 — backend chết ngoài ý muốn thì vẫn được tự khởi động lại', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    const manager = await createManager()
    await settle(manager.start())

    // A crash: nobody asked for this one.
    children[0]!.exit(1)
    await vi.advanceTimersByTimeAsync(3_000)

    expect(spawnMock).toHaveBeenCalledTimes(2)
    vi.restoreAllMocks()
  })

  it('stop() không kích hoạt chính sách khởi động lại', async () => {
    const manager = await createManager()
    await settle(manager.start())

    manager.stop()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(spawnMock).toHaveBeenCalledTimes(1)
  })
})
