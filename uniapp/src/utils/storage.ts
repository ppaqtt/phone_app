const STORAGE_KEYS = {
  NOTES: 'qingjian_notes',
  CATEGORIES: 'qingjian_categories',
  TAG_GROUPS: 'qingjian_tag_groups'
}

const isElectron = typeof window !== 'undefined' && !!(window as any).electronAPI

const memoryCache = new Map<string, any>()
let initialized = false
let initPromise: Promise<void> | null = null

const electronStore = (window as any)?.electronAPI?.dataStore

const loadFromElectron = async (): Promise<void> => {
  if (!electronStore) return
  const keys = Object.values(STORAGE_KEYS).concat([
    'qingjian_app_pin',
    'qingjian_app_locked'
  ])
  for (const key of keys) {
    try {
      const val = await electronStore.get(key, null)
      if (val !== null) {
        memoryCache.set(key, val)
      }
    } catch (e) {
      console.error('Load from electron store error:', key, e)
    }
  }
}

const migrateFromUniStorage = (): void => {
  const keys = Object.values(STORAGE_KEYS).concat([
    'qingjian_app_pin',
    'qingjian_app_locked'
  ])
  for (const key of keys) {
    try {
      const data = uni.getStorageSync(key)
      if (data) {
        memoryCache.set(key, JSON.parse(data))
      }
    } catch {
      // ignore
    }
  }
}

export const initStorage = async (): Promise<void> => {
  if (initialized) return
  if (initPromise) return initPromise

  initPromise = (async () => {
    if (isElectron && electronStore) {
      await loadFromElectron()
      const hasData = Array.from(memoryCache.keys()).some(k =>
        Object.values(STORAGE_KEYS).includes(k as any)
      )
      if (!hasData) {
        migrateFromUniStorage()
        await flushToElectron()
      }
    }
    initialized = true
  })()

  return initPromise
}

const flushToElectron = async (): Promise<void> => {
  if (!electronStore) return
  const keys = Object.values(STORAGE_KEYS).concat([
    'qingjian_app_pin',
    'qingjian_app_locked'
  ])
  for (const key of keys) {
    if (memoryCache.has(key)) {
      try {
        await electronStore.set(key, memoryCache.get(key))
      } catch (e) {
        console.error('Flush to electron store error:', key, e)
      }
    }
  }
}

let saveTimer: ReturnType<typeof setTimeout> | null = null
const scheduleSave = (): void => {
  if (!isElectron) return
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    flushToElectron()
    saveTimer = null
  }, 300)
}

export function getStorage<T>(key: string, defaultValue: T): T {
  if (isElectron) {
    if (memoryCache.has(key)) {
      return memoryCache.get(key) as T
    }
    return defaultValue
  }
  try {
    const data = uni.getStorageSync(key)
    return data ? JSON.parse(data) : defaultValue
  } catch {
    return defaultValue
  }
}

export function setStorage(key: string, value: any): void {
  if (isElectron) {
    memoryCache.set(key, value)
    scheduleSave()
    return
  }
  try {
    uni.setStorageSync(key, JSON.stringify(value))
  } catch (e) {
    console.error('Storage set error:', e)
    uni.showToast({ title: '存储失败', icon: 'none' })
  }
}

export function removeStorage(key: string): void {
  if (isElectron) {
    memoryCache.delete(key)
    if (electronStore) {
      electronStore.remove(key)
    }
    return
  }
  try {
    uni.removeStorageSync(key)
  } catch (e) {
    console.error('Storage remove error:', e)
  }
}

export function clearAllStorage(): void {
  if (isElectron) {
    memoryCache.clear()
    if (electronStore) {
      electronStore.clear()
    }
    return
  }
  try {
    uni.clearStorageSync()
  } catch (e) {
    console.error('Storage clear error:', e)
  }
}

export { STORAGE_KEYS, isElectron }
