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
.container {
  background: linear-gradient(180deg, #f8f9fa 0%, #ffffff 100%);
}

.page-header {
  position: relative;
  padding: $spacing-lg $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-lg});
  background: #ffffff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
  
  .header-title {
    font-size: 52rpx;
    font-weight: 700;
    color: $text-primary;
    letter-spacing: 2rpx;
  }
  
  .header-subtitle {
    font-size: $font-size-sm;
    color: $text-hint;
    margin-top: $spacing-xs;
    letter-spacing: 1rpx;
  }
}

.header-actions {
  position: absolute;
  right: $spacing-md;
  top: calc(env(safe-area-inset-top) + #{$spacing-lg});
}

.action-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba($primary-color, 0.08) 0%, rgba($primary-color, 0.12) 100%);
  border-radius: 50%;
  box-shadow: 0 4rpx 12rpx rgba($primary-color, 0.15);
  transition: all 0.3s ease;
  
  &:active {
    transform: scale(0.9);
    box-shadow: 0 2rpx 8rpx rgba($primary-color, 0.1);
  }
  
  .icon {
    font-size: 32rpx;
  }
}

.category-tabs {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-md $spacing-md;
  padding-top: $spacing-lg;
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
  padding: $spacing-sm $spacing-md;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 100rpx;
  font-size: $font-size-base;
  color: $text-secondary;
  border: 2rpx solid transparent;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
  
  &.active {
    background: linear-gradient(135deg, rgba($primary-color, 0.95) 0%, rgba($primary-color, 0.85) 100%);
    color: #FFFFFF;
    border-color: transparent;
    box-shadow: 0 6rpx 16rpx rgba($primary-color, 0.25);
    transform: translateY(-2rpx);
    
    .tab-count {
      background: rgba(255, 255, 255, 0.3);
    }
  }
  
  .tab-dot {
    width: 16rpx;
    height: 16rpx;
    border-radius: 50%;
    box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.1);
  }
  
  .tab-count {
    font-size: $font-size-xs;
    background: rgba(0, 0, 0, 0.08);
    padding: 4rpx 14rpx;
    border-radius: 100rpx;
    font-weight: 500;
  }
}

.notes-list {
  height: calc(100vh - 320rpx);
  padding: 0 $spacing-md;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 160rpx $spacing-lg;
  
  .empty-icon {
    font-size: 160rpx;
    margin-bottom: $spacing-lg;
    animation: floatAnimation 3s ease-in-out infinite;
  }
  
  .empty-text {
    font-size: $font-size-xl;
    color: $text-primary;
    margin-bottom: $spacing-sm;
    font-weight: 600;
  }
  
  .empty-hint {
    font-size: $font-size-sm;
    color: $text-hint;
  }
}

@keyframes floatAnimation {
  0%, 100% { transform: translateY(0rpx); }
  50% { transform: translateY(-20rpx); }
}

.note-card {
  position: relative;
  padding: $spacing-lg;
  border-radius: $radius-lg;
  margin-bottom: $spacing-md;
  background: #FFFFFF;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.06);
  border: 1rpx solid rgba(0, 0, 0, 0.04);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  
  &:active {
    transform: scale(0.98);
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.08);
  }
  
  // 不同颜色的卡片样式
  &[style*="#FFF9C4"] {
    border-left: 6rpx solid #FBC02D;
  }
  
  &[style*="#FFCDD2"] {
    border-left: 6rpx solid #EF5350;
  }
  
  &[style*="#C8E6C9"] {
    border-left: 6rpx solid #66BB6A;
  }
  
  &[style*="#BBDEFB"] {
    border-left: 6rpx solid #42A5F5;
  }
  
  &[style*="#E1BEE7"] {
    border-left: 6rpx solid #AB47BC;
  }
  
  &[style*="#FFE0B2"] {
    border-left: 6rpx solid #FF9800;
  }
  
  &[style*="#D7CCC8"] {
    border-left: 6rpx solid #8D6E63;
  }
}

.lock-badge {
  position: absolute;
  right: $spacing-lg;
  top: $spacing-lg;
  font-size: 28rpx;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 50%;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.note-category {
  display: inline-block;
  padding: 6rpx 18rpx;
  border-radius: $radius-sm;
  font-size: $font-size-xs;
  color: #FFFFFF;
  margin-bottom: $spacing-sm;
  font-weight: 500;
  letter-spacing: 1rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
}

.note-title {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-sm;
  line-height: 1.4;
  
  &.empty {
    color: $text-hint;
    font-weight: normal;
  }
}

.note-preview {
  font-size: $font-size-base;
  color: $text-secondary;
  line-height: 1.6;
  opacity: 0.85;
}

.ellipsis-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
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
  background: rgba($primary-color, 0.08);
  padding: 6rpx 14rpx;
  border-radius: $radius-sm;
  color: $primary-color;
  font-weight: 500;
}

.note-time {
  font-size: $font-size-xs;
  color: $text-hint;
  font-weight: 400;
}

.list-footer {
  height: 180rpx;
}

.fab {
  position: fixed;
  right: $spacing-lg;
  bottom: 180rpx;
  width: 120rpx;
  height: 120rpx;
  background: linear-gradient(135deg, $primary-color 0%, $primary-dark 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 
    0 8rpx 24rpx rgba($primary-color, 0.35),
    0 4rpx 12rpx rgba($primary-color, 0.2);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 50;

  &:active {
    transform: scale(0.9);
    box-shadow: 
      0 4rpx 16rpx rgba($primary-color, 0.3),
      0 2rpx 8rpx rgba($primary-color, 0.15);
  }

  .fab-icon {
    font-size: 56rpx;
    color: #FFFFFF;
    font-weight: 300;
    text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.2);
  }
}

.panel-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 200;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  animation: overlayFadeIn 0.3s ease;
}

@keyframes overlayFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.action-panel {
  width: 100%;
  background: #FFFFFF;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: $spacing-md;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-md});
  animation: panelSlideUp 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes panelSlideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.panel-title {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
  text-align: center;
  margin-bottom: $spacing-md;
  padding: $spacing-sm 0;
}

.action-panel-item {
  padding: $spacing-lg;
  text-align: center;
  font-size: $font-size-lg;
  color: $text-primary;
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.06);
  transition: background 0.2s ease;
  font-weight: 500;

  &.danger {
    color: $error-color;
  }

  &:active {
    background: rgba(0, 0, 0, 0.04);
  }
}

.action-panel-cancel {
  padding: $spacing-lg;
  text-align: center;
  font-size: $font-size-lg;
  color: $text-secondary;
  margin-top: $spacing-md;
  background: rgba(0, 0, 0, 0.04);
  border-radius: $radius-lg;
  transition: all 0.2s ease;
  font-weight: 500;

  &:active {
    opacity: 0.7;
    background: rgba(0, 0, 0, 0.08);
  }
}

.color-panel {
  width: 100%;
  background: #FFFFFF;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: $spacing-lg;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-lg});
  animation: panelSlideUp 0.3s cubic-bezier(0.4, 0, 0.2, 1);
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
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  
  &:active {
    transform: scale(0.95);
  }
}

.color-check {
  font-size: 48rpx;
  color: #FFFFFF;
  font-weight: bold;
  text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.3);
}
</style>
