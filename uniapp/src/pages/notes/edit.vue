<template>
  <view class="container" :style="{ background: noteColor }">
    <view class="edit-header">
      <view class="header-left">
        <view class="back-btn" @click="goBack">
          <text>←</text>
        </view>
      </view>
      <view class="header-center">
        <text class="header-title">{{ isNew ? '新建笔记' : '编辑笔记' }}</text>
      </view>
      <view class="header-right">
        <view class="action-btn" @click="showMoreMenu">
          <text>⋮</text>
        </view>
      </view>
    </view>

    <scroll-view class="edit-content" scroll-y>
      <input 
        class="title-input" 
        v-model="title" 
        placeholder="标题" 
        :maxlength="100"
        :auto-focus="isNew"
        :disabled="isReadOnly"
      />
      
      <textarea 
        class="content-input" 
        v-model="content" 
        placeholder="开始记录..."
        :auto-height="true"
        :maxlength="5000"
        :disabled="isReadOnly"
      />

      <view class="edit-footer">
        <view class="footer-section">
          <text class="section-label">分类</text>
          <view class="category-picker">
            <text 
              v-for="cat in categories" 
              :key="cat.id"
              :class="['category-chip', { active: currentCategory === cat.id }]"
              :style="{ borderColor: cat.color, color: currentCategory === cat.id ? cat.color : $text-secondary }"
              @click="selectCategory(cat.id)"
            >
              {{ cat.name }}
            </text>
            <text v-if="!currentCategory" class="category-chip active" @click="selectCategory(null)">
              无分类
            </text>
          </view>
        </view>

        <view class="footer-section">
          <text class="section-label">标签</text>
          <view class="tags-editor">
            <view 
              v-for="tag in tags" 
              :key="tag"
              class="tag-item"
            >
              <text>{{ tag }}</text>
              <text class="tag-remove" @click="removeTag(tag)">×</text>
            </view>
            <input 
              class="tag-input" 
              v-model="newTag" 
              placeholder="添加标签"
              @confirm="addTag"
              maxlength="20"
            />
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-actions">
      <view class="action-item" @click="toggleLock">
        <text>{{ isLocked ? '🔓' : '🔒' }}</text>
        <text>{{ isLocked ? '解锁' : '锁定' }}</text>
      </view>
      <view class="action-item" @click="showColorPicker">
        <text>🎨</text>
        <text>颜色</text>
      </view>
      <view class="action-item" @click="saveNote">
        <text>💾</text>
        <text>保存</text>
      </view>
    </view>

    <view v-if="showColorPanel" class="panel-overlay" @click="showColorPanel = false">
      <view class="color-panel" @click.stop>
        <view class="panel-title">选择颜色</view>
        <view class="color-grid">
          <view
            v-for="c in colorOptions"
            :key="c.value"
            class="color-option"
            :style="{ background: c.value, borderColor: noteColor === c.value ? $primary-color : 'transparent' }"
            @click="selectColor(c.value)"
          >
            <text v-if="noteColor === c.value" class="color-check">✓</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="showMorePanel" class="panel-overlay" @click="showMorePanel = false">
      <view class="action-panel" @click.stop>
        <view
          v-for="(action, index) in moreActions"
          :key="index"
          class="action-panel-item"
          :class="{ danger: index === 0 }"
          @click="onMoreAction(index)"
        >
          {{ action }}
        </view>
        <view class="action-panel-cancel" @click="showMorePanel = false">取消</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useNotesStore } from '@/stores/notes'
import type { Note } from '@/types'

const notesStore = useNotesStore()

const noteId = ref<string | null>(null)
const title = ref('')
const content = ref('')
const tags = ref<string[]>([])
const currentCategory = ref<string | null>(null)
const noteColor = ref('#FFFFFF')
const isLocked = ref(false)
const newTag = ref('')
const showColorPanel = ref(false)
const showMorePanel = ref(false)

const colorOptions = [
  { name: '白色', value: '#FFFFFF' },
  { name: '黄色', value: '#FFF9C4' },
  { name: '红色', value: '#FFCDD2' },
  { name: '绿色', value: '#C8E6C9' },
  { name: '蓝色', value: '#BBDEFB' },
  { name: '紫色', value: '#E1BEE7' },
  { name: '橙色', value: '#FFE0B2' },
  { name: '灰色', value: '#D7CCC8' }
]

