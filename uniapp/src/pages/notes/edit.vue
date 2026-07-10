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
      />
      
      <textarea 
        class="content-input" 
        v-model="content" 
        placeholder="开始记录..."
        :auto-height="true"
        :maxlength="5000"
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

const isNew = computed(() => !noteId.value)

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
  if (isLocked.value) {
    uni.showToast({ title: '笔记已锁定', icon: 'none' })
    return
  }

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
    uni.navigateBack()
  }, 500)
}

const goBack = () => {
  if (title.value || content.value) {
    uni.showModal({
      title: '提示',
      content: '笔记未保存，确定离开吗？',
      success: (res) => {
        if (res.confirm) {
          uni.navigateBack()
        }
      }
    })
  } else {
    uni.navigateBack()
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
  uni.showToast({ 
    title: isLocked.value ? '笔记已锁定' : '笔记已解锁', 
    icon: 'none' 
  })
}

const showColorPicker = () => {
  const colors = ['白色', '黄色', '红色', '绿色', '蓝色', '紫色', '橙色', '灰色']
  const colorValues = ['#FFFFFF', '#FFF9C4', '#FFCDD2', '#C8E6C9', '#BBDEFB', '#E1BEE7', '#FFE0B2', '#D7CCC8']
  
  uni.showActionSheet({
    itemList: colors,
    success: (res) => {
      noteColor.value = colorValues[res.tapIndex]
    }
  })
}

const showMoreMenu = () => {
  uni.showActionSheet({
    itemList: ['删除笔记', '分享', '复制内容'],
    success: (res) => {
      switch (res.tapIndex) {
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
  })
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
          uni.navigateBack()
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
</style>
