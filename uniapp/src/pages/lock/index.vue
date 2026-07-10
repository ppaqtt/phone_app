<template>
  <view class="lock-container">
    <!-- 锁定状态背景 -->
    <view class="lock-bg">
      <view class="lock-pattern"></view>
    </view>

    <!-- 主内容区 -->
    <view class="lock-content">
      <!-- 顶部图标 -->
      <view class="lock-icon-wrapper">
        <view class="lock-icon">
          <text class="lock-emoji">🔐</text>
        </view>
        <view class="lock-shine"></view>
      </view>

      <!-- 标题 -->
      <view class="lock-title">
        <text class="title-main">{{ pageTitle }}</text>
        <text class="title-sub">{{ pageSubtitle }}</text>
      </view>

      <!-- PIN 输入区 -->
      <view class="pin-section">
        <view class="pin-dots">
          <view
            v-for="index in pinLength"
            :key="index"
            :class="['pin-dot', { filled: index <= currentPin.length, active: index === currentPin.length + 1 }]"
          >
            <text v-if="index <= currentPin.length" class="dot-inner">●</text>
            <text v-else class="dot-inner">○</text>
          </view>
        </view>

        <!-- 错误提示 -->
        <view v-if="errorMsg" class="error-msg">
          <text class="error-text">{{ errorMsg }}</text>
        </view>

        <!-- 数字键盘 -->
        <view class="numpad">
          <view
            v-for="num in numpadKeys"
            :key="num"
            :class="['numpad-key', { special: num === 'delete' || num === 'forgot' }]"
            @click="handleNumpadClick(num)"
          >
            <text v-if="num === 'delete'" class="key-text">⌫</text>
            <text v-else-if="num === 'forgot'" class="key-text forgot">忘记密码</text>
            <text v-else class="key-text">{{ num }}</text>
          </view>
        </view>
      </view>

      <!-- 底部提示 -->
      <view class="lock-hint">
        <text class="hint-text">{{ hintText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useNotesStore } from '@/stores/notes'

const notesStore = useNotesStore()

const currentPin = ref('')
const confirmPin = ref('')
const pinLength = ref(6)
const errorMsg = ref('')
const mode = ref<'verify' | 'set' | 'confirm'>('verify')

const numpadKeys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'forgot', '0', 'delete']

const pageTitle = computed(() => {
  if (mode.value === 'verify') return '应用锁定'
  if (mode.value === 'set') return '设置密码'
  return '确认密码'
})

const pageSubtitle = computed(() => {
  if (mode.value === 'verify') return '请输入 PIN 码解锁应用'
  if (mode.value === 'set') return '请输入 4-6 位数字密码'
  return '请再次输入密码确认'
})

const hintText = computed(() => {
  if (mode.value === 'verify') return 'PIN 码用于保护您的隐私数据'
  if (mode.value === 'set') return '密码长度为 4-6 位数字'
  return '两次密码必须一致'
})

onMounted(() => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  const options = (currentPage as any).$page?.options || {}

  if (options.mode === 'set') {
    mode.value = 'set'
  } else if (notesStore.hasPin) {
    mode.value = 'verify'
  } else {
    mode.value = 'set'
  }
})

const handleNumpadClick = (key: string) => {
  if (key === 'forgot') {
    handleForgotPin()
    return
  }

  if (key === 'delete') {
    currentPin.value = currentPin.value.slice(0, -1)
    errorMsg.value = ''
    return
  }

  if (currentPin.value.length < pinLength.value) {
    currentPin.value += key
    errorMsg.value = ''

    // 检查是否达到最小长度
    if (currentPin.value.length === 6 || (currentPin.value.length >= 4 && mode.value !== 'verify')) {
      setTimeout(() => {
        handlePinSubmit()
      }, 300)
    }
  }
}

const handlePinSubmit = () => {
  if (mode.value === 'verify') {
    if (notesStore.verifyPin(currentPin.value)) {
      notesStore.unlockApp()
      uni.showToast({ title: '解锁成功', icon: 'success' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/notes/index' })
      }, 500)
    } else {
      errorMsg.value = '密码错误，请重试'
      currentPin.value = ''
    }
  } else if (mode.value === 'set') {
    if (currentPin.value.length < 4) {
      errorMsg.value = '密码至少需要 4 位'
      currentPin.value = ''
      return
    }
    confirmPin.value = currentPin.value
    currentPin.value = ''
    mode.value = 'confirm'
  } else if (mode.value === 'confirm') {
    if (currentPin.value === confirmPin.value) {
      if (notesStore.setPin(currentPin.value)) {
        uni.showToast({ title: '密码设置成功', icon: 'success' })
        setTimeout(() => {
          uni.switchTab({ url: '/pages/settings/index' })
        }, 500)
      } else {
        errorMsg.value = '设置失败，请重试'
        currentPin.value = ''
      }
    } else {
      errorMsg.value = '两次密码不一致'
      currentPin.value = ''
      mode.value = 'set'
    }
  }
}