const moreActions = ['删除笔记', '分享', '复制内容']

const isNew = computed(() => !noteId.value)
const isReadOnly = computed(() => isLocked.value)

const categories = computed(() => notesStore.categories)

onMounted(() => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  const options = (currentPage as any).$page?.options || {}
  
  if (options.id) {
    noteId.value = options.id
    loadNote()
  }
})

const loadNote = () => {
  const note = notesStore.notes.find(n => n.id === noteId.value)
  if (note) {
    title.value = note.title
    content.value = note.content
    tags.value = note.tags || []
    currentCategory.value = note.categoryId
    noteColor.value = note.color
    isLocked.value = note.isLocked
  }
}

const saveNote = () => {
  if (isNew.value) {
    notesStore.addNote({
      title: title.value,
      content: content.value,
      tags: tags.value,
      categoryId: currentCategory.value,
      color: noteColor.value,
      isLocked: isLocked.value
    })
    uni.showToast({ title: '创建成功', icon: 'success' })
  } else {
    notesStore.updateNote(noteId.value!, {
      title: title.value,
      content: content.value,
      tags: tags.value,
      categoryId: currentCategory.value,
      color: noteColor.value,
      isLocked: isLocked.value
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
  }

  setTimeout(() => {
    uni.switchTab({ url: '/pages/notes/index' })
  }, 500)
}

const goBack = () => {
  if (title.value || content.value) {
    uni.showModal({
      title: '提示',
      content: '笔记未保存，确定离开吗？',
      success: (res) => {
        if (res.confirm) {
          uni.switchTab({ url: '/pages/notes/index' })
        }
      }
    })
  } else {
    uni.switchTab({ url: '/pages/notes/index' })
  }
}

const selectCategory = (id: string | null) => {
  currentCategory.value = id
}

const addTag = () => {
  const tag = newTag.value.trim()
  if (tag && !tags.value.includes(tag)) {
    tags.value.push(tag)
    newTag.value = ''
  }
}

const removeTag = (tag: string) => {
  tags.value = tags.value.filter(t => t !== tag)
}

const toggleLock = () => {
  // 如果要解锁，需要验证PIN码
  if (isLocked.value) {
    if (!notesStore.hasPin) {
      uni.showModal({
        title: '解锁笔记',
        content: '请先设置应用PIN码',
        confirmText: '去设置',
        success: (res) => {
          if (res.confirm) {
            uni.navigateTo({ url: '/pages/lock/index?mode=set' })
          }
        }
      })
      return
    }
    
    // 跳转到PIN验证页面解锁
    uni.navigateTo({
      url: '/pages/lock/index?mode=unlock&noteId=' + noteId.value,
      success: () => {
        // 验证成功后会返回并解锁
      }
    })
  } else {
    // 锁定笔记
    if (!notesStore.hasPin) {
      uni.showModal({
        title: '锁定笔记',
        content: '锁定笔记需要先设置应用PIN码',
        confirmText: '去设置',
        success: (res) => {
          if (res.confirm) {
            uni.navigateTo({ url: '/pages/lock/index?mode=set' })
          }
        }
      })
      return
    }
    
    isLocked.value = true
    if (!isNew.value && noteId.value) {
      notesStore.updateNote(noteId.value, { isLocked: true })
    }
    uni.showToast({
      title: '笔记已锁定',
      icon: 'success'
    })
  }
}

const showColorPicker = () => {
  showColorPanel.value = true
}

const selectColor = (color: string) => {
  noteColor.value = color
  showColorPanel.value = false
}

const showMoreMenu = () => {
  showMorePanel.value = true
}

const onMoreAction = (index: number) => {
  showMorePanel.value = false
  switch (index) {
    case 0:
      deleteNote()
      break
    case 1:
      shareNote()
      break
    case 2:
      copyContent()
      break
  }
}

const deleteNote = () => {
  uni.showModal({
    title: '删除笔记',
    content: '确定要删除这条笔记吗？',
    success: (res) => {
      if (res.confirm && noteId.value) {
        notesStore.deleteNote(noteId.value)
        uni.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => {
          uni.switchTab({ url: '/pages/notes/index' })
        }, 500)
      }
    }
  })
}

const shareNote = () => {
  const text = `${title.value || '无标题'}\n\n${content.value}`
  uni.setClipboardData({
    data: text,
    success: () => {
      uni.showToast({ title: '内容已复制', icon: 'success' })
    }
  })
}

const copyContent = () => {
  const text = `${title.value || ''}\n\n${content.value}`
  uni.setClipboardData({
    data: text,
    success: () => {
      uni.showToast({ title: '已复制', icon: 'success' })
    }
  })
}
</script>

<style lang="scss" scoped>
/* 容器背景 - 浅灰渐变到白色 */
.container {
  background: linear-gradient(180deg, #E8EAF6 0%, #F5F5F5 50%, #FFFFFF 100%);
  min-height: 100vh;
}

/* 编辑页面头部 - 渐变背景 */
.edit-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-lg});
  background: linear-gradient(135deg, #5C6BC0 0%, #7986CB 50%, #9C27B0 100%);
  backdrop-filter: blur(20px);
  box-shadow: 0 4rpx 20rpx rgba(92, 107, 192, 0.25);
  border-bottom: none;
}

