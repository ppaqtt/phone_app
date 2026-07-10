<template>
  <view class="container">
    <view class="page-header">
      <view class="header-title">设置</view>
    </view>

    <scroll-view class="settings-list" scroll-y>
      <view class="settings-section">
        <view class="section-title">数据管理</view>
        <view class="settings-item" @click="exportBackup">
          <view class="item-left">
            <text class="item-icon">📤</text>
            <text class="item-text">导出备份</text>
          </view>
          <view class="item-right">
            <text class="item-hint">导出所有笔记数据</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
        <view class="settings-item" @click="importBackup">
          <view class="item-left">
            <text class="item-icon">📥</text>
            <text class="item-text">导入备份</text>
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
        <view class="settings-item" @click="showPrivacyPolicy">
          <view class="item-left">
            <text class="item-icon">🔒</text>
            <text class="item-text">隐私政策</text>
          </view>
          <view class="item-right">
            <text class="item-arrow">›</text>
          </view>
        </view>
        <view class="settings-item" @click="showTermsOfService">
          <view class="item-left">
            <text class="item-icon">📄</text>
            <text class="item-text">服务条款</text>
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

const exportBackup = () => {
  const json = exportBackup()
  uni.showModal({
    title: '导出备份',
    content: '备份数据已生成，是否复制到剪贴板？',
    confirmText: '复制',
    success: (res) => {
      if (res.confirm) {
        uni.setClipboardData({
          data: json,
          success: () => {
            uni.showToast({ title: '已复制', icon: 'success' })
          }
        })
      }
    }
  })
}

const importBackup = () => {
  uni.showModal({
    title: '导入备份',
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

const goToTrash = () => {
  uni.navigateTo({ url: '/pages/trash/index' })
}

const goToTags = () => {
  uni.navigateTo({ url: '/pages/tags/index' })
}

const goToAbout = () => {
  uni.navigateTo({ url: '/pages/about/index' })
}

const showPrivacyPolicy = () => {
  uni.showModal({
    title: '隐私政策',
    content: '清笺是一款完全本地化的笔记应用。我们不会收集、存储或上传您的任何个人数据。所有笔记内容仅存储在您的设备本地。',
    showCancel: false
  })
}

const showTermsOfService = () => {
  uni.showModal({
    title: '服务条款',
    content: '使用清笺即表示您同意我们的服务条款。我们致力于提供安全、可靠的笔记管理服务。',
    showCancel: false
  })
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