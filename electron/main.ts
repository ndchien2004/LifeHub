import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron'
import path from 'node:path'
import { BackendManager, type BackendInfo } from './backendManager'
import { ReminderNotifier } from './reminderNotifier'
import { ReminderStream, type ReminderNotification } from './reminderStream'
import { createTray, type TrayController } from './tray'
import {
  clearApiKey,
  getApiKey,
  hasApiKey,
  isEncryptionAvailable,
  setApiKey,
} from './secureStore'

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
let tray: TrayController | null = null

const reminderStream = new ReminderStream()

const notifier = new ReminderNotifier({
  onActivate: (reminder) => {
    showMainWindow()
    mainWindow?.webContents.send('app:navigate', {
      route: reminder.refType === 'TASK' ? 'tasks' : 'calendar',
      id: reminder.refId,
    })
  },
  onFired: (reminder, shownNatively) => {
    // The renderer always hears about it: it refreshes the calendar, and shows an in-app toast
    // when the OS refused to (UC-04 exception E1).
    mainWindow?.webContents.send('app:reminder-fired', { reminder, shownNatively })
  },
})

const backend = new BackendManager({
  jarPath,
  dataDir,
  logDir,
  // Read at every spawn so a key pasted into Settings takes effect on the next restart.
  getApiKey,
  onRestarted: (info) => {
    // A restart means a new port and a new token, so the push channel has to be retargeted too.
    notifier.setBackendInfo(info)
    reminderStream.start(info)
    mainWindow?.webContents.send('app:backend-restarted', info)
    mainWindow?.webContents.reload()
  },
  onFatal: (reason) => void showFatalError(reason),
})

reminderStream.on('reminder', (reminder: ReminderNotification) => notifier.show(reminder))

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

  notifier.setBackendInfo(info)
  reminderStream.start(info)

  mainWindow = createMainWindow()
  mainWindow.once('ready-to-show', () => {
    splashWindow?.destroy()
    splashWindow = null
    mainWindow?.show()
  })

  // FR-SYS-07: closing the window hides it instead of quitting, so reminders keep arriving.
  // Only the tray menu and an explicit quit actually end the process.
  mainWindow.on('close', (event) => {
    if (!tray?.isQuitting()) {
      event.preventDefault()
      mainWindow?.hide()
    }
  })
  mainWindow.on('closed', () => {
    mainWindow = null
  })

  tray ??= createTray(() => mainWindow)
}

function showMainWindow(): void {
  if (!mainWindow) {
    return
  }
  if (mainWindow.isMinimized()) {
    mainWindow.restore()
  }
  mainWindow.show()
  mainWindow.focus()
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

// IPC surface (04-ARCHITECTURE.md §6.3).
ipcMain.handle('app:get-backend-info', () => {
  const info = backend.getInfo()
  if (!info) {
    throw new Error('Dịch vụ nền chưa sẵn sàng.')
  }
  return info
})

/**
 * Whether the OS will display notifications for this app (UC-04 exception E1).
 *
 * Electron exposes support, not the user's permission setting, so a "granted" answer means the
 * channel exists rather than that a toast is guaranteed to appear. The renderer treats it as the
 * signal to stop duplicating every reminder as an in-app toast, which is the decision it actually
 * needs to make.
 */
ipcMain.handle('notification:permission', () => (notifier.isSupported() ? 'granted' : 'denied'))

/**
 * Stores the AI API key in the OS credential store and restarts the backend so it picks it up.
 *
 * The key reaches the backend only through the environment of the process it spawns, so a new key
 * means a new process — there is no way to hand it to a running JVM, and inventing one would mean
 * an endpoint that accepts a secret over HTTP.
 */
ipcMain.handle('secure:set-api-key', async (_event, key: unknown) => {
  if (typeof key !== 'string') {
    throw new Error('API key phải là chuỗi.')
  }

  const { persisted } = setApiKey(key)
  await backend.restartNow()
  return { persisted, hasKey: hasApiKey() }
})

ipcMain.handle('secure:has-api-key', () => ({
  hasKey: hasApiKey(),
  encryptionAvailable: isEncryptionAvailable(),
}))

ipcMain.handle('secure:clear-api-key', async () => {
  clearApiKey()
  await backend.restartNow()
  return { hasKey: false }
})

/** Applies a setting the backend only reads at startup, such as the display timezone. */
ipcMain.handle('app:restart-backend', () => backend.restartNow())

// One instance only: two processes writing the same SQLite file is asking for trouble.
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  // Launching again while it sits in the tray should bring the window back, not start a second app.
  app.on('second-instance', showMainWindow)

  void app.whenReady().then(bootstrap)
}

// Deliberately not quitting here: the window is hidden to the tray rather than destroyed
// (FR-SYS-07), and quitting on the last window closing would defeat that on every platform.

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    void bootstrap()
  } else {
    showMainWindow()
  }
})

app.on('before-quit', () => {
  // Covers a quit the app did not start - Cmd+Q, or the OS shutting down. Without this the close
  // handler would keep hiding the window and the process would never exit.
  tray?.markQuitting()
  reminderStream.stop()
  tray?.destroy()
  tray = null
  backend.stop()
})