.header-left, .header-right {
  width: 80rpx;
}

/* 返回按钮和操作按钮 - 圆角图标 */
.back-btn, .action-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  color: #FFFFFF;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0.15) 100%);
  border-radius: 20rpx;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4rpx 12rpx rgba(255, 255, 255, 0.1),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.3);
  
  &:active {
    transform: scale(0.85) rotate(-5deg);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.25) 100%);
    box-shadow: 0 2rpx 8rpx rgba(255, 255, 255, 0.15);
  }
}

.header-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 2rpx;
  text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.2);
}

/* 编辑内容区域 */
.edit-content {
  height: calc(100vh - 360rpx);
  padding: $spacing-lg $spacing-md;
}

/* 标题输入框 - 圆角边框和阴影 */
.title-input {
  width: 100%;
  font-size: 56rpx;
  font-weight: 700;
  color: $text-primary;
  padding: $spacing-lg;
  margin-bottom: $spacing-lg;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0.9) 100%);
  border-radius: 24rpx;
  border: 3rpx solid rgba(92, 107, 192, 0.15);
  letter-spacing: 2rpx;
  box-shadow: 
    0 8rpx 24rpx rgba(92, 107, 192, 0.12),
    0 4rpx 12rpx rgba(0, 0, 0, 0.06),
    inset 0 2rpx 0 rgba(255, 255, 255, 1);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:focus {
    border-color: #5C6BC0;
    box-shadow: 
      0 12rpx 32rpx rgba(92, 107, 192, 0.18),
      0 6rpx 16rpx rgba(156, 39, 176, 0.1),
      inset 0 2rpx 0 rgba(255, 255, 255, 1);
  }
  
  &:disabled {
    opacity: 0.4;
    background: rgba(255, 255, 255, 0.6);
  }
}

/* 内容输入框 - 圆角边框和阴影 */
.content-input {
  width: 100%;
  font-size: $font-size-lg;
  color: $text-primary;
  line-height: 2.2;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0.9) 100%);
  min-height: 500rpx;
  padding: $spacing-lg;
  border-radius: 24rpx;
  border: 3rpx solid rgba(92, 107, 192, 0.15);
  box-shadow: 
    0 8rpx 24rpx rgba(92, 107, 192, 0.12),
    0 4rpx 12rpx rgba(0, 0, 0, 0.06),
    inset 0 2rpx 0 rgba(255, 255, 255, 1);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:focus {
    border-color: #5C6BC0;
    box-shadow: 
      0 12rpx 32rpx rgba(92, 107, 192, 0.18),
      0 6rpx 16rpx rgba(156, 39, 176, 0.1),
      inset 0 2rpx 0 rgba(255, 255, 255, 1);
  }
  
  &:disabled {
    opacity: 0.4;
    background: rgba(255, 255, 255, 0.6);
  }
}

/* 编辑页面底部区域 */
.edit-footer {
  margin-top: $spacing-xl;
  padding-top: $spacing-xl;
  border-top: 3rpx solid rgba(92, 107, 192, 0.12);
}

/* 底部分组 */
.footer-section {
  margin-bottom: $spacing-xl;
  
  &:last-child {
    margin-bottom: 0;
  }
}

