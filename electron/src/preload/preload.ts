import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  getAppDataPath: () => ipcRenderer.invoke('get-app-data-path'),
  showSaveDialog: () => ipcRenderer.invoke('show-save-dialog'),
  showOpenDialog: () => ipcRenderer.invoke('show-open-dialog'),
  writeFile: (filePath: string, data: string) => ipcRenderer.invoke('write-file', filePath, data),
  readFile: (filePath: string) => ipcRenderer.invoke('read-file', filePath),
  dataStore: {
    get: <T>(key: string, defaultValue: T): Promise<T> =>
      ipcRenderer.invoke('data-store-get', key, defaultValue),
    set: (key: string, value: any): Promise<boolean> =>
      ipcRenderer.invoke('data-store-set', key, value),
    remove: (key: string): Promise<boolean> =>
      ipcRenderer.invoke('data-store-remove', key),
    clear: (): Promise<boolean> =>
      ipcRenderer.invoke('data-store-clear')
  }
})

declare global {
  interface Window {
    electronAPI: {
      getAppDataPath: () => Promise<string>
      showSaveDialog: () => Promise<any>
      showOpenDialog: () => Promise<any>
      writeFile: (filePath: string, data: string) => Promise<void>
      readFile: (filePath: string) => Promise<string>
      dataStore: {
        get: <T>(key: string, defaultValue: T) => Promise<T>
        set: (key: string, value: any) => Promise<boolean>
        remove: (key: string) => Promise<boolean>
        clear: () => Promise<boolean>
      }
    }
  }
}