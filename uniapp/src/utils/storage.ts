const STORAGE_KEYS = {
  NOTES: 'qingjian_notes',
  CATEGORIES: 'qingjian_categories',
  TAG_GROUPS: 'qingjian_tag_groups'
}

export function getStorage<T>(key: string, defaultValue: T): T {
  try {
    const data = uni.getStorageSync(key)
    return data ? JSON.parse(data) : defaultValue
  } catch {
    return defaultValue
  }
}

export function setStorage(key: string, value: any): void {
  try {
    uni.setStorageSync(key, JSON.stringify(value))
  } catch (e) {
    console.error('Storage set error:', e)
    uni.showToast({ title: '存储失败', icon: 'none' })
  }
}

export function removeStorage(key: string): void {
  try {
    uni.removeStorageSync(key)
  } catch (e) {
    console.error('Storage remove error:', e)
  }
}

export function clearAllStorage(): void {
  try {
    uni.clearStorageSync()
  } catch (e) {
    console.error('Storage clear error:', e)
  }
}

export { STORAGE_KEYS }
