<template>
  <view class="container">
    <view class="page-header">
      <view class="header-title">设置</view>
    </view>

    <scroll-view class="settings-list" scroll-y>
      <view class="settings-section">
        <view class="section-title">数据管理</view>
        <view class="settings-item" @click="handleExportBackup">
          <view class="item-left">
            <text class="item-icon">📤</text>
            <text class="item-text">导出备份</text>
          </view>
          <view class="item-right">
            <text class="item-hint">导出所有笔记数据</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
        <view class="settings-item" @click="handleImportFile">
          <view class="item-left">
            <text class="item-icon">📥</text>
            <text class="item-text">导入文件</text>
          </view>
          <view class="item-right">
            <text class="item-hint">从备份文件恢复数据</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="settings-section">
        <view class="section-title">功能入口</view>
        <view class="settings-item" @click="goToTrash">
          <view class="item-left">
            <text class="item-icon">🗑️</text>
            <text class="item-text">回收站</text>
          </view>
          <view class="item-right">
            <text class="item-hint">{{ deletedNotes.length }} 条已删除</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
        <view class="settings-item" @click="goToTags">
          <view class="item-left">
            <text class="item-icon">🏷️</text>
            <text class="item-text">标签管理</text>
          </view>
          <view class="item-right">
            <text class="item-hint">管理标签组</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="settings-section">
        <view class="section-title">关于</view>
        <view class="settings-item" @click="goToAbout">
          <view class="item-left">
            <text class="item-icon">ℹ️</text>
            <text class="item-text">关于清笺</text>
          </view>
          <view class="item-right">
            <text class="item-hint">版本 1.0.0</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
        <view class="settings-item" @click="goToPrivacy">
          <view class="item-left">
            <text class="item-icon">🔒</text>
            <text class="item-text">隐私政策</text>
          </view>
          <view class="item-right">
            <text class="item-arrow">›</text>
          </view>
        </view>
        <view class="settings-item" @click="goToTerms">
          <view class="item-left">
            <text class="item-icon">📄</text>
            <text class="item-text">用户须知</text>
          </view>
          <view class="item-right">
            <text class="item-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="settings-section">
        <view class="settings-item danger" @click="clearAllData">
          <view class="item-left">
            <text class="item-icon">🗑️</text>
            <text class="item-text">清空所有数据</text>
          </view>
          <view class="item-right">
            <text class="item-hint">此操作不可恢复</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="version-info">
        <text>清笺 v1.0.0</text>
        <text class="version-sub">完全本地化的笔记应用</text>
      </view>

      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useNotesStore } from '@/stores/notes'
import { exportBackup, importBackup as doImportBackup } from '@/utils/backup'
import { clearAllStorage } from '@/utils/storage'

const notesStore = useNotesStore()

const deletedNotes = computed(() => notesStore.deletedNotes)

const handleExportBackup = async () => {
  const json = exportBackup()
  const electronAPI = typeof window !== 'undefined' ? (window as any).electronAPI : null
  if (electronAPI) {
    const result = await electronAPI.showSaveDialog()
    if (!result.canceled && result.filePath) {
      const fs = require('fs')
      fs.writeFileSync(result.filePath, json, 'utf-8')
      uni.showToast({ title: '已导出', icon: 'success' })
    }
  } else {
    uni.setClipboardData({
      data: json,
      success: () => {
        uni.showToast({ title: '已复制到剪贴板', icon: 'success' })
      }
    })
  }
}

const handleImportFile = async () => {
  const electronAPI = typeof window !== 'undefined' ? (window as any).electronAPI : null
  if (electronAPI) {
    const result = await electronAPI.showOpenDialog()
    if (!result.canceled && result.filePaths.length > 0) {
      const fs = require('fs')
      const json = fs.readFileSync(result.filePaths[0], 'utf-8')
      const success = doImportBackup(json)
      if (success) {
        notesStore.loadFromStorage()
        uni.showToast({ title: '导入成功', icon: 'success' })
      }
    }
  } else {
    uni.showModal({
      title: '导入文件',
      content: '请粘贴备份数据：',
      editable: true,
      placeholderText: '在此粘贴JSON备份数据',
      confirmText: '导入',
      success: (res) => {
        if (res.confirm && res.content) {
          const success = doImportBackup(res.content)
          if (success) {
            notesStore.loadFromStorage()
            uni.showToast({ title: '导入成功', icon: 'success' })
          }
        }
      }
    })
  }
}

const goToTrash = () => {
  uni.navigateTo({ url: '/pages/trash/index' })
}

const goToTags = () => {
  uni.navigateTo({ url: '/pages/tags/index' })
}

const goToAbout = () => {
  uni.navigateTo({ url: '/pages/about/index' })
}

const goToPrivacy = () => {
  uni.navigateTo({ url: '/pages/privacy/index' })
}

const goToTerms = () => {
  uni.navigateTo({ url: '/pages/terms/index' })
}

const clearAllData = () => {
  uni.showModal({
    title: '清空数据',
    content: '确定要清空所有数据吗？此操作不可恢复！',
    confirmColor: '#EF5350',
    success: (res) => {
      if (res.confirm) {
        clearAllStorage()
        notesStore.loadFromStorage()
        uni.showToast({ title: '已清空', icon: 'success' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.settings-list {
  height: calc(100vh - 200rpx);
}

.settings-section {
  margin: $spacing-md;
  background: $card-bg;
  border-radius: $radius-lg;
  overflow: hidden;
}

.section-title {
  padding: $spacing-md $spacing-lg $spacing-sm;
  font-size: $font-size-xs;
  color: $text-hint;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  border-bottom: 1rpx solid $border-color;
  
  &:last-child {
    border-bottom: none;
  }
  
  &.danger {
    .item-text {
      color: $error-color;
    }
  }
  
  &:active {
    background: $bg-color;
  }
}

.item-left {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.item-icon {
  font-size: $font-size-xl;
}

.item-text {
  font-size: $font-size-base;
  color: $text-primary;
}

.item-right {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.item-hint {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.item-arrow {
  font-size: $font-size-lg;
  color: $text-hint;
}

.version-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-xl;
  
  text {
    font-size: $font-size-sm;
    color: $text-hint;
  }
  
  .version-sub {
    font-size: $font-size-xs;
    margin-top: $spacing-xs;
  }
}

.list-footer {
  height: 80rpx;
}
</style>