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
  isLocked.value = !isLocked.value
  if (!isNew.value && noteId.value) {
    notesStore.updateNote(noteId.value, { isLocked: isLocked.value })
  }
  uni.showToast({
    title: isLocked.value ? '笔记已锁定' : '笔记已解锁',
    icon: 'none'
  })
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
.edit-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-lg});
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
}

.header-left, .header-right {
  width: 80rpx;
}

.back-btn, .action-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $font-size-xl;
  color: $text-primary;
}

.header-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.edit-content {
  height: calc(100vh - 320rpx);
  padding: $spacing-md;
}

.title-input {
  width: 100%;
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
  padding: $spacing-sm 0;
  margin-bottom: $spacing-xs;
  background: transparent;
}

.content-input {
  width: 100%;
  font-size: $font-size-base;
  color: $text-primary;
  line-height: 1.8;
  background: transparent;
  min-height: 400rpx;
}

.edit-footer {
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1rpx solid $border-color;
}

.footer-section {
  margin-bottom: $spacing-lg;
}

.section-label {
  font-size: $font-size-sm;
  color: $text-secondary;
  margin-bottom: $spacing-sm;
  display: block;
}

.category-picker {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.category-chip {
  padding: $spacing-xs $spacing-md;
  border-radius: 100rpx;
  font-size: $font-size-sm;
  border: 1rpx solid $border-color;
  color: $text-secondary;
  
  &.active {
    background: rgba($primary-color, 0.08);
  }
}

.tags-editor {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-sm;
  background: rgba($primary-color, 0.1);
  border-radius: $radius-sm;
  font-size: $font-size-sm;
  
  .tag-remove {
    font-size: $font-size-lg;
    color: $text-secondary;
    line-height: 1;
  }
}

.tag-input {
  flex: 1;
  min-width: 120rpx;
  font-size: $font-size-sm;
  color: $text-primary;
  padding: $spacing-xs $spacing-sm;
  background: rgba(0, 0, 0, 0.04);
  border-radius: $radius-sm;
}

.bottom-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-around;
  padding: $spacing-md;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-md});
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.04);
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;

  text {
    font-size: $font-size-sm;
    color: $text-secondary;
  }

  text:first-child {
    font-size: $font-size-lg;
  }

  &:active {
    opacity: 0.6;
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

.color-panel {
  width: 100%;
  background: $card-bg;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: $spacing-lg;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-lg});
}

.panel-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  text-align: center;
  margin-bottom: $spacing-lg;
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

.action-panel {
  width: 100%;
  background: $card-bg;
  border-radius: $radius-xl $radius-xl 0 0;
  padding: $spacing-md;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-md});
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
</style>
