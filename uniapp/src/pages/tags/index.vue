<template>
  <view class="container">
    <view class="page-header">
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
</script>

<style lang="scss" scoped>
.tags-list {
  height: calc(100vh - 200rpx);
  padding: 0 $spacing-md;
}

.tag-item {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  background: $card-bg;
  border-radius: $radius-lg;
  margin-bottom: $spacing-md;
}

.tag-name {
  flex: 1;
  font-size: $font-size-base;
  color: $text-primary;
}

.tag-count {
  font-size: $font-size-sm;
  color: $text-secondary;
  margin-right: $spacing-md;
}

.tag-delete {
  font-size: $font-size-xl;
  color: $text-hint;
  line-height: 1;
  
  &:active {
    color: $error-color;
  }
}

.add-tag {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-lg;
  background: $bg-color;
  border: 2rpx dashed $border-color;
  border-radius: $radius-lg;
  margin-bottom: $spacing-md;
  
  &:active {
    background: rgba($primary-color, 0.04);
    border-color: $primary-color;
  }
}

.add-icon {
  font-size: $font-size-xl;
  color: $text-secondary;
}

.add-text {
  font-size: $font-size-base;
  color: $text-secondary;
}

.list-footer {
  height: 80rpx;
}
</style>
