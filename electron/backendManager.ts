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

  /** Stops the backend without triggering the restart policy. */
  stop(): void {
    this.shuttingDown = true
    this.child?.kill()
    this.child = null
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
        // AI_API_KEY is added here in Phase 4; it is never written to disk (NFR-SEC-01).
        env: { ...process.env },
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
