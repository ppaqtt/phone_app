<template>
  <view class="container">
    <view class="page-header">
      <text class="back-btn" @click="goBack">‹</text>
      <view class="header-title">设置</view>
    </view>

    <scroll-view class="settings-list" scroll-y>
      <view class="settings-section">
        <view class="section-title">安全设置</view>
        <view class="settings-item" @click="handleAppLock">
          <view class="item-left">
            <text class="item-icon">🔐</text>
            <text class="item-text">应用锁定</text>
          </view>
          <view class="item-right">
            <text class="item-hint">{{ notesStore.hasPin ? '已启用' : '未设置' }}</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
        <view v-if="notesStore.hasPin" class="settings-item" @click="handleRemovePin">
          <view class="item-left">
            <text class="item-icon">🔓</text>
            <text class="item-text">关闭锁定</text>
          </view>
          <view class="item-right">
            <text class="item-hint">移除 PIN 码</text>
            <text class="item-arrow">›</text>
          </view>
        </view>
      </view>

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
      await electronAPI.writeFile(result.filePath, json)
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
      const json = await electronAPI.readFile(result.filePaths[0])
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

const handleAppLock = () => {
  if (notesStore.hasPin) {
    uni.showModal({
      title: '应用锁定',
      content: '已设置 PIN 码，是否重新设置？',
      confirmText: '重新设置',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          notesStore.removePin()
          uni.navigateTo({ url: '/pages/lock/index?mode=set' })
        }
      }
    })
  } else {
    uni.navigateTo({ url: '/pages/lock/index?mode=set' })
  }
}

const handleRemovePin = () => {
  uni.showModal({
    title: '关闭锁定',
    content: '确定要关闭应用锁定功能吗？',
    confirmColor: '#EF5350',
    success: (res) => {
      if (res.confirm) {
        notesStore.removePin()
        uni.showToast({ title: '已关闭锁定', icon: 'success' })
      }
    }
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

const goBack = () => {
  uni.switchTab({ url: '/pages/notes/index' })
}
</script>

<style lang="scss" scoped>
/* 容器背景 - 浅灰渐变到白色 */
.container {
  background: linear-gradient(180deg, #E8EAF6 0%, #F5F5F5 50%, #FFFFFF 100%);
  min-height: 100vh;
}

/* 页面头部 - 渐变背景 */
.page-header {
  display: flex;
  align-items: center;
  padding: $spacing-lg $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-lg});
  background: linear-gradient(135deg, #5C6BC0 0%, #7986CB 50%, #9C27B0 100%);
  box-shadow: 0 4rpx 20rpx rgba(92, 107, 192, 0.25);
}

/* 返回按钮 - 圆角背景 */
.back-btn {
  font-size: 48rpx;
  color: #FFFFFF;
  padding: 0 $spacing-sm;
  margin-right: $spacing-sm;
  line-height: 1;
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0.15) 100%);
  border-radius: 20rpx;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 4rpx 12rpx rgba(255, 255, 255, 0.1),
    inset 0 2rpx 0 rgba(255, 255, 255, 0.3);
  
  &:active {
    transform: scale(0.85) rotate(-5deg);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.25) 100%);
  }
}

.header-title {
  font-size: 44rpx;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 3rpx;
  text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.2);
}

/* 设置列表滚动区域 */
.settings-list {
  height: calc(100vh - 200rpx);
  padding: $spacing-xl $spacing-md;
}

/* 设置分组 - 卡片样式 */
.settings-section {
  margin-bottom: $spacing-xl;
  background: linear-gradient(135deg, #FFFFFF 0%, rgba(255, 255, 255, 0.95) 100%);
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 
    0 12rpx 32rpx rgba(0, 0, 0, 0.12),
    0 6rpx 16rpx rgba(92, 107, 192, 0.08),
    0 3rpx 8rpx rgba(0, 0, 0, 0.06),
    inset 0 2rpx 0 rgba(255, 255, 255, 1);
  border: 2rpx solid rgba(92, 107, 192, 0.08);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 分组标题 - 主题色背景 */
.section-title {
  padding: $spacing-lg $spacing-xl $spacing-md;
  font-size: 36rpx;
  color: #FFFFFF;
  font-weight: 700;
  letter-spacing: 2rpx;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.12) 100%);
  box-shadow: 0 4rpx 12rpx rgba(92, 107, 192, 0.1);
}

/* 设置项 */
.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-xl $spacing-xl;
  border-bottom: 2rpx solid rgba(92, 107, 192, 0.08);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:last-child {
    border-bottom: none;
  }
  
  &.danger {
    .item-text {
      color: #EF5350;
    }
    
    .item-icon {
      background: linear-gradient(135deg, rgba(239, 83, 80, 0.15) 0%, rgba(239, 83, 80, 0.12) 100%);
    }
  }
  
  &:active {
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.06) 0%, rgba(156, 39, 176, 0.04) 100%);
    transform: scale(0.98);
  }
}

.item-left {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
}

/* 设置项图标 - 圆角背景 */
.item-icon {
  font-size: 40rpx;
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.12) 0%, rgba(156, 39, 176, 0.08) 100%);
  border-radius: 20rpx;
  box-shadow: 
    0 4rpx 12rpx rgba(92, 107, 192, 0.1),
    0 2rpx 6rpx rgba(0, 0, 0, 0.04);
}

.item-text {
  font-size: 36rpx;
  color: $text-primary;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.item-right {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.item-hint {
  font-size: 28rpx;
  color: $text-secondary;
  font-weight: 500;
  letter-spacing: 1rpx;
}

.item-arrow {
  font-size: 32rpx;
  color: rgba(92, 107, 192, 0.6);
  font-weight: 700;
}

/* 版本信息 */
.version-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-xl 0;
  
  text {
    font-size: 28rpx;
    color: rgba(92, 107, 192, 0.6);
    font-weight: 500;
    letter-spacing: 1rpx;
  }
  
  .version-sub {
    font-size: 24rpx;
    margin-top: $spacing-xs;
    color: rgba(156, 39, 176, 0.5);
    opacity: 0.8;
    letter-spacing: 2rpx;
  }
}

/* 列表底部间距 */
.list-footer {
  height: 100rpx;
}
</style>