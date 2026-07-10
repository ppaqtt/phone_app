<template>
  <view class="container">
    <view class="page-header">
      <view class="header-title">清笺</view>
      <view class="header-subtitle">{{ activeNotes.length }} 条笔记</view>
      <view class="header-actions">
        <view class="action-btn" @click="goToSearch">
          <text class="icon">🔍</text>
        </view>
      </view>
    </view>

    <view class="category-tabs">
      <view 
        v-for="cat in allCategories" 
        :key="cat.id"
        :class="['tab-item', { active: selectedCategory === cat.id }]"
        @click="selectCategory(cat.id)"
      >
        <view v-if="cat.color" class="tab-dot" :style="{ background: cat.color }"></view>
        <text>{{ cat.name }}</text>
        <text v-if="cat.count" class="tab-count">{{ cat.count }}</text>
      </view>
    </view>

    <scroll-view class="notes-list" scroll-y :enhanced="true" :show-scrollbar="false">
      <view v-if="filteredNotes.length === 0" class="empty-state">
        <text class="empty-icon">📝</text>
        <text class="empty-text">还没有笔记</text>
        <text class="empty-hint">点击右下角按钮创建第一条笔记</text>
      </view>

      <view 
        v-for="note in filteredNotes" 
        :key="note.id"
        class="note-card"
        :style="{ background: note.color }"
        @click="editNote(note)"
        @longpress="showNoteMenu(note)"
      >
        <view v-if="note.isLocked" class="lock-badge">🔒</view>
        <view v-if="note.categoryId" class="note-category" :style="{ background: getCategoryColor(note.categoryId) }">
          {{ getCategoryName(note.categoryId) }}
        </view>
        
        <view class="note-title" :class="{ empty: !note.title }">
          {{ note.title || '无标题' }}
        </view>
        <view v-if="note.content" class="note-preview ellipsis-2">
          {{ note.content }}
        </view>
        
        <view class="note-footer">
          <view class="note-tags" v-if="note.tags && note.tags.length">
            <text v-for="tag in note.tags.slice(0, 3)" :key="tag" class="note-tag">{{ tag }}</text>
          </view>
          <text class="note-time">{{ formatDateShort(note.updatedAt) }}</text>
        </view>
      </view>

      <view class="list-footer"></view>
    </scroll-view>

    <view class="fab" @click="createNote">
      <text class="fab-icon">+</text>
    </view>

    <view v-if="showMenuPanel" class="panel-overlay" @click="showMenuPanel = false">
      <view class="action-panel" @click.stop>
        <view
          v-for="(action, index) in noteMenuActions"
          :key="index"
          class="action-panel-item"
          :class="{ danger: index === 0 }"
          @click="onMenuAction(index)"
        >
          {{ action }}
        </view>
        <view class="action-panel-cancel" @click="showMenuPanel = false">取消</view>
      </view>
    </view>

    <view v-if="showCategoryPanel" class="panel-overlay" @click="showCategoryPanel = false">
      <view class="action-panel" @click.stop>
        <view class="panel-title">选择分类</view>
        <view class="action-panel-item" @click="onSelectCategoryForNote(0)">无分类</view>
        <view
          v-for="(cat, index) in notesStore.categories"
          :key="cat.id"
          class="action-panel-item"
          @click="onSelectCategoryForNote(index + 1)"
        >
          {{ cat.name }}
        </view>
        <view class="action-panel-cancel" @click="showCategoryPanel = false">取消</view>
      </view>
    </view>

    <view v-if="showNoteColorPanel" class="panel-overlay" @click="showNoteColorPanel = false">
      <view class="color-panel" @click.stop>
        <view class="panel-title">标记颜色</view>
        <view class="color-grid">
          <view
            v-for="c in noteColorOptions"
            :key="c.value"
            class="color-option"
            :style="{ background: c.value }"
            @click="onSelectColorForNote(c.value)"
          >
            <text v-if="menuNote?.color === c.value" class="color-check">✓</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useNotesStore } from '@/stores/notes'
import type { Note, Category } from '@/types'
import { formatDateShort } from '@/utils/id'

const notesStore = useNotesStore()

const selectedCategory = ref<string | null>(null)
const menuNote = ref<Note | null>(null)
const showMenuPanel = ref(false)
const showCategoryPanel = ref(false)
const showNoteColorPanel = ref(false)

const noteMenuActions = ['删除', '移动到分类', '标记颜色']
const noteColorOptions = [
  { name: '白色', value: '#FFFFFF' },
  { name: '黄色', value: '#FFF9C4' },
  { name: '红色', value: '#FFCDD2' },
  { name: '绿色', value: '#C8E6C9' },
  { name: '蓝色', value: '#BBDEFB' },
  { name: '紫色', value: '#E1BEE7' },
  { name: '橙色', value: '#FFE0B2' },
  { name: '灰色', value: '#D7CCC8' }
]

const activeNotes = computed(() => notesStore.activeNotes)

const allCategories = computed(() => {
  const all: (Category & { count?: number })[] = [
    { id: null as unknown as string, name: '全部', color: '', createdAt: 0, updatedAt: 0, count: activeNotes.value.length }
  ]
  
  notesStore.categories.forEach(cat => {
    const count = activeNotes.value.filter(n => n.categoryId === cat.id).length
    all.push({ ...cat, count })
  })
  
  return all
})

const filteredNotes = computed(() => {
  if (!selectedCategory.value) return activeNotes.value
  return activeNotes.value.filter(n => n.categoryId === selectedCategory.value)
})

const getCategoryName = (id: string): string => {
  const cat = notesStore.getCategoryById(id)
  return cat?.name || ''
}

