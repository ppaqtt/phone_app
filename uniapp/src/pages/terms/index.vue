<template>
  <view class="container">
    <view class="page-header">
      <text class="back-btn" @click="goBack">‹</text>
      <view class="header-title">用户须知</view>
    </view>

    <scroll-view class="content" scroll-y>
      <view class="section">
        <view class="section-title">欢迎使用清笺</view>
        <view class="section-body">
          感谢您选择清笺作为您的笔记工具。在使用本应用前，请您仔细阅读以下用户须知。使用本应用即表示您同意遵守以下条款。
        </view>
      </view>

      <view class="section">
        <view class="section-title">服务说明</view>
        <view class="section-body">
          清笺是一款完全本地化的笔记应用，提供笔记记录、分类管理、标签管理、全文搜索、数据备份等功能。所有数据处理均在您的设备本地完成。
        </view>
      </view>

      <view class="section">
        <view class="section-title">数据安全</view>
        <view class="section-body">
          <view class="list">
            <view class="list-item">请定期导出备份文件，以防设备损坏或数据丢失</view>
            <view class="list-item">备份文件请妥善保管，避免泄露个人隐私</view>
            <view class="list-item">锁定功能可保护笔记不被误编辑，但请注意备份</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">使用规范</view>
        <view class="section-body">
          <view class="list">
            <view class="list-item">请勿将本应用用于存储违法违规内容</view>
            <view class="list-item">请遵守当地法律法规使用本应用</view>
            <view class="list-item">不得对本应用进行反向工程或破解</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">免责声明</view>
        <view class="section-body">
          <view class="list">
            <view class="list-item">因用户未备份导致的数据丢失，我们不承担责任</view>
            <view class="list-item">因设备故障、系统升级等原因造成的数据损失，我们不承担责任</view>
            <view class="list-item">我们尽力保证应用稳定运行，但不承诺绝对无故障</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">更新与变更</view>
        <view class="section-body">
          我们可能会不定期更新本用户须知。更新后的条款将在应用内公布，继续使用即视为接受新条款。
        </view>
      </view>

      <view class="section">
        <view class="section-title">联系我们</view>
        <view class="section-body">
          如您对本用户须知有任何疑问，欢迎通过以下方式联系我们：
          <view class="contact">📧 pdpdxpz@qq.com</view>
        </view>
      </view>

      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
const goBack = () => {
  uni.navigateBack()
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