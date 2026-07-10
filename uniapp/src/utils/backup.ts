import type { BackupPayload, Note, Category, TagGroup } from '@/types'
import { getStorage, setStorage, STORAGE_KEYS } from './storage'

const BACKUP_VERSION = 1

export function createBackup(): BackupPayload {
  const notes = getStorage<Note[]>(STORAGE_KEYS.NOTES, [])
  const categories = getStorage<Category[]>(STORAGE_KEYS.CATEGORIES, [])
  const tagGroups = getStorage<TagGroup[]>(STORAGE_KEYS.TAG_GROUPS, [])

  return {
    version: BACKUP_VERSION,
    categories,
    notes: notes.filter(n => !n.isDeleted),
    images: []
  }
}

export function exportBackup(): string {
  const payload = createBackup()
  return JSON.stringify(payload, null, 2)
}

export function importBackup(jsonString: string): boolean {
  try {
    const payload: BackupPayload = JSON.parse(jsonString)

    if (payload.version !== BACKUP_VERSION) {
      console.warn(`Backup version mismatch: expected ${BACKUP_VERSION}, got ${payload.version}`)
    }

    const categories = payload.categories || []
    const notes = payload.notes || []

    setStorage(STORAGE_KEYS.CATEGORIES, categories)
    setStorage(STORAGE_KEYS.NOTES, notes)

    uni.showToast({ title: '导入成功', icon: 'success' })
    return true
  } catch (e) {
    console.error('Import backup error:', e)
    uni.showToast({ title: '导入失败', icon: 'none' })
    return false
  }
}

export function downloadBackup(): void {
  const json = exportBackup()

  uni.showModal({
    title: '导出备份',
    content: '复制以下备份数据：',
    editable: true,
    placeholderText: json,
    confirmText: '复制',
    success: (res) => {
      if (res.confirm) {
        uni.setClipboardData({
          data: json,
          success: () => {
            uni.showToast({ title: '已复制到剪贴板', icon: 'success' })
          }
        })
      }
    }
  })
}
