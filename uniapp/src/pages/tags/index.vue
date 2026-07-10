<template>
  <view class="container">
    <view class="page-header">
      <text class="back-btn" @click="goBack">‹</text>
      <view class="header-title">标签管理</view>
    </view>

    <scroll-view class="tags-list" scroll-y>
      <view v-for="tag in allTags" :key="tag" class="tag-item">
        <text class="tag-name">{{ tag }}</text>
        <text class="tag-count">{{ getNoteCount(tag) }}</text>
        <view class="tag-delete" @click="deleteTag(tag)">×</view>
      </view>

      <view class="add-tag" @click="showAddDialog">
        <text class="add-icon">+</text>
        <text class="add-text">添加标签</text>
      </view>

      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useNotesStore } from '@/stores/notes'

const notesStore = useNotesStore()
const showDialog = ref(false)
const newTag = ref('')

const allTags = computed(() => {
  const tagSet = new Set<string>()
  notesStore.activeNotes.forEach(note => {
    note.tags.forEach(tag => tagSet.add(tag))
  })
  return Array.from(tagSet).sort()
})

const getNoteCount = (tag: string): number => {
  return notesStore.activeNotes.filter(n => n.tags.includes(tag)).length
}

const showAddDialog = () => {
  newTag.value = ''
  showDialog.value = true
}

const addTag = () => {
  const tag = newTag.value.trim()
  if (!tag) {
    uni.showToast({ title: '请输入标签名称', icon: 'none' })
    return
  }

  if (allTags.value.includes(tag)) {
    uni.showToast({ title: '标签已存在', icon: 'none' })
    return
  }

  uni.showToast({ title: '添加成功', icon: 'success' })
  showDialog.value = false
}

const deleteTag = (tag: string) => {
  uni.showModal({
    title: '删除标签',
    content: `确定要删除标签"${tag}"吗？`,
    success: (res) => {
      if (res.confirm) {
        notesStore.notes.forEach(note => {
          if (note.tags.includes(tag)) {
            notesStore.updateNote(note.id, { tags: note.tags.filter(t => t !== tag) })
          }
        })
        uni.showToast({ title: '已删除', icon: 'success' })
      }
    }
  })
}

const goBack = () => {
  uni.switchTab({ url: '/pages/settings/index' })
}
</script>

<style lang="scss" scoped>
.container {
  min-height: 100vh;
  background: linear-gradient(180deg, #E8EAF6 0%, #F5F5F5 30%, #FFFFFF 100%);
}

.page-header {
  display: flex;
  align-items: center;
  padding: $spacing-md $spacing-lg;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-md});
  background: linear-gradient(135deg, #5C6BC0 0%, #7E57C2 50%, #9C27B0 100%);
  box-shadow: 0 8rpx 32rpx rgba(92, 107, 192, 0.3);
  position: relative;
  z-index: 10;
}

.back-btn {
  font-size: $font-size-xxl;
  color: rgba(255, 255, 255, 0.9);
  padding: 0 $spacing-sm;
  margin-right: $spacing-sm;
  line-height: 1;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20rpx;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  &:active {
    transform: scale(0.92);
    background: rgba(255, 255, 255, 0.3);
  }
}

.header-title {
  font-size: $font-size-xl;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 2rpx;
}

.tags-list {
  height: calc(100vh - 180rpx);
  padding: $spacing-lg;
}

.tag-item {
  display: flex;
  align-items: center;
  padding: $spacing-xl $spacing-lg;
  background: #FFFFFF;
  border-radius: 24rpx;
  margin-bottom: $spacing-md;
  box-shadow: 
    0 4rpx 20rpx rgba(0, 0, 0, 0.06),
    0 2rpx 8rpx rgba(0, 0, 0, 0.04),
    0 1rpx 4rpx rgba(0, 0, 0, 0.02);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 8rpx;
    background: linear-gradient(180deg, #9C27B0 0%, #5C6BC0 100%);
  }

  &:active {
    transform: translateY(-2rpx);
    box-shadow: 
      0 8rpx 32rpx rgba(0, 0, 0, 0.1),
      0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  }
}

.tag-name {
  flex: 1;
  font-size: $font-size-base;
  font-weight: 600;
  color: $text-primary;
  margin-left: $spacing-md;
  letter-spacing: 1rpx;
}

.tag-count {
  font-size: $font-size-sm;
  color: $text-hint;
  margin-right: $spacing-md;
  background: rgba(156, 39, 176, 0.08);
  padding: 6rpx 20rpx;
  border-radius: 20rpx;
  font-weight: 500;
}

.tag-delete {
  font-size: $font-size-xl;
  color: rgba(239, 83, 80, 0.6);
  line-height: 1;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(239, 83, 80, 0.08);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:active {
    color: #EF5350;
    background: rgba(239, 83, 80, 0.15);
    transform: scale(0.9);
  }
}

.add-tag {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-xl;
  background: rgba(255, 255, 255, 0.8);
  border: 3rpx dashed rgba(156, 39, 176, 0.3);
  border-radius: 24rpx;
  margin-bottom: $spacing-md;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(10px);

  &:active {
    background: rgba(156, 39, 176, 0.06);
    border-color: #9C27B0;
    transform: scale(0.98);
  }
}

.add-icon {
  font-size: 44rpx;
  color: #9C27B0;
  font-weight: 300;
}

.add-text {
  font-size: $font-size-base;
  color: #9C27B0;
  font-weight: 500;
  letter-spacing: 1rpx;
}

.list-footer {
  height: 120rpx;
}
</style>
