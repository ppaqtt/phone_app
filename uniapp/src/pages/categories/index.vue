<template>
  <view class="container">
    <view class="page-header">
      <text class="back-btn" @click="goBack">‹</text>
      <view class="header-content">
        <view class="header-title">分类管理</view>
        <view class="header-subtitle">{{ categories.length }} 个分类</view>
      </view>
    </view>

    <scroll-view class="category-list" scroll-y>
      <view v-for="cat in categories" :key="cat.id" class="category-item">
        <view class="category-left">
          <view class="category-color" :style="{ background: cat.color }"></view>
          <view class="category-info">
            <text class="category-name">{{ cat.name }}</text>
            <text class="category-count">{{ getNoteCount(cat.id) }} 条笔记</text>
          </view>
        </view>
        <view class="category-right">
          <view class="action-btn" @click="editCategory(cat)">
            <text>编辑</text>
          </view>
          <view 
            v-if="cat.id !== defaultCategory?.id" 
            class="action-btn danger" 
            @click="deleteCategory(cat)"
          >
            <text>删除</text>
          </view>
        </view>
      </view>

      <view class="add-category" @click="showAddDialog">
        <text class="add-icon">+</text>
        <text class="add-text">添加分类</text>
      </view>

      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useNotesStore } from '@/stores/notes'
import type { Category } from '@/types'
import { CATEGORY_COLORS } from '@/types'

const notesStore = useNotesStore()
const showDialog = ref(false)
const editingCategory = ref<Category | null>(null)
const categoryName = ref('')
const selectedColor = ref('')

const categories = computed(() => notesStore.categories)
const defaultCategory = computed(() => categories.value[0])

const getNoteCount = (catId: string): number => {
  return notesStore.activeNotes.filter(n => n.categoryId === catId).length
}

const showAddDialog = () => {
  editingCategory.value = null
  categoryName.value = ''
  selectedColor.value = CATEGORY_COLORS[categories.value.length % CATEGORY_COLORS.length]
  showDialog.value = true
}

const editCategory = (cat: Category) => {
  editingCategory.value = cat
  categoryName.value = cat.name
  selectedColor.value = cat.color
  showDialog.value = true
}

const saveCategory = () => {
  const name = categoryName.value.trim()
  if (!name) {
    uni.showToast({ title: '请输入分类名称', icon: 'none' })
    return
  }

  if (editingCategory.value) {
    notesStore.updateCategory(editingCategory.value.id, { name, color: selectedColor.value })
    uni.showToast({ title: '更新成功', icon: 'success' })
  } else {
    notesStore.addCategory(name, selectedColor.value)
    uni.showToast({ title: '创建成功', icon: 'success' })
  }

  showDialog.value = false
}

const deleteCategory = (cat: Category) => {
  uni.showModal({
    title: '删除分类',
    content: `确定要删除分类"${cat.name}"吗？该分类下的笔记将变为无分类。`,
    success: (res) => {
      if (res.confirm) {
        notesStore.deleteCategory(cat.id)
        uni.showToast({ title: '已删除', icon: 'success' })
      }
    }
  })
}

const goBack = () => {
  uni.switchTab({ url: '/pages/notes/index' })
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

.header-content {
  flex: 1;
}

.header-title {
  font-size: $font-size-xl;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 2rpx;
}

.header-subtitle {
  font-size: $font-size-sm;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 4rpx;
}

.category-list {
  height: calc(100vh - 180rpx);
  padding: $spacing-lg;
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
    background: linear-gradient(180deg, #5C6BC0 0%, #9C27B0 100%);
  }

  &:active {
    transform: translateY(-2rpx);
    box-shadow: 
      0 8rpx 32rpx rgba(0, 0, 0, 0.1),
      0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  }
}

.category-left {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  flex: 1;
}

.category-color {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.15);
  flex-shrink: 0;
}

.category-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.category-name {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  letter-spacing: 1rpx;
}

.category-count {
  font-size: $font-size-sm;
  color: $text-hint;
  background: rgba(92, 107, 192, 0.08);
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  display: inline-block;
  width: fit-content;
}

.category-right {
  display: flex;
  gap: $spacing-sm;
}

.action-btn {
  padding: $spacing-sm $spacing-md;
  font-size: $font-size-sm;
  color: #5C6BC0;
  border-radius: 20rpx;
  background: rgba(92, 107, 192, 0.08);
  font-weight: 500;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &.danger {
    color: #EF5350;
    background: rgba(239, 83, 80, 0.08);
  }

  &:active {
    transform: scale(0.95);
  }
}

.add-category {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-xl;
  background: rgba(255, 255, 255, 0.8);
  border: 3rpx dashed rgba(92, 107, 192, 0.3);
  border-radius: 24rpx;
  margin-bottom: $spacing-md;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(10px);

  &:active {
    background: rgba(92, 107, 192, 0.06);
    border-color: #5C6BC0;
    transform: scale(0.98);
  }
}

.add-icon {
  font-size: 44rpx;
  color: #5C6BC0;
  font-weight: 300;
}

.add-text {
  font-size: $font-size-base;
  color: #5C6BC0;
  font-weight: 500;
  letter-spacing: 1rpx;
}

.list-footer {
  height: 120rpx;
}
</style>
