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
/* 容器背景 - 浅灰渐变到白色 */
.container {
  background: linear-gradient(180deg, #E8EAF6 0%, #F5F5F5 50%, #FFFFFF 100%);
  min-height: 100vh;
}

/* 页面头部 - 使用渐变背景 */
.page-header {
  position: relative;
  padding: $spacing-lg $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-lg});
  background: linear-gradient(135deg, #5C6BC0 0%, #7986CB 100%);
  box-shadow: 0 4rpx 20rpx rgba(92, 107, 192, 0.3);
  
  .header-title {
    font-size: 56rpx;
    font-weight: 700;
    color: #FFFFFF;
    letter-spacing: 4rpx;
    text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
  }
  
  .header-subtitle {
    font-size: $font-size-base;
    color: rgba(255, 255, 255, 0.85);
    margin-top: $spacing-xs;
    letter-spacing: 2rpx;
    font-weight: 500;
  }
}

.header-actions {
  position: absolute;
  right: $spacing-md;
  top: calc(env(safe-area-inset-top) + #{$spacing-lg});
}

/* 操作按钮 - 圆角图标，带阴影 */
.action-btn {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0.85) 100%);
  border-radius: 24rpx;
  box-shadow: 
    0 8rpx 24rpx rgba(92, 107, 192, 0.2),
    0 4rpx 12rpx rgba(0, 0, 0, 0.08),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:active {
    transform: scale(0.85) rotate(5deg);
    box-shadow: 
      0 4rpx 16rpx rgba(92, 107, 192, 0.15),
      0 2rpx 8rpx rgba(0, 0, 0, 0.05);
  }
  
  .icon {
    font-size: 36rpx;
  }
}

/* 分类标签栏 */
.category-tabs {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-lg $spacing-md;
  padding-top: $spacing-xl;
  overflow-x: auto;
  white-space: nowrap;
  background: transparent;
  
  &::-webkit-scrollbar {
    display: none;
  }
}

/* 分类标签项 - 圆角胶囊样式 */
.tab-item {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(135deg, #FFFFFF 0%, rgba(255, 255, 255, 0.9) 100%);
  border-radius: 32rpx;
  font-size: $font-size-base;
  color: $text-secondary;
  border: 2rpx solid rgba(92, 107, 192, 0.1);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4rpx 16rpx rgba(0, 0, 0, 0.08),
    0 2rpx 8rpx rgba(92, 107, 192, 0.06);
  
  &.active {
    background: linear-gradient(135deg, #5C6BC0 0%, #9C27B0 100%);
    color: #FFFFFF;
    border-color: transparent;
    box-shadow: 
      0 8rpx 24rpx rgba(92, 107, 192, 0.35),
      0 4rpx 12rpx rgba(156, 39, 176, 0.2);
    transform: translateY(-4rpx) scale(1.02);
    
    .tab-count {
      background: rgba(255, 255, 255, 0.25);
      color: #FFFFFF;
    }
    
    .tab-dot {
      box-shadow: 0 0 8rpx rgba(255, 255, 255, 0.8);
    }
  }
  
  .tab-dot {
    width: 16rpx;
    height: 16rpx;
    border-radius: 50%;
    box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.2);
  }
  
  .tab-count {
    font-size: $font-size-xs;
    background: rgba(92, 107, 192, 0.12);
    padding: 4rpx 12rpx;
    border-radius: 20rpx;
    font-weight: 600;
    color: #5C6BC0;
  }
}

/* 笔记列表滚动区域 */
.notes-list {
  height: calc(100vh - 320rpx);
  padding: 0 $spacing-md;
}

/* 空状态 - 漂亮的插图和提示文字 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 180rpx $spacing-lg;
  
  .empty-icon {
    font-size: 180rpx;
    margin-bottom: $spacing-xl;
    opacity: 0.7;
    animation: floatAnimation 3s ease-in-out infinite, rotateAnimation 8s linear infinite;
    filter: drop-shadow(0 8rpx 24rpx rgba(92, 107, 192, 0.2));
  }
  
  .empty-text {
    font-size: 40rpx;
    color: $text-primary;
    margin-bottom: $spacing-md;
    font-weight: 600;
    letter-spacing: 2rpx;
  }
  
  .empty-hint {
    font-size: $font-size-base;
    color: #9C27B0;
    opacity: 0.8;
    font-weight: 500;
    padding: $spacing-sm $spacing-lg;
    background: linear-gradient(135deg, rgba(156, 39, 176, 0.08) 0%, rgba(92, 107, 192, 0.08) 100%);
    border-radius: 32rpx;
    box-shadow: 0 2rpx 12rpx rgba(156, 39, 176, 0.15);
  }
}

/* 空状态浮动动画 */
@keyframes floatAnimation {
  0%, 100% { transform: translateY(0rpx) rotate(0deg); }
  50% { transform: translateY(-30rpx) rotate(5deg); }
}

/* 空状态旋转动画 */
@keyframes rotateAnimation {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-3deg); }
  75% { transform: rotate(3deg); }
}