const handleForgotPin = () => {
  uni.showModal({
    title: '忘记密码',
    content: '忘记 PIN 码将清除所有数据，确定继续吗？',
    confirmColor: '#EF5350',
    confirmText: '清除数据',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm) {
        notesStore.removePin()
        uni.showToast({ title: '已清除密码', icon: 'success' })
        setTimeout(() => {
          uni.switchTab({ url: '/pages/notes/index' })
        }, 500)
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.lock-container {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.lock-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;

  .lock-pattern {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image: 
      radial-gradient(circle at 20% 50%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
      radial-gradient(circle at 80% 80%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
      radial-gradient(circle at 40% 20%, rgba(255, 255, 255, 0.05) 0%, transparent 30%);
    animation: patternMove 20s ease-in-out infinite;
  }
}

@keyframes patternMove {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.8; }
}

.lock-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: calc(env(safe-area-inset-top) + 80rpx) 60rpx calc(env(safe-area-inset-bottom) + 80rpx);
  height: 100%;
}

.lock-icon-wrapper {
  position: relative;
  margin-bottom: 60rpx;

  .lock-icon {
    width: 160rpx;
    height: 160rpx;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 
      0 20rpx 40rpx rgba(0, 0, 0, 0.2),
      inset 0 2rpx 4rpx rgba(255, 255, 255, 0.3);

    .lock-emoji {
      font-size: 80rpx;
      animation: lockBounce 2s ease-in-out infinite;
    }
  }

  .lock-shine {
    position: absolute;
    top: -20rpx;
    left: -20rpx;
    right: -20rpx;
    bottom: -20rpx;
    border-radius: 50%;
    background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.3) 0%, transparent 60%);
    animation: shineRotate 10s linear infinite;
  }
}

@keyframes lockBounce {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

@keyframes shineRotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.lock-title {
  text-align: center;
  margin-bottom: 80rpx;

  .title-main {
    font-size: 48rpx;
    font-weight: 600;
    color: #FFFFFF;
    margin-bottom: 16rpx;
    display: block;
    letter-spacing: 2rpx;
  }

  .title-sub {
    font-size: 28rpx;
    color: rgba(255, 255, 255, 0.7);
    display: block;
    letter-spacing: 1rpx;
  }
}

.pin-section {
  width: 100%;
  max-width: 600rpx;
}

.pin-dots {
  display: flex;
  justify-content: center;
  gap: 32rpx;
  margin-bottom: 40rpx;

  .pin-dot {
    width: 48rpx;
    height: 48rpx;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s ease;
    border: 2rpx solid rgba(255, 255, 255, 0.3);

    &.filled {
      background: rgba(255, 255, 255, 0.3);
      transform: scale(1.1);
      border-color: rgba(255, 255, 255, 0.5);
    }

    &.active {
      border-color: rgba(255, 255, 255, 0.8);
      animation: dotPulse 1.5s ease-in-out infinite;
    }

    .dot-inner {
      font-size: 24rpx;
      color: #FFFFFF;
    }
  }
}

@keyframes dotPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.error-msg {
  text-align: center;
  margin-bottom: 32rpx;
  animation: errorShake 0.5s ease;

  .error-text {
    font-size: 28rpx;
    color: #FF6B6B;
    font-weight: 500;
  }
}

@keyframes errorShake {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-10rpx); }
  40%, 80% { transform: translateX(10rpx); }
}

.numpad {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
  padding: 40rpx;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 40rpx;
  box-shadow: inset 0 2rpx 4rpx rgba(255, 255, 255, 0.2);

  .numpad-key {
    height: 100rpx;
    background: rgba(255, 255, 255, 0.2);
    border-radius: 24rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s ease;
    box-shadow: 
      0 4rpx 8rpx rgba(0, 0, 0, 0.15),
      inset 0 1rpx 2rpx rgba(255, 255, 255, 0.3);

    &:active {
      transform: scale(0.95);
      background: rgba(255, 255, 255, 0.3);
    }

    &.special {
      background: rgba(255, 255, 255, 0.12);
    }

    .key-text {
      font-size: 40rpx;
      color: #FFFFFF;
      font-weight: 500;
      user-select: none;

      &.forgot {
        font-size: 24rpx;
        color: rgba(255, 255, 255, 0.6);
      }
    }
  }
}

.lock-hint {
  text-align: center;
  margin-top: 60rpx;

  .hint-text {
    font-size: 24rpx;
    color: rgba(255, 255, 255, 0.5);
    letter-spacing: 1rpx;
  }
}
</style>