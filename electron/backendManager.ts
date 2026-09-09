import { ChildProcess, spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { randomBytes } from 'node:crypto'
import { existsSync } from 'node:fs'
import path from 'node:path'

/**
 * Owns the lifetime of the Java backend process (04-ARCHITECTURE.md §5).
 *
 * Responsibilities: pick a free high port, mint the per-launch shared token, spawn the jar,
 * wait for /actuator/health, and bring it back up if it dies (NFR-REL-02).
 */

export interface BackendInfo {
  port: number
  token: string
}

export interface BackendManagerOptions {
  /** Absolute path to the packaged backend jar. */
  jarPath: string
  /** Directory for lifehub.db and backups/. */
  dataDir: string
  /** Directory for rotated log files. */
  logDir: string
  /**
   * Supplies the AI API key at spawn time (FR-AI-09).
   *
   * A function rather than a value: the user can paste a key into Settings long after launch, and
   * the next spawn has to pick up whatever is current rather than what was there at construction.
   */
  getApiKey?: () => string | null
  /** Called after an automatic restart, so the renderer can retarget the new port. */
  onRestarted?: (info: BackendInfo) => void
  /** Called when the backend is gone for good and the user has to decide what to do. */
  onFatal?: (reason: string) => void
}

/** NFR-SEC-03: the dynamic/private port range, chosen at random per launch. */
const PORT_RANGE_START = 49152
const PORT_RANGE_END = 65535

const HEALTH_POLL_INTERVAL_MS = 300
const HEALTH_TIMEOUT_MS = 30_000
const MAX_RESTART_ATTEMPTS = 3

/** How long a deliberate restart waits for the old process to die before giving up on it. */
const EXIT_TIMEOUT_MS = 5_000

export class BackendManager {
  private child: ChildProcess | null = null
  private info: BackendInfo | null = null
  private restartAttempts = 0
  private shuttingDown = false

  constructor(private readonly options: BackendManagerOptions) {}

  /** Starts the backend and resolves once /actuator/health reports UP. */
  async start(): Promise<BackendInfo> {
    if (!existsSync(this.options.jarPath)) {
      throw new Error(
        `Không tìm thấy backend jar tại ${this.options.jarPath}. ` +
          'Chạy `npm run backend:build` trước khi mở ứng dụng.',
      )
    }

    const port = await findFreePort()
    const token = randomBytes(32).toString('base64url')
    this.info = { port, token }

    this.spawnProcess(this.info)
    await this.waitUntilHealthy(port)
    this.restartAttempts = 0
    return this.info
  }

  getInfo(): BackendInfo | null {
    return this.info
  }

  /**
   * Restarts the backend on a fresh port and token, resolving once it is healthy again.
   *
   * The Settings screen needs this: the API key and the display timezone are both read once at
   * startup, so changing either only takes effect on the next process.
   *
   * The old process is awaited, not merely signalled. `kill()` returns as soon as the signal is
   * delivered and the `exit` event arrives a tick later — so lowering `shuttingDown` in the same
   * tick would let `handleExit` see a non-zero exit code with the flag already down and treat the
   * deliberate kill as a crash. It would then spawn a *second* backend under the restart policy,
   * leaving two JVMs writing the same SQLite file.
   */
  async restartNow(): Promise<BackendInfo> {
    await this.stopAndWait()
    this.shuttingDown = false
    this.restartAttempts = 0

    const info = await this.start()
    this.options.onRestarted?.(info)
    return info
  }

  /** Stops the backend without triggering the restart policy. */
  stop(): void {
    this.shuttingDown = true
    this.child?.kill()
    this.child = null
  }

  /**
   * Kills the running backend and resolves once the OS confirms it is gone.
   *
   * `shuttingDown` stays up for the whole wait, which is what keeps `handleExit` out of the restart
   * policy. If the process ignores the signal, the wait is abandoned after
   * {@link EXIT_TIMEOUT_MS} rather than leaving the Settings screen spinning forever: a stuck
   * backend is bad, and a user who can never save an API key again is worse.
   */
  private stopAndWait(): Promise<void> {
    const child = this.child
    this.shuttingDown = true
    this.child = null

    // Already gone, or never started: no exit event is coming.
    if (child === null || child.exitCode !== null || child.signalCode !== null) {
      return Promise.resolve()
    }

    return new Promise<void>((resolve) => {
      let timer: NodeJS.Timeout | undefined

      const finish = () => {
        if (timer !== undefined) {
          clearTimeout(timer)
        }
        child.off('exit', finish)
        resolve()
      }

      timer = setTimeout(() => {
        console.warn(
          `[backend] không thoát sau ${EXIT_TIMEOUT_MS} ms, buộc dừng rồi khởi động lại`,
        )
        child.kill('SIGKILL')
        finish()
      }, EXIT_TIMEOUT_MS)

      child.once('exit', finish)
      child.kill()
    })
  }

  /** The child's environment, with the API key added when the user has configured one. */
  private environment(): NodeJS.ProcessEnv {
    const apiKey = this.options.getApiKey?.() ?? null
    return apiKey === null || apiKey === '' ? { ...process.env } : { ...process.env, AI_API_KEY: apiKey }
  }

  private spawnProcess(info: BackendInfo): void {
    const child = spawn(
      resolveJavaCommand(),
      [
        '-jar',
        this.options.jarPath,
        `--server.port=${info.port}`,
        `--app.token=${info.token}`,
        `--app.data-dir=${this.options.dataDir}`,
        `--app.log-dir=${this.options.logDir}`,
      ],
      {
        // The key travels in the child's environment and nowhere else: never on the command line,
        // where any process listing would show it, and never in a file (NFR-SEC-01).
        env: this.environment(),
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true,
      },
    )

    child.stdout?.on('data', (chunk: Buffer) => process.stdout.write(`[backend] ${chunk}`))
    child.stderr?.on('data', (chunk: Buffer) => process.stderr.write(`[backend] ${chunk}`))
    child.on('exit', (code) => this.handleExit(code))

    this.child = child
  }

  /**
   * Restart policy from 04-ARCHITECTURE.md §5: up to three attempts with 2^n second backoff,
   * then hand the decision to the user.
   */
  private handleExit(code: number | null): void {
    if (this.shuttingDown || code === 0) {
      return
    }

    this.restartAttempts += 1
    if (this.restartAttempts > MAX_RESTART_ATTEMPTS) {
      this.options.onFatal?.(
        `Dịch vụ nền dừng đột ngột ${MAX_RESTART_ATTEMPTS} lần liên tiếp (mã thoát ${code}).`,
      )
      return
    }

    const delayMs = 2 ** this.restartAttempts * 1000
    console.warn(
      `[backend] exited with code ${code}, restart attempt ${this.restartAttempts}/${MAX_RESTART_ATTEMPTS} in ${delayMs}ms`,
    )

    setTimeout(() => {
      void this.restart()
    }, delayMs)
  }

  private async restart(): Promise<void> {
    try {
      // A fresh port and token per spawn: the old port may still be in TIME_WAIT, and a
      // token that outlived the process it authenticated has no reason to stay valid.
      const port = await findFreePort()
      const token = randomBytes(32).toString('base64url')
      this.info = { port, token }

      this.spawnProcess(this.info)
      await this.waitUntilHealthy(port)
      this.options.onRestarted?.(this.info)
    } catch (error) {
      this.options.onFatal?.(
        `Không khởi động lại được dịch vụ nền: ${error instanceof Error ? error.message : String(error)}`,
      )
    }
  }

  private async waitUntilHealthy(port: number): Promise<void> {
    const deadline = Date.now() + HEALTH_TIMEOUT_MS

    while (Date.now() < deadline) {
      if (await isHealthy(port)) {
        return
      }
      await delay(HEALTH_POLL_INTERVAL_MS)
    }

    throw new Error(`Dịch vụ nền không phản hồi sau ${HEALTH_TIMEOUT_MS / 1000} giây.`)
  }
}

/** Health is a plain liveness probe and carries no token (see AppTokenFilter). */
async function isHealthy(port: number): Promise<boolean> {
  try {
    const response = await fetch(`http://127.0.0.1:${port}/actuator/health`, {
      signal: AbortSignal.timeout(1000),
    })
    if (!response.ok) {
      return false
    }
    const body = (await response.json()) as { status?: string }
    return body.status === 'UP'
  } catch {
    return false
  }
}

/**
 * Binds port 0 so the OS hands back a free port, then reuses it.
 *
 * There is an unavoidable race between releasing the port and the JVM binding it. It is
 * harmless here: if the port were taken in between, the health check fails and the restart
 * policy picks a different one.
 */
function findFreePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer()
    server.unref()
    server.on('error', reject)
    server.listen({ port: 0, host: '127.0.0.1' }, () => {
      const address = server.address()
      if (address === null || typeof address === 'string') {
        server.close()
        reject(new Error('Không lấy được cổng trống cho dịch vụ nền.'))
        return
      }
      const { port } = address
      server.close(() =>
        resolve(port >= PORT_RANGE_START && port <= PORT_RANGE_END ? port : randomPortInRange()),
      )
    })
  })
}

function randomPortInRange(): number {
  return PORT_RANGE_START + Math.floor(Math.random() * (PORT_RANGE_END - PORT_RANGE_START))
}

/** Prefers the bundled JRE in a packaged app (Phase 6), falls back to JAVA_HOME then PATH. */
function resolveJavaCommand(): string {
  const executable = process.platform === 'win32' ? 'java.exe' : 'java'

  const bundled = process.env.LIFEHUB_JAVA_HOME
  if (bundled) {
    return path.join(bundled, 'bin', executable)
  }
  if (process.env.JAVA_HOME) {
    const candidate = path.join(process.env.JAVA_HOME, 'bin', executable)
    if (existsSync(candidate)) {
      return candidate
    }
  }
  return 'java'
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
