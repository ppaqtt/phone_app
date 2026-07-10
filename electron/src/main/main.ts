import { app, BrowserWindow, Menu, Tray, ipcMain, dialog } from 'electron'
import path from 'path'
import fs from 'fs'

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null

const createWindow = () => {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: '清笺',
    webPreferences: {
      preload: path.join(__dirname, '../preload/preload.js'),
      sandbox: false,
      contextIsolation: true,
      nodeIntegration: false
    },
    icon: path.join(__dirname, '../../build/icon.png')
  })

  const h5Path = path.join(__dirname, '../../src/renderer/h5/index.html')
  
  mainWindow.loadFile(h5Path)

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    return { action: 'deny' }
  })
}

const createTray = () => {
  const iconPath = path.join(__dirname, '../../build/icon.png')
  
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