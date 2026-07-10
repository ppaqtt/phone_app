<template>
  <view class="container">
    <view class="page-header">
      <text class="back-btn" @click="goBack">‹</text>
      <view class="header-title">隐私政策</view>
    </view>

    <scroll-view class="content" scroll-y>
      <view class="section">
        <view class="section-title">引言</view>
        <view class="section-body">
          清笺是一款完全本地化的笔记应用。我们深知个人信息对您的重要性，因此制定了本隐私政策，帮助您了解我们如何收集、使用和保护您的信息。本政策适用于清笺应用的所有版本。
        </view>
      </view>

      <view class="section">
        <view class="section-title">信息收集</view>
        <view class="section-body">
          清笺不会收集、存储或上传您的任何个人数据到云端服务器。所有笔记内容、分类、标签等数据仅存储在您的设备本地。我们不会：
          <view class="list">
            <view class="list-item">收集您的姓名、邮箱、手机号等个人信息</view>
            <view class="list-item">上传您的笔记内容到任何服务器</view>
            <view class="list-item">收集您的设备信息或使用数据</view>
            <view class="list-item">使用任何第三方分析或追踪工具</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">数据存储</view>
        <view class="section-body">
          所有数据均存储在您的设备本地存储空间中。您可以随时导出备份文件，或将备份文件导入恢复到应用中。数据完全由您掌控。
        </view>
      </view>

      <view class="section">
        <view class="section-title">权限说明</view>
        <view class="section-body">
          清笺在运行过程中可能需要以下权限：
          <view class="list">
            <view class="list-item"><text class="bold">存储权限</text>：用于保存笔记数据和备份文件</view>
            <view class="list-item"><text class="bold">剪贴板权限</text>：用于复制和粘贴笔记内容</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">第三方SDK</view>
        <view class="section-body">
          清笺不使用任何第三方SDK或统计服务。所有功能均为原生实现，确保您的数据不会通过第三方渠道泄露。
        </view>
      </view>

      <view class="section">
        <view class="section-title">您的权利</view>
        <view class="section-body">
          您对自己的数据拥有完全的控制权：
          <view class="list">
            <view class="list-item">随时查看、编辑或删除笔记内容</view>
            <view class="list-item">导出备份文件进行本地存档</view>
            <view class="list-item">导入备份文件恢复数据</view>
            <view class="list-item">清空所有应用数据</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">联系我们</view>
        <view class="section-body">
          如您对本隐私政策有任何疑问或建议，欢迎通过以下方式联系我们：
          <view class="contact">📧 ppdxpz@qq.com</view>
        </view>
      </view>

      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
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
  border-bottom: 1rpx solid $border-color;
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

.content {
  height: calc(100vh - 120rpx);
  padding: $spacing-md;
}

.section {
  margin-bottom: $spacing-lg;
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

.section-body {
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: 1.8;
}

.list {
  margin-top: $spacing-md;
}

.list-item {
  position: relative;
  padding-left: $spacing-md;
  margin-bottom: $spacing-sm;
  line-height: 1.6;

  &::before {
    content: '•';
    position: absolute;
    left: 0;
    color: $primary-color;
  }
}

.bold {
  font-weight: 600;
  color: $text-primary;
}

.contact {
  margin-top: $spacing-md;
  padding: $spacing-md;
  background: $bg-color;
  border-radius: $radius-md;
  font-size: $font-size-base;
  color: $primary-color;
  text-align: center;
}

.list-footer {
  height: 80rpx;
}
</style>