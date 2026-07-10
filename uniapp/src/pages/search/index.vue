<template>
  <view class="container">
    <view class="search-header">
      <view class="search-bar">
        <text class="back-btn" @click="goBack">‹</text>
        <text class="search-icon">🔍</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索笔记"
          @confirm="handleSearch"
          :focus="true"
        />
        <text v-if="keyword" class="clear-btn" @click="clearKeyword">×</text>
      </view>
    </view>

    <scroll-view class="search-results" scroll-y>
      <view v-if="!keyword" class="search-history">
        <view class="section-header">
          <text class="section-title">搜索历史</text>
          <text class="clear-all" @click="clearHistory">清空</text>
        </view>
        <view class="history-tags">
          <text 
            v-for="tag in searchHistory" 
            :key="tag" 
            class="history-tag"
            @click="search(tag)"
          >
            {{ tag }}
          </text>
        </view>
      </view>

      <view v-if="keyword && searchResults.length === 0" class="empty-state">
        <text class="empty-icon">🔍</text>
        <text class="empty-text">未找到相关笔记</text>
        <text class="empty-hint">试试其他关键词</text>
      </view>

      <view v-if="keyword && searchResults.length > 0" class="results-list">
        <view class="section-header">
          <text class="section-title">找到 {{ searchResults.length }} 条结果</text>
        </view>
        <view 
          v-for="note in searchResults" 
          :key="note.id"
          class="note-card"
          :style="{ background: note.color }"
          @click="editNote(note)"
        >
          <view v-if="note.isLocked" class="lock-badge">🔒</view>
          <view v-if="note.categoryId" class="note-category" :style="{ background: getCategoryColor(note.categoryId) }">
            {{ getCategoryName(note.categoryId) }}
          </view>
          <view class="note-title">
            {{ highlightText(note.title) }}
          </view>
          <view v-if="note.content" class="note-preview ellipsis-2">
            {{ highlightText(note.content) }}
          </view>
          <view class="note-footer">
            <text class="note-time">{{ formatDateShort(note.updatedAt) }}</text>
          </view>
        </view>
      </view>

      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useNotesStore } from '@/stores/notes'
import type { Note } from '@/types'
import { formatDateShort } from '@/utils/id'

const notesStore = useNotesStore()

const keyword = ref('')
const searchHistory = ref<string[]>([])

const searchResults = computed(() => {
  if (!keyword.value.trim()) return []
  const query = keyword.value.toLowerCase()
  return notesStore.activeNotes.filter(n => 
    n.title.toLowerCase().includes(query) ||
    n.content.toLowerCase().includes(query) ||
    n.tags.some(t => t.toLowerCase().includes(query))
  )
})

watch(keyword, (newVal) => {
  if (newVal.trim()) {
    handleSearch()
  }
})

const handleSearch = () => {
  const query = keyword.value.trim()
  if (query && !searchHistory.value.includes(query)) {
    searchHistory.value.unshift(query)
    if (searchHistory.value.length > 10) {
      searchHistory.value.pop()
    }
    uni.setStorageSync('qingjian_search_history', JSON.stringify(searchHistory.value))
  }
}

const search = (query: string) => {
  keyword.value = query
}

const clearKeyword = () => {
  keyword.value = ''
}

const clearHistory = () => {
  searchHistory.value = []
  uni.removeStorageSync('qingjian_search_history')
}

const getCategoryName = (id: string): string => {
  const cat = notesStore.getCategoryById(id)
  return cat?.name || ''
}

const getCategoryColor = (id: string): string => {
  const cat = notesStore.getCategoryById(id)
  return cat?.color || '#5C6BC0'
}

