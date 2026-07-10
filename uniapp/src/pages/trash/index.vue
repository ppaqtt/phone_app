<template>
  <view class="container">
    <view class="page-header">
      <text class="back-btn" @click="goBack">‹</text>
      <view class="header-title">回收站</view>
      <view class="header-subtitle">{{ deletedNotes.length }} 条已删除</view>
    </view>

    <scroll-view class="trash-list" scroll-y>
      <view v-if="deletedNotes.length === 0" class="empty-state">
        <text class="empty-icon">🗑️</text>
        <text class="empty-text">回收站是空的</text>
      </view>

      <view v-for="note in deletedNotes" :key="note.id" class="note-card" @longpress="showNoteMenu(note)">
        <view class="note-content">
          <view class="note-title" :class="{ empty: !note.title }">
            {{ note.title || '无标题' }}
          </view>
          <view v-if="note.content" class="note-preview ellipsis-2">
            {{ note.content }}
          </view>
          <view class="note-footer">
            <text class="delete-time">已删除 {{ formatDateShort(note.deletedAt!) }}</text>
          </view>
        </view>
        <view class="note-actions">
          <view class="action-btn restore" @click.stop="restoreNote(note)">
            <text>恢复</text>
          </view>
          <view class="action-btn delete" @click.stop="permanentlyDelete(note)">
            <text>删除</text>
          </view>
        </view>
      </view>

      <view v-if="deletedNotes.length > 0" class="bottom-action">
        <view class="clear-all-btn" @click="clearAll">
          <text>清空回收站</text>
        </view>
      </view>

      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useNotesStore } from '@/stores/notes'
import type { Note } from '@/types'
import { formatDateShort } from '@/utils/id'

const notesStore = useNotesStore()

const deletedNotes = computed(() => notesStore.deletedNotes)

const restoreNote = (note: Note) => {
  uni.showModal({
    title: '恢复笔记',
    content: '确定要恢复这条笔记吗？',
    success: (res) => {
      if (res.confirm) {
        notesStore.restoreNote(note.id)
        uni.showToast({ title: '已恢复', icon: 'success' })
      }
    }
  })
}

const permanentlyDelete = (note: Note) => {
  uni.showModal({
    title: '永久删除',
    content: '此操作不可恢复，确定要永久删除吗？',
    confirmColor: '#EF5350',
    success: (res) => {
      if (res.confirm) {
        notesStore.permanentlyDeleteNote(note.id)
        uni.showToast({ title: '已永久删除', icon: 'success' })
      }
    }
  })
}

const clearAll = () => {
  uni.showModal({
    title: '清空回收站',
    content: `确定要清空所有 ${deletedNotes.value.length} 条已删除笔记吗？此操作不可恢复。`,
    confirmColor: '#EF5350',
    success: (res) => {
      if (res.confirm) {
        deletedNotes.value.forEach(note => {
          notesStore.permanentlyDeleteNote(note.id)
        })
        uni.showToast({ title: '已清空', icon: 'success' })
      }
    }
  })
}

const showNoteMenu = (note: Note) => {
  uni.showActionSheet({
    itemList: ['恢复', '永久删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        restoreNote(note)
      } else {
        permanentlyDelete(note)
      }
    }
  })
}

const goBack = () => {
  uni.switchTab({ url: '/pages/settings/index' })
}
</script>

<style lang="scss" scoped>
.page-header {
  display: flex;
  align-items: center;
  padding: $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-md});
  background: $card-bg;
}

.back-btn {
  font-size: $font-size-xxl;
  color: $text-secondary;
  padding: 0 $spacing-sm;
  margin-right: $spacing-sm;
  line-height: 1;
}

.header-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-right: $spacing-sm;
}

.header-subtitle {
  font-size: $font-size-sm;
  color: $text-hint;
}

.trash-list {
  height: calc(100vh - 200rpx);
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
  }
}

.note-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  background: $card-bg;
  border-radius: $radius-lg;
  margin-bottom: $spacing-md;
}

.note-content {
  flex: 1;
  margin-right: $spacing-md;
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
  margin-top: $spacing-sm;
}

.delete-time {
  font-size: $font-size-xs;
  color: $text-hint;
}

.note-actions {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.action-btn {
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-md;
  font-size: $font-size-sm;
  
  &.restore {
    background: rgba($primary-color, 0.1);
    color: $primary-color;
  }
  
  &.delete {
    background: rgba($error-color, 0.1);
    color: $error-color;
  }
  
  &:active {
    opacity: 0.6;
  }
}

.bottom-action {
  padding: $spacing-md;
  text-align: center;
}

.clear-all-btn {
  padding: $spacing-sm $spacing-lg;
  background: rgba($error-color, 0.1);
  color: $error-color;
  border-radius: 100rpx;
  font-size: $font-size-sm;
  display: inline-block;
  
  &:active {
    background: rgba($error-color, 0.2);
  }
}

.list-footer {
  height: 80rpx;
}
</style>