/* 分组标签 */
.section-label {
  font-size: 36rpx;
  color: #5C6BC0;
  margin-bottom: $spacing-lg;
  display: block;
  font-weight: 700;
  letter-spacing: 2rpx;
}

/* 分类选择器 */
.category-picker {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

/* 分类芯片 - 圆角胶囊样式 */
.category-chip {
  padding: $spacing-sm $spacing-lg;
  border-radius: 28rpx;
  font-size: 32rpx;
  border: 3rpx solid rgba(92, 107, 192, 0.2);
  color: $text-secondary;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 600;
  letter-spacing: 1rpx;
  box-shadow: 
    0 4rpx 12rpx rgba(92, 107, 192, 0.1),
    0 2rpx 6rpx rgba(0, 0, 0, 0.04);
  
  &.active {
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.12) 100%);
    border-color: #5C6BC0;
    color: #5C6BC0;
    box-shadow: 
      0 8rpx 24rpx rgba(92, 107, 192, 0.2),
      0 4rpx 12rpx rgba(156, 39, 176, 0.12),
      inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
    transform: translateY(-4rpx);
  }
  
  &:active {
    transform: scale(0.92) translateY(0);
  }
}

/* 标签编辑器 */
.tags-editor {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
}

/* 标签项 */
.tag-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.12) 100%);
  border-radius: 24rpx;
  font-size: 32rpx;
  color: #5C6BC0;
  font-weight: 600;
  letter-spacing: 1rpx;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4rpx 12rpx rgba(92, 107, 192, 0.12),
    0 2rpx 6rpx rgba(0, 0, 0, 0.04);
  
  .tag-remove {
    font-size: 32rpx;
    color: rgba(92, 107, 192, 0.5);
    line-height: 1;
    font-weight: 700;
    transition: all 0.2s ease;
    
    &:active {
      opacity: 0.5;
      transform: scale(0.8);
    }
  }
  
  &:active {
    transform: scale(0.95);
  }
}

/* 标签输入框 */
.tag-input {
  flex: 1;
  min-width: 180rpx;
  font-size: 32rpx;
  color: $text-primary;
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0.85) 100%);
  border-radius: 24rpx;
  border: 3rpx solid rgba(92, 107, 192, 0.12);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4rpx 12rpx rgba(92, 107, 192, 0.08),
    0 2rpx 6rpx rgba(0, 0, 0, 0.04);
  font-weight: 500;
  
  &:focus {
    border-color: #5C6BC0;
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.05) 0%, rgba(156, 39, 176, 0.03) 100%);
    box-shadow: 
      0 8rpx 20rpx rgba(92, 107, 192, 0.12),
      0 4rpx 10rpx rgba(0, 0, 0, 0.06);
  }
}

/* 底部操作按钮区域 */
.bottom-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-around;
  padding: $spacing-lg $spacing-xl;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-lg});
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(255, 255, 255, 0.95) 100%);
  backdrop-filter: blur(20px);
  box-shadow: 
    0 -8rpx 32rpx rgba(92, 107, 192, 0.15),
    0 -4rpx 16rpx rgba(0, 0, 0, 0.08);
}

/* 底部操作按钮项 - 漂亮的图标和动画效果 */
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-md $spacing-lg;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.08) 0%, rgba(156, 39, 176, 0.06) 100%);
  border-radius: 24rpx;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4rpx 12rpx rgba(92, 107, 192, 0.1),
    0 2rpx 6rpx rgba(0, 0, 0, 0.04);
  animation: actionItemBounce 0.6s ease-out;

  text {
    font-size: 28rpx;
    color: $text-secondary;
    font-weight: 600;
    letter-spacing: 1rpx;
  }

  text:first-child {
    font-size: 40rpx;
  }

  &:active {
    transform: scale(0.85) translateY(4rpx);
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.12) 100%);
    box-shadow: 
      0 2rpx 8rpx rgba(92, 107, 192, 0.08),
      0 1rpx 4rpx rgba(0, 0, 0, 0.03);
  }
}

/* 操作按钮弹跳动画 */
@keyframes actionItemBounce {
  0% {
    opacity: 0;
    transform: translateY(20rpx);
  }
  60% {
    transform: translateY(-10rpx);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
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
  margin-bottom: $spacing-xl;
  letter-spacing: 2rpx;
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
  border: 4rpx solid rgba(92, 107, 192, 0.2);
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
</style>
