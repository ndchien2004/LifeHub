import '@testing-library/jest-dom/vitest'

/*
 * Environment gaps in jsdom that the app relies on in a real Electron renderer.
 * These are test-harness shims only; production code uses the platform APIs directly.
 */

// jsdom 25 under Node 26 ships no Web Storage implementation, so the theme store — which
// persists the user's choice (FR-SYS-06) — would have nothing to write to.
if (typeof window.localStorage === 'undefined') {
  const store = new Map<string, string>()
  const localStorageStub: Storage = {
    get length() {
      return store.size
    },
    clear: () => store.clear(),
    getItem: (key) => store.get(key) ?? null,
    key: (index) => [...store.keys()][index] ?? null,
    removeItem: (key) => void store.delete(key),
    setItem: (key, value) => void store.set(key, String(value)),
  }
  Object.defineProperty(window, 'localStorage', { value: localStorageStub, configurable: true })
  Object.defineProperty(globalThis, 'localStorage', { value: localStorageStub, configurable: true })
}

// The theme resolver asks the OS for its colour preference.
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}
