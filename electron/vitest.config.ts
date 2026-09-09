import { defineConfig } from 'vitest/config'

/**
 * Test config for the Electron main process.
 *
 * Separate from the renderer's config because nothing here runs in a browser: these are Node
 * modules that spawn processes and open sockets, so they need the `node` environment and no jsdom.
 */
export default defineConfig({
  test: {
    environment: 'node',
    globals: false,
    // Relative to the npm script's working directory, which is always the repository root.
    include: ['electron/*.test.ts'],
  },
})
