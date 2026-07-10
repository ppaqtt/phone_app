<template>
  <view class="container">
    <view class="page-header">
      <text class="back-btn" @click="goBack">‹</text>
      <view class="header-title">关于清笺</view>
    </view>

    <view class="about-header">
      <view class="app-icon">📝</view>
      <view class="app-name">清笺</view>
      <view class="app-slogan">完全本地化的笔记应用</view>
      <view class="app-version">版本 1.0.0</view>
    </view>

    <scroll-view class="about-content" scroll-y>
      <view class="about-section">
        <view class="section-title">关于清笺</view>
        <view class="section-content">
          <text>清笺是一款专注于隐私保护的笔记应用，所有数据仅存储在您的设备本地，不会上传到任何云端服务器。</text>
        </view>
      </view>

      <view class="about-section">
        <view class="section-title">主要功能</view>
        <view class="feature-list">
          <view class="feature-item">
            <text class="feature-icon">📄</text>
            <text class="feature-text">支持富文本编辑</text>
          </view>
          <view class="feature-item">
            <text class="feature-icon">🔒</text>
            <text class="feature-text">笔记锁定保护</text>
          </view>
          <view class="feature-item">
            <text class="feature-icon">📁</text>
            <text class="feature-text">分类与标签管理</text>
          </view>
          <view class="feature-item">
            <text class="feature-icon">🔍</text>
            <text class="feature-text">全文搜索</text>
          </view>
          <view class="feature-item">
            <text class="feature-icon">💾</text>
            <text class="feature-text">数据备份与恢复</text>
          </view>
          <view class="feature-item">
            <text class="feature-icon">🗑️</text>
            <text class="feature-text">回收站功能</text>
          </view>
        </view>
      </view>

      <view class="about-section">
        <view class="section-title">联系方式</view>
        <view class="contact-list">
          <view class="contact-item" @click="openEmail">
            <text class="contact-icon">📧</text>
            <text class="contact-text">联系我们</text>
            <text class="contact-value">ppdxpz@qq.com</text>
          </view>
        </view>
      </view>

      <view class="about-section">
        <view class="section-title">开源协议</view>
        <view class="section-content">
          <text>本应用基于 MIT 开源协议。</text>
        </view>
      </view>

      <view class="about-section">
        <view class="section-title">用户协议</view>
        <view class="section-content">
          <text>使用本应用即表示您同意我们的隐私政策和服务条款。</text>
        </view>
      </view>

      <view class="list-footer"></view>
    </scroll-view>

    <view class="bottom-action">
      <view class="feedback-btn" @click="showFeedback">
        <text>💡 反馈建议</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
const openEmail = () => {
  uni.showToast({ title: 'ppdxpz@qq.com', icon: 'none' })
}

const showFeedback = () => {
  uni.showModal({
    title: '反馈建议',
    content: '感谢您的反馈！如有任何问题或建议，请发送邮件至 ppdxpz@qq.com',
    showCancel: false
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
}

.about-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-xl $spacing-lg;
  background: linear-gradient(135deg, $primary-color 0%, $primary-dark 100%);
  
  .app-icon {
    font-size: 160rpx;
    margin-bottom: $spacing-lg;
  }
  
  .app-name {
    font-size: $font-size-xxl;
    font-weight: 700;
    color: #FFFFFF;
    margin-bottom: $spacing-xs;
  }
  
  .app-slogan {
    font-size: $font-size-sm;
    color: rgba(255, 255, 255, 0.8);
    margin-bottom: $spacing-md;
  }
  
  .app-version {
    font-size: $font-size-xs;
    color: rgba(255, 255, 255, 0.6);
    padding: $spacing-xs $spacing-md;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 100rpx;
  }
}

.about-content {
  height: calc(100vh - 400rpx);
}

.about-section {
  margin: $spacing-md;
  padding: $spacing-lg;
  background: $card-bg;
  border-radius: $radius-lg;
}

.section-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.section-content {
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: 1.8;
}

.feature-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.feature-icon {
  font-size: $font-size-lg;
}

.feature-text {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.contact-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  
  &:active {
    opacity: 0.6;
  }
}

.contact-icon {
  font-size: $font-size-lg;
}

.contact-text {
  font-size: $font-size-sm;
  color: $text-primary;
}

.contact-value {
  font-size: $font-size-sm;
  color: $text-secondary;
  margin-left: auto;
}

.bottom-action {
  padding: $spacing-md;
  padding-bottom: calc(env(safe-area-inset-bottom) + #{$spacing-md});
}

.feedback-btn {
  padding: $spacing-md;
  background: $primary-color;
  color: #FFFFFF;
  border-radius: $radius-lg;
  text-align: center;
  font-size: $font-size-base;
  font-weight: 500;
  
  &:active {
    background: $primary-dark;
    opacity: 0.9;
  }
}

.list-footer {
  height: 40rpx;
}
</style>