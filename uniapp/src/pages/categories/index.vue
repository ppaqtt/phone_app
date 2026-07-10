<template>
  <view class="container">
    <view class="page-header">
      <view class="header-title">分类管理</view>
      <view class="header-subtitle">{{ categories.length }} 个分类</view>
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
</script>

<style lang="scss" scoped>
.category-list {
  height: calc(100vh - 200rpx);
  padding: 0 $spacing-md;
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  background: $card-bg;
  border-radius: $radius-lg;
  margin-bottom: $spacing-md;
}

.category-left {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.category-color {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
}

.category-info {
  display: flex;
  flex-direction: column;
}

.category-name {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.category-count {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.category-right {
  display: flex;
  gap: $spacing-md;
}

.action-btn {
  padding: $spacing-xs $spacing-md;
  font-size: $font-size-sm;
  color: $primary-color;
  
  &.danger {
    color: $error-color;
  }
  
  &:active {
    opacity: 0.6;
  }
}

.add-category {
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
