import { vi } from 'vitest'
import type { LifeHubBridge } from '@/shared/lib/electron'

/**
 * A stand-in for the Electron preload bridge.
 *
 * Every test that renders something touching `window.lifehub` needs the whole surface, not the one
 * method it happens to call - a partial object type-checks nowhere and drifts every time the bridge
 * grows. Overrides let a test replace just the part it is asserting on.
 */
export function stubLifeHubBridge(overrides: Partial<LifeHubBridge> = {}): LifeHubBridge {
  const bridge: LifeHubBridge = {
    getBackendInfo: vi.fn().mockResolvedValue({ port: 51234, token: 'secret-token' }),
    onBackendRestarted: vi.fn().mockReturnValue(() => {}),
    getNotificationPermission: vi.fn().mockResolvedValue('granted'),
    onReminderFired: vi.fn().mockReturnValue(() => {}),
    onNavigate: vi.fn().mockReturnValue(() => {}),
    setApiKey: vi.fn().mockResolvedValue({ persisted: true, hasKey: true }),
    hasApiKey: vi.fn().mockResolvedValue({ hasKey: false, encryptionAvailable: true }),
    clearApiKey: vi.fn().mockResolvedValue({ hasKey: false }),
    restartBackend: vi.fn().mockResolvedValue({ port: 51234, token: 'secret-token' }),
    ...overrides,
  }
  window.lifehub = bridge
  return bridge
}