/* 笔记卡片 - 卡片式设计，多层次阴影 */
.note-card {
  position: relative;
  padding: $spacing-lg;
  border-radius: 24rpx;
  margin-bottom: $spacing-md;
  background: #FFFFFF;
  box-shadow: 
    0 12rpx 32rpx rgba(0, 0, 0, 0.12),
    0 6rpx 16rpx rgba(92, 107, 192, 0.08),
    0 3rpx 8rpx rgba(0, 0, 0, 0.06),
    inset 0 2rpx 0 rgba(255, 255, 255, 1);
  border: 2rpx solid rgba(92, 107, 192, 0.08);
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  
  &:active {
    transform: scale(0.96) translateY(4rpx);
    box-shadow: 
      0 6rpx 20rpx rgba(0, 0, 0, 0.15),
      0 3rpx 12rpx rgba(92, 107, 192, 0.1),
      0 2rpx 6rpx rgba(0, 0, 0, 0.08);
  }
  
  /* 不同颜色卡片的左边框样式 */
  &[style*="#FFF9C4"] {
    border-left: 8rpx solid #FBC02D;
    background: linear-gradient(135deg, #FFF9C4 0%, rgba(251, 192, 45, 0.15) 100%);
  }
  
  &[style*="#FFCDD2"] {
    border-left: 8rpx solid #EF5350;
    background: linear-gradient(135deg, #FFCDD2 0%, rgba(239, 83, 80, 0.15) 100%);
  }
  
  &[style*="#C8E6C9"] {
    border-left: 8rpx solid #66BB6A;
    background: linear-gradient(135deg, #C8E6C9 0%, rgba(102, 187, 106, 0.15) 100%);
  }
  
  &[style*="#BBDEFB"] {
    border-left: 8rpx solid #42A5F5;
    background: linear-gradient(135deg, #BBDEFB 0%, rgba(66, 165, 245, 0.15) 100%);
  }
  
  &[style*="#E1BEE7"] {
    border-left: 8rpx solid #AB47BC;
    background: linear-gradient(135deg, #E1BEE7 0%, rgba(171, 71, 188, 0.15) 100%);
  }
  
  &[style*="#FFE0B2"] {
    border-left: 8rpx solid #FF9800;
    background: linear-gradient(135deg, #FFE0B2 0%, rgba(255, 152, 0, 0.15) 100%);
  }
  
  &[style*="#D7CCC8"] {
    border-left: 8rpx solid #8D6E63;
    background: linear-gradient(135deg, #D7CCC8 0%, rgba(141, 110, 99, 0.15) 100%);
  }
}

/* 锁定徽章 */
.lock-badge {
  position: absolute;
  right: $spacing-lg;
  top: $spacing-lg;
  font-size: 32rpx;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.15) 100%);
  border-radius: 50%;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);
}

/* 笔记分类标签 */
.note-category {
  display: inline-block;
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  font-size: $font-size-xs;
  color: #FFFFFF;
  margin-bottom: $spacing-sm;
  font-weight: 600;
  letter-spacing: 2rpx;
  box-shadow: 0 4rpx 12rpx rgba(92, 107, 192, 0.25);
}

/* 笔记标题 */
.note-title {
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: $spacing-sm;
  line-height: 1.5;
  letter-spacing: 1rpx;
  
  &.empty {
    color: rgba(92, 107, 192, 0.6);
    font-weight: 500;
  }
}

/* 笔记内容预览 */
.note-preview {
  font-size: $font-size-lg;
  color: $text-secondary;
  line-height: 1.7;
  opacity: 0.9;
}

/* 多行文本截断 */
.ellipsis-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* 笔记底部区域 */
.note-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-md;
}

/* 笔记标签列表 */
.note-tags {
  display: flex;
  gap: $spacing-xs;
}

/* 笔记标签项 */
.note-tag {
  font-size: $font-size-xs;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.1) 100%);
  padding: 8rpx 16rpx;
  border-radius: 20rpx;
  color: #5C6BC0;
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(92, 107, 192, 0.12);
}

/* 笔记时间 */
.note-time {
  font-size: $font-size-xs;
  color: $text-hint;
  font-weight: 500;
  letter-spacing: 1rpx;
}

/* 列表底部间距 */
.list-footer {
  height: 200rpx;
}

/* FAB 浮动按钮 - 漂亮的渐变色和阴影 */
.fab {
  position: fixed;
  right: $spacing-xl;
  bottom: 180rpx;
  width: 128rpx;
  height: 128rpx;
  background: linear-gradient(135deg, #5C6BC0 0%, #9C27B0 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 
    0 16rpx 48rpx rgba(92, 107, 192, 0.45),
    0 8rpx 24rpx rgba(156, 39, 176, 0.3),
    0 4rpx 12rpx rgba(0, 0, 0, 0.2),
    inset 0 4rpx 0 rgba(255, 255, 255, 0.3);
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 50;
  animation: fabPulse 2s ease-in-out infinite;

  &:active {
    transform: scale(0.85) rotate(90deg);
    box-shadow: 
      0 8rpx 32rpx rgba(92, 107, 192, 0.35),
      0 4rpx 16rpx rgba(156, 39, 176, 0.2),
      0 2rpx 8rpx rgba(0, 0, 0, 0.1);
  }

  .fab-icon {
    font-size: 64rpx;
    color: #FFFFFF;
    font-weight: 300;
    text-shadow: 0 4rpx 8rpx rgba(0, 0, 0, 0.3);
  }
}

/* FAB 脉冲动画 */
@keyframes fabPulse {
  0%, 100% {
    box-shadow: 
      0 16rpx 48rpx rgba(92, 107, 192, 0.45),
      0 8rpx 24rpx rgba(156, 39, 176, 0.3),
      0 4rpx 12rpx rgba(0, 0, 0, 0.2),
      inset 0 4rpx 0 rgba(255, 255, 255, 0.3);
  }
  50% {
    box-shadow: 
      0 20rpx 56rpx rgba(92, 107, 192, 0.5),
      0 10rpx 28rpx rgba(156, 39, 176, 0.35),
      0 6rpx 16rpx rgba(0, 0, 0, 0.25),
      inset 0 6rpx 0 rgba(255, 255, 255, 0.4);
  }
}

/* 面板遮罩层 */
.panel-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 200;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  animation: overlayFadeIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 面板遮罩淡入动画 */
@keyframes overlayFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* 操作面板 */
.action-panel {
  width: 100%;
  background: linear-gradient(180deg, #FFFFFF 0%, rgba(255, 255, 255, 0.98) 100%);
  border-radius: 40rpx 40rpx 0 0;
  padding: $spacing-lg;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-lg});
  animation: panelSlideUp 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 -8rpx 32rpx rgba(92, 107, 192, 0.15);
}

/* 面板滑入动画 */
@keyframes panelSlideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

/* 面板标题 */
.panel-title {
  font-size: 48rpx;
  font-weight: 700;
  color: #5C6BC0;
  text-align: center;
  margin-bottom: $spacing-lg;
  padding: $spacing-md 0;
  letter-spacing: 2rpx;
}

/* 操作面板项 */
.action-panel-item {
  padding: $spacing-lg $spacing-xl;
  text-align: center;
  font-size: 36rpx;
  color: $text-primary;
  border-bottom: 2rpx solid rgba(92, 107, 192, 0.08);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 600;
  letter-spacing: 1rpx;

  &.danger {
    color: #EF5350;
    font-weight: 700;
  }

  &:active {
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.08) 0%, rgba(156, 39, 176, 0.06) 100%);
    transform: scale(0.98);
  }
}