const getCategoryColor = (id: string): string => {
  const cat = notesStore.getCategoryById(id)
  return cat?.color || '#5C6BC0'
}

const selectCategory = (id: string) => {
  selectedCategory.value = selectedCategory.value === id ? null : id
}

const createNote = () => {
  const note = notesStore.addNote({})
  editNote(note)
}

const editNote = (note: Note) => {
  uni.navigateTo({ url: `/pages/notes/edit?id=${note.id}` })
}

const showNoteMenu = (note: Note) => {
  menuNote.value = note
  showMenuPanel.value = true
}

const onMenuAction = (index: number) => {
  const note = menuNote.value
  showMenuPanel.value = false
  if (!note) return
  switch (index) {
    case 0:
      notesStore.deleteNote(note.id)
      uni.showToast({ title: '已移至回收站', icon: 'success' })
      break
    case 1:
      menuNote.value = note
      showCategoryPanel.value = true
      break
    case 2:
      menuNote.value = note
      showNoteColorPanel.value = true
      break
  }
}

const onSelectCategoryForNote = (index: number) => {
  const note = menuNote.value
  showCategoryPanel.value = false
  if (!note) return
  const catId = index === 0 ? null : notesStore.categories[index - 1]?.id || null
  notesStore.updateNote(note.id, { categoryId: catId })
  uni.showToast({ title: '已移动', icon: 'success' })
}

const onSelectColorForNote = (color: string) => {
  const note = menuNote.value
  showNoteColorPanel.value = false
  if (!note) return
  notesStore.updateNote(note.id, { color })
}

const goToSearch = () => {
  uni.switchTab({ url: '/pages/search/index' })
}
</script>

<style lang="scss" scoped>
.page-header {
  position: relative;
}

.header-actions {
  position: absolute;
  right: $spacing-md;
  top: calc(env(safe-area-inset-top) + #{$spacing-lg});
}

.action-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-color;
  border-radius: 50%;
  
  .icon {
    font-size: $font-size-lg;
  }
}

.category-tabs {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-md;
  overflow-x: auto;
  white-space: nowrap;
  
  &::-webkit-scrollbar {
    display: none;
  }
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-md;
  background: $card-bg;
  border-radius: 100rpx;
  font-size: $font-size-sm;
  color: $text-secondary;
  border: 1rpx solid transparent;
  transition: all 0.2s ease;
  
  &.active {
    background: rgba($primary-color, 0.1);
    color: $primary-color;
    border-color: $primary-color;
  }
  
  .tab-dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
  }
  
  .tab-count {
    font-size: $font-size-xs;
    background: rgba(0, 0, 0, 0.06);
    padding: 2rpx 12rpx;
    border-radius: 100rpx;
    
    &.active & {
      background: rgba($primary-color, 0.2);
    }
  }
}

.notes-list {
  height: calc(100vh - 280rpx);
  padding: 0 $spacing-md;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx $spacing-lg;
  
  .empty-icon {
    font-size: 120rpx;
    margin-bottom: $spacing-lg;
  }
  
  .empty-text {
    font-size: $font-size-lg;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
  }
  
  .empty-hint {
    font-size: $font-size-sm;
    color: $text-hint;
  }
}

.note-card {
  position: relative;
  padding: $spacing-lg;
  border-radius: $radius-lg;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-sm;
  
  &:active {
    opacity: 0.8;
  }
}

.lock-badge {
  position: absolute;
  right: $spacing-md;
  top: $spacing-md;
  font-size: $font-size-base;
}

.note-category {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: $radius-sm;
  font-size: $font-size-xs;
  color: #FFFFFF;
  margin-bottom: $spacing-sm;
}

.note-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
  
  &.empty {
    color: $text-hint;
    font-weight: normal;
  }
}

.note-preview {
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: 1.6;
}

.note-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-md;
}

.note-tags {
  display: flex;
  gap: $spacing-xs;
}

.note-tag {
  font-size: $font-size-xs;
  background: rgba(0, 0, 0, 0.06);
  padding: 4rpx 12rpx;
  border-radius: $radius-sm;
}

.note-time {
  font-size: $font-size-xs;
  color: $text-hint;
}

.list-footer {
  height: 160rpx;
}

.fab-button {
  position: fixed;
  right: $spacing-lg;
  bottom: 160rpx;
  width: 120rpx;
  height: 120rpx;
  background: $primary-color;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-lg;

  &:active {
    background: $primary-dark;
    transform: scale(0.95);
  }

  .fab-icon {
    font-size: 60rpx;
    color: #FFFFFF;
    font-weight: 300;
  }
}

.panel-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 200;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.action-panel {
  width: 100%;
  background: $card-bg;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: $spacing-md;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-md});
}

.panel-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  text-align: center;
  margin-bottom: $spacing-md;
  padding: $spacing-sm 0;
}

.action-panel-item {
  padding: $spacing-lg;
  text-align: center;
  font-size: $font-size-base;
  color: $text-primary;
  border-bottom: 1rpx solid $border-color;

  &.danger {
    color: $error-color;
  }

  &:active {
    background: $bg-color;
  }
}

.action-panel-cancel {
  padding: $spacing-lg;
  text-align: center;
  font-size: $font-size-base;
  color: $text-secondary;
  margin-top: $spacing-md;
  background: $bg-color;
  border-radius: $radius-lg;

  &:active {
    opacity: 0.7;
  }
}

.color-panel {
  width: 100%;
  background: $card-bg;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: $spacing-lg;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-lg});
}

.color-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-md;
}

.color-option {
  aspect-ratio: 1;
  border-radius: $radius-lg;
  border: 3rpx solid transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-sm;
}

.color-check {
  font-size: $font-size-xl;
  color: $text-primary;
  font-weight: bold;
}
</style>