const highlightText = (text: string): string => {
  if (!keyword.value) return text
  const regex = new RegExp(`(${keyword.value})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

const editNote = (note: Note) => {
  uni.navigateTo({ url: `/pages/notes/edit?id=${note.id}` })
}

const goBack = () => {
  uni.switchTab({ url: '/pages/notes/index' })
}

const loadHistory = () => {
  const history = uni.getStorageSync('qingjian_search_history')
  if (history) {
    try {
      searchHistory.value = JSON.parse(history)
    } catch {}
  }
}

loadHistory()
</script>

<style lang="scss" scoped>
/* 容器背景 - 浅灰渐变到白色 */
.container {
  background: linear-gradient(180deg, #E8EAF6 0%, #F5F5F5 50%, #FFFFFF 100%);
  min-height: 100vh;
}

/* 搜索头部 */
.search-header {
  padding: $spacing-xl $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-xl});
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0.85) 100%);
  backdrop-filter: blur(30px);
  box-shadow: 0 4rpx 20rpx rgba(92, 107, 192, 0.12);
}

/* 搜索栏 - 毛玻璃效果 */
.search-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md $spacing-lg;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0.9) 100%);
  backdrop-filter: blur(40px);
  border-radius: 32rpx;
  border: 3rpx solid rgba(92, 107, 192, 0.15);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 8rpx 24rpx rgba(92, 107, 192, 0.12),
    0 4rpx 12rpx rgba(0, 0, 0, 0.06),
    inset 0 2rpx 0 rgba(255, 255, 255, 1);
  
  &:focus-within {
    border-color: #5C6BC0;
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.08) 0%, rgba(156, 39, 176, 0.06) 100%);
    box-shadow: 
      0 12rpx 32rpx rgba(92, 107, 192, 0.18),
      0 6rpx 16rpx rgba(156, 39, 176, 0.1),
      inset 0 2rpx 0 rgba(255, 255, 255, 1);
  }
}

/* 返回按钮 */
.back-btn {
  font-size: 48rpx;
  color: #5C6BC0;
  padding: 0 $spacing-xs;
  line-height: 1;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:active {
    transform: scale(0.8) rotate(-10deg);
    color: #9C27B0;
  }
}

.search-icon {
  font-size: 36rpx;
  opacity: 0.6;
  color: #5C6BC0;
}

/* 搜索输入框 */
.search-input {
  flex: 1;
  font-size: 36rpx;
  color: $text-primary;
  background: transparent;
  font-weight: 600;
  letter-spacing: 1rpx;
}

/* 清除按钮 */
.clear-btn {
  font-size: 36rpx;
  color: rgba(92, 107, 192, 0.6);
  padding: $spacing-xs;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.12) 0%, rgba(156, 39, 176, 0.08) 100%);
  border-radius: 50%;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2rpx 8rpx rgba(92, 107, 192, 0.1);
  
  &:active {
    transform: scale(0.85);
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.2) 0%, rgba(156, 39, 176, 0.15) 100%);
  }
}

/* 搜索结果列表 */
.search-results {
  height: calc(100vh - 240rpx);
  padding: $spacing-lg $spacing-md;
}

/* 搜索历史区域 */
.search-history {
  padding: $spacing-xl $spacing-md;
}

/* 分组头部 */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-xl;
}

/* 分组标题 */
.section-title {
  font-size: 40rpx;
  color: #5C6BC0;
  font-weight: 700;
  letter-spacing: 2rpx;
}

/* 清空全部按钮 */
.clear-all {
  font-size: 32rpx;
  color: #9C27B0;
  font-weight: 600;
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(135deg, rgba(156, 39, 176, 0.12) 0%, rgba(92, 107, 192, 0.08) 100%);
  border-radius: 24rpx;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  letter-spacing: 1rpx;
  box-shadow: 0 4rpx 12rpx rgba(156, 39, 176, 0.1);
  
  &:active {
    transform: scale(0.92);
    background: linear-gradient(135deg, rgba(156, 39, 176, 0.2) 0%, rgba(92, 107, 192, 0.12) 100%);
  }
}

/* 搜索历史标签列表 */
.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

/* 搜索历史标签 - 圆角胶囊样式 */
.history-tag {
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0.85) 100%);
  backdrop-filter: blur(20px);
  border-radius: 28rpx;
  font-size: 32rpx;
  color: $text-secondary;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 600;
  letter-spacing: 1rpx;
  border: 2rpx solid rgba(92, 107, 192, 0.1);
  box-shadow: 
    0 4rpx 12rpx rgba(92, 107, 192, 0.08),
    0 2rpx 6rpx rgba(0, 0, 0, 0.04);
  
  &:active {
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.12) 100%);
    color: #5C6BC0;
    transform: scale(0.92);
    box-shadow: 
      0 6rpx 16rpx rgba(92, 107, 192, 0.12),
      0 3rpx 8rpx rgba(0, 0, 0, 0.06);
  }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 180rpx $spacing-lg;
  
  .empty-icon {
    font-size: 180rpx;
    margin-bottom: $spacing-xl;
    opacity: 0.5;
    color: rgba(92, 107, 192, 0.4);
    animation: searchEmptyFloat 3s ease-in-out infinite;
  }
  
  .empty-text {
    font-size: 40rpx;
    color: #5C6BC0;
    margin-bottom: $spacing-md;
    font-weight: 700;
    letter-spacing: 2rpx;
  }
  
  .empty-hint {
    font-size: 32rpx;
    color: rgba(156, 39, 176, 0.6);
    font-weight: 500;
    letter-spacing: 1rpx;
  }
}

/* 空状态浮动动画 */
@keyframes searchEmptyFloat {
  0%, 100% { transform: translateY(0rpx) rotate(0deg); }
  50% { transform: translateY(-20rpx) rotate(3deg); }
}

/* 搜索结果列表 */
.results-list {
  padding: 0 $spacing-md;
}

/* 搜索结果卡片 - 阴影和渐变背景 */
.note-card {
  position: relative;
  padding: $spacing-lg;
  border-radius: 24rpx;
  margin-bottom: $spacing-md;
  background: linear-gradient(135deg, #FFFFFF 0%, rgba(255, 255, 255, 0.95) 100%);
  box-shadow: 
    0 12rpx 32rpx rgba(0, 0, 0, 0.12),
    0 6rpx 16rpx rgba(92, 107, 192, 0.08),
    0 3rpx 8rpx rgba(0, 0, 0, 0.06),
    inset 0 2rpx 0 rgba(255, 255, 255, 1);
  border: 2rpx solid rgba(92, 107, 192, 0.08);
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:active {
    transform: scale(0.96) translateY(4rpx);
    box-shadow: 
      0 6rpx 20rpx rgba(0, 0, 0, 0.15),
      0 3rpx 12rpx rgba(92, 107, 192, 0.1),
      0 2rpx 6rpx rgba(0, 0, 0, 0.08);
  }
  
  /* 不同颜色卡片的渐变背景 */
  &[style*="#FFF9C4"] {
    background: linear-gradient(135deg, #FFF9C4 0%, rgba(251, 192, 45, 0.15) 100%);
    border-left: 8rpx solid #FBC02D;
  }
  
  &[style*="#FFCDD2"] {
    background: linear-gradient(135deg, #FFCDD2 0%, rgba(239, 83, 80, 0.15) 100%);
    border-left: 8rpx solid #EF5350;
  }
  
  &[style*="#C8E6C9"] {
    background: linear-gradient(135deg, #C8E6C9 0%, rgba(102, 187, 106, 0.15) 100%);
    border-left: 8rpx solid #66BB6A;
  }
  
  &[style*="#BBDEFB"] {
    background: linear-gradient(135deg, #BBDEFB 0%, rgba(66, 165, 245, 0.15) 100%);
    border-left: 8rpx solid #42A5F5;
  }
  
  &[style*="#E1BEE7"] {
    background: linear-gradient(135deg, #E1BEE7 0%, rgba(171, 71, 188, 0.15) 100%);
    border-left: 8rpx solid #AB47BC;
  }
  
  &[style*="#FFE0B2"] {
    background: linear-gradient(135deg, #FFE0B2 0%, rgba(255, 152, 0, 0.15) 100%);
    border-left: 8rpx solid #FF9800;
  }
  
  &[style*="#D7CCC8"] {
    background: linear-gradient(135deg, #D7CCC8 0%, rgba(141, 110, 99, 0.15) 100%);
    border-left: 8rpx solid #8D6E63;
  }
}

/* 锁定徽章 */
.lock-badge {
  position: absolute;
  right: $spacing-lg;
  top: $spacing-lg;
  font-size: 32rpx;
  background: linear-gradient(135deg, rgba(92, 107, 192, 0.15) 0%, rgba(156, 39, 176, 0.15) 100%);
  border-radius: 50%;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.1);
}

/* 笔记分类标签 */
.note-category {
  display: inline-block;
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  color: #FFFFFF;
  margin-bottom: $spacing-sm;
  font-weight: 600;
  letter-spacing: 2rpx;
  box-shadow: 0 4rpx 12rpx rgba(92, 107, 192, 0.25);
}

/* 笔记标题 */
.note-title {
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: $spacing-sm;
  line-height: 1.5;
  letter-spacing: 1rpx;
  
  /* 搜索高亮效果 */
  mark {
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.35) 0%, rgba(156, 39, 176, 0.25) 100%);
    color: #5C6BC0;
    border-radius: 6rpx;
    padding: 4rpx 8rpx;
    font-weight: 700;
    box-shadow: 0 2rpx 8rpx rgba(92, 107, 192, 0.15);
  }
}

/* 笔记内容预览 */
.note-preview {
  font-size: 32rpx;
  color: $text-secondary;
  line-height: 1.7;
  opacity: 0.9;
  
  /* 搜索高亮效果 */
  mark {
    background: linear-gradient(135deg, rgba(92, 107, 192, 0.35) 0%, rgba(156, 39, 176, 0.25) 100%);
    color: #5C6BC0;
    border-radius: 6rpx;
    padding: 4rpx 8rpx;
    font-weight: 700;
    box-shadow: 0 2rpx 8rpx rgba(92, 107, 192, 0.15);
  }
}

/* 多行文本截断 */
.ellipsis-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* 笔记底部区域 */
.note-footer {
  margin-top: $spacing-md;
}

/* 笔记时间 */
.note-time {
  font-size: 28rpx;
  color: rgba(92, 107, 192, 0.5);
  font-weight: 500;
  letter-spacing: 1rpx;
}

/* 列表底部间距 */
.list-footer {
  height: 100rpx;
}
</style>