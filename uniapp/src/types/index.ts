export interface Note {
  id: string
  title: string
  content: string
  categoryId: string | null
  tags: string[]
  color: string
  createdAt: number
  updatedAt: number
  isLocked: boolean
  reminderTime: number | null
  isDeleted: boolean
  deletedAt: number | null
}

export interface Category {
  id: string
  name: string
  color: string
  createdAt: number
  updatedAt: number
}

export interface Tag {
  id: string
  name: string
  color: string
  createdAt: number
}

export interface TagGroup {
  id: string
  name: string
  tags: Tag[]
  createdAt: number
  updatedAt: number
}

export interface BackupPayload {
  version: number
  categories: Category[]
  notes: Note[]
  images: any[]
}

export const NOTE_COLORS = [
  '#FFFFFF',
  '#FFF9C4',
  '#FFCDD2',
  '#C8E6C9',
  '#BBDEFB',
  '#E1BEE7',
  '#FFE0B2',
  '#D7CCC8'
]

export const CATEGORY_COLORS = [
  '#5C6BC0',
  '#42A5F5',
  '#66BB6A',
  '#FFA726',
  '#EF5350',
  '#AB47BC',
  '#26C6DA',
  '#FF7043'
]
