import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  getAppDataPath: () => ipcRenderer.invoke('get-app-data-path'),
  showSaveDialog: () => ipcRenderer.invoke('show-save-dialog'),
  showOpenDialog: () => ipcRenderer.invoke('show-open-dialog'),
  writeFile: (filePath: string, data: string) => ipcRenderer.invoke('write-file', filePath, data),
  readFile: (filePath: string) => ipcRenderer.invoke('read-file', filePath)
})

declare global {
  interface Window {
    electronAPI: {
      getAppDataPath: () => Promise<string>
      showSaveDialog: () => Promise<any>
      showOpenDialog: () => Promise<any>
      writeFile: (filePath: string, data: string) => Promise<void>
      readFile: (filePath: string) => Promise<string>
    }
  }
}