/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Only used when the UI runs in a plain browser without the Electron shell. */
  readonly VITE_BACKEND_PORT?: string
  readonly VITE_APP_TOKEN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
