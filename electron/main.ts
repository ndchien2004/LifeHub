import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron'
import path from 'node:path'
import { BackendManager, type BackendInfo } from './backendManager'

/**
 * Electron main process — application entry point.
 *
 * Startup order follows 04-ARCHITECTURE.md §5: show a splash immediately, start the backend
 * and poll its health, and only swap in the real window once it answers. The user therefore
 * never sees an empty frame or a failed first request.
 */

const isDev = !app.isPackaged
const DEV_SERVER_URL = 'http://127.0.0.1:5173'

/**
 * In development everything lives in the repository so the files are easy to inspect.
 * A packaged build writes to userData instead, which survives reinstalls and is writable
 * on a normal user account (Phase 6).
 */
const projectRoot = path.resolve(__dirname, '..', '..')
const dataDir = isDev ? path.join(projectRoot, 'data') : path.join(app.getPath('userData'), 'data')
const logDir = isDev ? path.join(projectRoot, 'logs') : path.join(app.getPath('userData'), 'logs')
const jarPath = isDev
  ? path.join(projectRoot, 'backend', 'target', 'lifehub-backend.jar')
  : path.join(process.resourcesPath, 'backend', 'lifehub-backend.jar')

let mainWindow: BrowserWindow | null = null
let splashWindow: BrowserWindow | null = null

const backend = new BackendManager({
  jarPath,
  dataDir,
  logDir,
  onRestarted: (info) => {
    mainWindow?.webContents.send('app:backend-restarted', info)
    mainWindow?.webContents.reload()
  },
  onFatal: (reason) => void showFatalError(reason),
})

function createSplashWindow(): BrowserWindow {
  const splash = new BrowserWindow({
    width: 400,
    height: 260,
    frame: false,
    resizable: false,
    center: true,
    show: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false },
  })
  void splash.loadFile(path.join(__dirname, '..', 'splash.html'))
  return splash
}

function createMainWindow(): BrowserWindow {
  const window = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    show: false,
    title: 'LifeHub',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  // External links open in the real browser, never inside the app frame.
  window.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })

  if (isDev) {
    void window.loadURL(DEV_SERVER_URL)
  } else {
    void window.loadFile(path.join(projectRoot, 'frontend', 'dist', 'index.html'))
  }

  return window
}

async function bootstrap(): Promise<void> {
  splashWindow = createSplashWindow()

  let info: BackendInfo
  try {
    info = await backend.start()
  } catch (error) {
    splashWindow?.destroy()
    splashWindow = null
    await showFatalError(error instanceof Error ? error.message : String(error))
    return
  }

  console.log(`[main] backend ready on 127.0.0.1:${info.port}`)

  mainWindow = createMainWindow()
  mainWindow.once('ready-to-show', () => {
    splashWindow?.destroy()
    splashWindow = null
    mainWindow?.show()
  })
  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

/** Offers the two things that actually help when startup fails: retry, or read the log. */
async function showFatalError(reason: string): Promise<void> {
  const { response } = await dialog.showMessageBox({
    type: 'error',
    title: 'LifeHub — không khởi động được',
    message: 'Không khởi động được dịch vụ nền',
    detail: `${reason}\n\nBạn có thể thử lại, hoặc mở thư mục log để xem chi tiết.`,
    buttons: ['Thử lại', 'Mở thư mục log', 'Thoát'],
    defaultId: 0,
    cancelId: 2,
  })

  if (response === 0) {
    await bootstrap()
    return
  }
  if (response === 1) {
    await shell.openPath(logDir)
  }
  app.quit()
}

// IPC surface for Phase 0 (04-ARCHITECTURE.md §6.3).
ipcMain.handle('app:get-backend-info', () => {
  const info = backend.getInfo()
  if (!info) {
    throw new Error('Dịch vụ nền chưa sẵn sàng.')
  }
  return info
})

// One instance only: two processes writing the same SQLite file is asking for trouble.
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) {
        mainWindow.restore()
      }
      mainWindow.focus()
    }
  })

  void app.whenReady().then(bootstrap)
}

app.on('window-all-closed', () => {
  // Phase 2 replaces this with minimise-to-tray (FR-SYS-07).
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    void bootstrap()
  }
})

app.on('before-quit', () => backend.stop())