/* 取消按钮 */
.action-panel-cancel {
  padding: $spacing-lg $spacing-xl;
  text-align: center;
  font-size: 36rpx;
  color: $text-secondary;
  margin-top: $spacing-md;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.08) 0%, rgba(156, 39, 176, 0.06) 100%);
  border-radius: 28rpx;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 600;
  letter-spacing: 2rpx;
  box-shadow: 0 4rpx 12rpx rgba(92, 107, 192, 0.1);

  &:active {
    opacity: 0.8;
    transform: scale(0.96);
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.12) 0%, rgba(156, 39, 176, 0.1) 100%);
  }
}

/* 颜色选择面板 */
.color-panel {
  width: 100%;
  background: linear-gradient(180deg, #FFFFFF 0%, rgba(255, 255, 255, 0.98) 100%);
  border-radius: 40rpx 40rpx 0 0;
  padding: $spacing-xl;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-xl});
  animation: panelSlideUp 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 -8rpx 32rpx rgba(92, 107, 192, 0.15);
}

/* 颜色网格布局 */
.color-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-lg;
}

/* 颜色选项 - 圆角方块 */
.color-option {
  aspect-ratio: 1;
  border-radius: 24rpx;
  border: 4rpx solid rgba(92, 107, 192, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 
    0 8rpx 24rpx rgba(0, 0, 0, 0.12),
    0 4rpx 12rpx rgba(92, 107, 192, 0.08);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:active {
    transform: scale(0.88) rotate(5deg);
    box-shadow: 
      0 4rpx 16rpx rgba(0, 0, 0, 0.15),
      0 2rpx 8rpx rgba(92, 107, 192, 0.1);
  }
}

/* 颜色选中标记 */
.color-check {
  font-size: 56rpx;
  color: #FFFFFF;
  font-weight: bold;
  text-shadow: 0 4rpx 8rpx rgba(0, 0, 0, 0.4);
}
</style>
