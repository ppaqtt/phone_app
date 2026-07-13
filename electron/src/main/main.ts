import { app, BrowserWindow, Menu, Tray, ipcMain, dialog } from 'electron'
import path from 'path'
import fs from 'fs'

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null

const isDev = !app.isPackaged

const getDataDir = (): string => {
  const dir = path.join(app.getPath('userData'), 'data')
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true })
  }
  return dir
}

const getDataFilePath = (key: string): string => {
  const safeKey = key.replace(/[^a-zA-Z0-9_-]/g, '_')
  return path.join(getDataDir(), `${safeKey}.json`)
}

const readDataFile = <T>(key: string, defaultValue: T): T => {
  try {
    const filePath = getDataFilePath(key)
    if (!fs.existsSync(filePath)) {
      return defaultValue
    }
    const data = fs.readFileSync(filePath, 'utf-8')
    return data ? JSON.parse(data) : defaultValue
  } catch (e) {
    console.error('Read data file error:', key, e)
    return defaultValue
  }
}

const writeDataFile = (key: string, value: any): void => {
  try {
    const filePath = getDataFilePath(key)
    const tmpPath = filePath + '.tmp'
    fs.writeFileSync(tmpPath, JSON.stringify(value), 'utf-8')
    fs.renameSync(tmpPath, filePath)
  } catch (e) {
    console.error('Write data file error:', key, e)
    throw e
  }
}

const getRendererPath = (): string => {
  if (isDev) {
    return path.join(__dirname, '../../src/renderer/h5')
  }
  return path.join(process.resourcesPath, 'renderer/h5')
}

const getIconPath = (): string => {
  if (isDev) {
    return path.join(__dirname, '../../build/icon.png')
  }
  return path.join(process.resourcesPath, 'icon.png')
}

const getPreloadPath = (): string => {
  if (isDev) {
    return path.join(__dirname, '../preload/preload.js')
  }
  return path.join(process.resourcesPath, 'preload/preload.js')
}

const createWindow = () => {
  const preloadPath = getPreloadPath()
  const iconPath = getIconPath()

  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: '清笺',
    webPreferences: {
      preload: preloadPath,
      sandbox: false,
      contextIsolation: true,
      nodeIntegration: false
    },
    icon: iconPath
  })

  const rendererDir = getRendererPath()
  const indexPath = path.join(rendererDir, 'index.html')
  
  if (fs.existsSync(indexPath)) {
    mainWindow.loadFile(indexPath)
  } else {
    mainWindow.loadURL(`data:text/html,<html><body><h1>加载失败</h1><p>找不到页面文件：${indexPath}</p><p>app.isPackaged: ${app.isPackaged}</p><p>resourcesPath: ${process.resourcesPath}</p></body></html>`)
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    return { action: 'deny' }
  })
}

const createTray = () => {
  const iconPath = getIconPath()
  
  if (!fs.existsSync(iconPath)) {
    return
  }

  tray = new Tray(iconPath)
  const contextMenu = Menu.buildFromTemplate([
    {
      label: '打开清笺',
      click: () => {
        if (mainWindow) {
          mainWindow.show()
        }
      }
    },
    {
      label: '退出',
      click: () => {
        app.quit()
      }
    }
  ])

  tray.setToolTip('清笺')
  tray.setContextMenu(contextMenu)
  
  tray.on('click', () => {
    if (mainWindow) {
      mainWindow.show()
    }
  })
}

const setupMenu = () => {
  const menu = Menu.buildFromTemplate([
    {
      label: '文件',
      submenu: [
        {
          label: '导出备份',
          click: async () => {
            const result = await dialog.showSaveDialog({
              title: '导出备份',
              defaultPath: `qingjian_backup_${new Date().toISOString().slice(0, 10)}.json`,
              filters: [
                { name: 'JSON 文件', extensions: ['json'] },
                { name: '所有文件', extensions: ['*'] }
              ]
            })
            
            if (!result.canceled && result.filePath) {
              ipcMain.emit('export-backup', result.filePath)
            }
          }
        },
        {
          label: '导入备份',
          click: async () => {
            const result = await dialog.showOpenDialog({
              title: '导入备份',
              filters: [
                { name: 'JSON 文件', extensions: ['json'] },
                { name: '所有文件', extensions: ['*'] }
              ]
            })
            
            if (!result.canceled && result.filePaths.length > 0) {
              ipcMain.emit('import-backup', result.filePaths[0])
            }
          }
        },
        { type: 'separator' },
        { role: 'quit' }
      ]
    },
    {
      label: '编辑',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'delete' },
        { role: 'selectAll' }
      ]
    },
    {
      label: '视图',
      submenu: [
        { role: 'reload' },
        { role: 'forceReload' },
        { type: 'separator' },
        { role: 'toggleDevTools' },
        { type: 'separator' },
        { role: 'togglefullscreen' }
      ]
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '关于清笺',
          click: () => {
            dialog.showMessageBox({
              title: '关于清笺',
              message: '清笺 v1.0.0\n\n完全本地化的笔记应用\n所有数据仅存储在本地',
              buttons: ['确定']
            })
          }
        }
      ]
    }
  ])

  Menu.setApplicationMenu(menu)
}

app.whenReady().then(() => {
  createWindow()
  createTray()
  setupMenu()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

ipcMain.handle('get-app-data-path', () => {
  return app.getPath('userData')
})

ipcMain.handle('show-save-dialog', async () => {
  const result = await dialog.showSaveDialog({
    title: '导出备份',
    defaultPath: `qingjian_backup_${new Date().toISOString().slice(0, 10)}.json`,
    filters: [
      { name: 'JSON 文件', extensions: ['json'] }
    ]
  })
  return result
})

ipcMain.handle('show-open-dialog', async () => {
  const result = await dialog.showOpenDialog({
    title: '导入备份',
    filters: [
      { name: 'JSON 文件', extensions: ['json'] }
    ]
  })
  return result
})

ipcMain.handle('write-file', async (_, filePath: string, data: string) => {
  fs.writeFileSync(filePath, data, 'utf-8')
})

ipcMain.handle('read-file', async (_, filePath: string) => {
  return fs.readFileSync(filePath, 'utf-8')
})

ipcMain.handle('data-store-get', async (_, key: string, defaultValue: any) => {
  return readDataFile(key, defaultValue)
})

ipcMain.handle('data-store-set', async (_, key: string, value: any) => {
  writeDataFile(key, value)
  return true
})

ipcMain.handle('data-store-remove', async (_, key: string) => {
  try {
    const filePath = getDataFilePath(key)
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath)
    }
    return true
  } catch (e) {
    console.error('Remove data file error:', key, e)
    return false
  }
})

ipcMain.handle('data-store-clear', async () => {
  try {
    const dir = getDataDir()
    const files = fs.readdirSync(dir)
    files.forEach(file => {
      if (file.endsWith('.json')) {
        fs.unlinkSync(path.join(dir, file))
      }
    })
    return true
  } catch (e) {
    console.error('Clear data store error:', e)
    return false
  }
})