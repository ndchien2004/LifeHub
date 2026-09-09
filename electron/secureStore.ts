import { app, safeStorage } from 'electron'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'

/**
 * Keeps the AI API key in the operating system credential store (FR-AI-09, NFR-SEC-01).
 *
 * Electron's safeStorage encrypts with a key held by the OS keychain — DPAPI on Windows, Keychain
 * on macOS, libsecret on Linux — so the blob on disk is unreadable to another user account and to
 * anyone who copies the file elsewhere. Nothing here ever writes the key in the clear, and the
 * ciphertext deliberately lives outside `data/`, so a database backup can never carry it along.
 *
 * When the platform has no keychain available (a Linux box with no secret service running),
 * persisting is refused rather than downgraded to a plaintext file. The key still works for the
 * session — it is held in memory — and the caller is told it will not survive a restart, which is a
 * choice the user can make with the facts in front of them.
 */

const KEY_FILE = 'ai-api-key.bin'

/** Held for this run so an unpersistable key still works until the app closes. */
let inMemoryKey: string | null = null

function keyPath(): string {
  return path.join(app.getPath('userData'), 'secure', KEY_FILE)
}

/** Whether the OS will actually encrypt for us. */
export function isEncryptionAvailable(): boolean {
  try {
    return safeStorage.isEncryptionAvailable()
  } catch {
    return false
  }
}

export interface StoreResult {
  /** False when the key works for this session but could not be written to disk. */
  persisted: boolean
}

/** Stores the key, encrypted. An empty value clears it. */
export function setApiKey(rawKey: string): StoreResult {
  const key = rawKey.trim()
  if (key === '') {
    clearApiKey()
    return { persisted: true }
  }

  inMemoryKey = key

  if (!isEncryptionAvailable()) {
    return { persisted: false }
  }

  const file = keyPath()
  mkdirSync(path.dirname(file), { recursive: true })
  writeFileSync(file, safeStorage.encryptString(key), { mode: 0o600 })
  return { persisted: true }
}

/** The stored key, or null when none has been set. */
export function getApiKey(): string | null {
  if (inMemoryKey !== null) {
    return inMemoryKey
  }

  const file = keyPath()
  if (!existsSync(file) || !isEncryptionAvailable()) {
    return null
  }

  try {
    const decrypted = safeStorage.decryptString(readFileSync(file))
    inMemoryKey = decrypted
    return decrypted
  } catch (error) {
    // A blob written under a different OS user or a reset keychain cannot be read back. Losing a
    // key the user can paste again is far better than refusing to start.
    console.warn('[secure] không giải mã được API key đã lưu:', error)
    return null
  }
}

export function hasApiKey(): boolean {
  return getApiKey() !== null
}

export function clearApiKey(): void {
  inMemoryKey = null
  const file = keyPath()
  if (existsSync(file)) {
    rmSync(file)
  }
}
