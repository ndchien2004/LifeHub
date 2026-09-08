import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  // Relative base: in production Electron loads the bundle from the file system,
  // where absolute asset paths would resolve against the drive root.
  base: './',
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    // Pin to IPv4 loopback. Vite otherwise binds [::1] only on Windows, which leaves
    // every 127.0.0.1 consumer — wait-on, the Electron loader, the CSP connect-src —
    // unable to reach it.
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
