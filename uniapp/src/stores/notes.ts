import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Note, Category, TagGroup } from '@/types'
import { NOTE_COLORS, CATEGORY_COLORS } from '@/types'
import { getStorage, setStorage, STORAGE_KEYS } from '@/utils/storage'
import { generateId } from '@/utils/id'
import { hashPin, verifyPin as verifyPinHash, isPinHashed } from '@/utils/crypto'

export const useNotesStore = defineStore('notes', () => {
  const notes = ref<Note[]>([])
  const categories = ref<Category[]>([])
  const tagGroups = ref<TagGroup[]>([])

  // 应用锁定相关状态
  const isAppLocked = ref(false)
  const appPinHash = ref<string>('')
  const hasPin = computed(() => !!appPinHash.value)

  // 初始化时从存储加载锁定状态
  const loadLockState = () => {
    const storedPin = getStorage<string>('qingjian_app_pin', '')
    if (storedPin && !isPinHashed(storedPin)) {
      const hashed = hashPin(storedPin)
      appPinHash.value = hashed
      setStorage('qingjian_app_pin', hashed)
    } else {
      appPinHash.value = storedPin
    }
    isAppLocked.value = getStorage<boolean>('qingjian_app_locked', false)
  }

  // 设置 PIN 码
  const setPin = (pin: string) => {
    if (pin.length >= 4 && pin.length <= 6) {
      const hashed = hashPin(pin)
      appPinHash.value = hashed
      setStorage('qingjian_app_pin', hashed)
      return true
    }
    return false
  }

  // 验证 PIN 码
  const verifyPin = (pin: string): boolean => {
    return verifyPinHash(pin, appPinHash.value)
  }

  // 锁定应用
  const lockApp = () => {
    if (hasPin.value) {
      isAppLocked.value = true
      setStorage('qingjian_app_locked', true)
    }
  }

  // 解锁应用
  const unlockApp = () => {
    isAppLocked.value = false
    setStorage('qingjian_app_locked', false)
  }

  // 移除 PIN 码
  const removePin = () => {
    appPinHash.value = ''
    isAppLocked.value = false
    setStorage('qingjian_app_pin', '')
    setStorage('qingjian_app_locked', false)
  }

  const activeNotes = computed(() => 
    notes.value.filter(n => !n.isDeleted).sort((a, b) => b.updatedAt - a.updatedAt)
  )

  const deletedNotes = computed(() => 
    notes.value.filter(n => n.isDeleted).sort((a, b) => b.deletedAt! - a.deletedAt!)
  )

  const getCategoryById = (id: string | null): Category | null => {
    if (!id) return null
    return categories.value.find(c => c.id === id) || null
  }

  const loadFromStorage = () => {
    notes.value = getStorage<Note[]>(STORAGE_KEYS.NOTES, [])
    categories.value = getStorage<Category[]>(STORAGE_KEYS.CATEGORIES, [])
    tagGroups.value = getStorage<TagGroup[]>(STORAGE_KEYS.TAG_GROUPS, [])
    loadLockState()

    if (categories.value.length === 0) {
      categories.value = [
        { id: generateId(), name: '默认分类', color: '#5C6BC0', createdAt: Date.now(), updatedAt: Date.now() }
      ]
      saveCategories()
    }
  }

  const saveNotes = () => setStorage(STORAGE_KEYS.NOTES, notes.value)
  const saveCategories = () => setStorage(STORAGE_KEYS.CATEGORIES, categories.value)
  const saveTagGroups = () => setStorage(STORAGE_KEYS.TAG_GROUPS, tagGroups.value)

  const addNote = (note: Partial<Note>): Note => {
    const now = Date.now()
    const newNote: Note = {
      id: note.id || generateId(),
      title: note.title || '',
      content: note.content || '',
      categoryId: note.categoryId || null,
      tags: note.tags || [],
      color: note.color || NOTE_COLORS[0],
      createdAt: note.createdAt || now,
      updatedAt: now,
      isLocked: note.isLocked || false,
      reminderTime: note.reminderTime || null,
      isDeleted: false,
      deletedAt: null
    }
    notes.value.unshift(newNote)
    saveNotes()
    return newNote
  }

  const updateNote = (id: string, updates: Partial<Note>): void => {
    const index = notes.value.findIndex(n => n.id === id)
    if (index !== -1) {
      notes.value[index] = { ...notes.value[index], ...updates, updatedAt: Date.now() }
      saveNotes()
    }
  }

  const deleteNote = (id: string): void => {
    const index = notes.value.findIndex(n => n.id === id)
    if (index !== -1) {
      notes.value[index] = {
        ...notes.value[index],
        isDeleted: true,
        deletedAt: Date.now()
      }
      saveNotes()
    }
  }

  const restoreNote = (id: string): void => {
    const index = notes.value.findIndex(n => n.id === id)
    if (index !== -1) {
      notes.value[index] = {
        ...notes.value[index],
        isDeleted: false,
        deletedAt: null
      }
      saveNotes()
    }
  }

  const permanentlyDeleteNote = (id: string): void => {
    notes.value = notes.value.filter(n => n.id !== id)
    saveNotes()
  }

  const addCategory = (name: string, color?: string): Category => {
    const newCategory: Category = {
      id: generateId(),
      name,
      color: color || CATEGORY_COLORS[categories.value.length % CATEGORY_COLORS.length],
      createdAt: Date.now(),
      updatedAt: Date.now()
    }
    categories.value.push(newCategory)
    saveCategories()
    return newCategory
  }

  const updateCategory = (id: string, updates: Partial<Category>): void => {
    const index = categories.value.findIndex(c => c.id === id)
    if (index !== -1) {
      categories.value[index] = { ...categories.value[index], ...updates, updatedAt: Date.now() }
      saveCategories()
    }
  }

  const deleteCategory = (id: string): void => {
    categories.value = categories.value.filter(c => c.id !== id)
    notes.value.forEach(n => {
      if (n.categoryId === id) {
        n.categoryId = null
      }
    })
    saveCategories()
    saveNotes()
  }

  return {
    notes,
    categories,
    tagGroups,
    activeNotes,
    deletedNotes,
    getCategoryById,
    loadFromStorage,
    addNote,
    updateNote,
    deleteNote,
    restoreNote,
    permanentlyDeleteNote,
    addCategory,
    updateCategory,
    deleteCategory,
    // 锁定相关
    isAppLocked,
    hasPin,
    setPin,
    verifyPin,
    lockApp,
    unlockApp,
    removePin
  }
})
