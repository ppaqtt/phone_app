import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  getAppDataPath: () => ipcRenderer.invoke('get-app-data-path'),
  showSaveDialog: () => ipcRenderer.invoke('show-save-dialog'),
  showOpenDialog: () => ipcRenderer.invoke('show-open-dialog')
})

declare global {
  interface Window {
    electronAPI: {
      getAppDataPath: () => Promise<string>
      showSaveDialog: () => Promise<any>
      showOpenDialog: () => Promise<any>
    }
  }
}