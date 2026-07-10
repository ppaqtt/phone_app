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
.container {
  background: linear-gradient(180deg, #f8f9fa 0%, #ffffff 100%);
}

.search-header {
  padding: $spacing-lg $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-lg});
  background: #FFFFFF;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 100rpx;
  border: 2rpx solid rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  
  &:focus-within {
    border-color: $primary-color;
    background: rgba($primary-color, 0.04);
    box-shadow: 0 4rpx 12rpx rgba($primary-color, 0.12);
  }
}

.back-btn {
  font-size: 48rpx;
  color: $text-primary;
  padding: 0 $spacing-xs;
  line-height: 1;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  
  &:active {
    transform: scale(0.9);
  }
}

.search-icon {
  font-size: 32rpx;
  opacity: 0.5;
}

.search-input {
  flex: 1;
  font-size: $font-size-lg;
  color: $text-primary;
  background: transparent;
  font-weight: 500;
}

.clear-btn {
  font-size: 32rpx;
  color: $text-hint;
  padding: $spacing-xs;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 50%;
  transition: all 0.2s ease;
  
  &:active {
    transform: scale(0.9);
    background: rgba(0, 0, 0, 0.12);
  }
}

.search-results {
  height: calc(100vh - 220rpx);
  padding: $spacing-md;
}

.search-history {
  padding: $spacing-lg $spacing-md;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
}

.section-title {
  font-size: $font-size-lg;
  color: $text-primary;
  font-weight: 600;
}

.clear-all {
  font-size: $font-size-base;
  color: $primary-color;
  font-weight: 500;
  padding: $spacing-sm $spacing-md;
  background: rgba($primary-color, 0.08);
  border-radius: $radius-sm;
  transition: all 0.2s ease;
  
  &:active {
    transform: scale(0.95);
    background: rgba($primary-color, 0.12);
  }
}

.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.history-tag {
  padding: $spacing-sm $spacing-md;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 100rpx;
  font-size: $font-size-base;
  color: $text-secondary;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 500;
  
  &:active {
    background: rgba($primary-color, 0.12);
    color: $primary-color;
    transform: scale(0.95);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 160rpx $spacing-lg;
  
  .empty-icon {
    font-size: 160rpx;
    margin-bottom: $spacing-lg;
    opacity: 0.4;
  }
  
  .empty-text {
    font-size: $font-size-xl;
    color: $text-primary;
    margin-bottom: $spacing-sm;
    font-weight: 600;
  }
  
  .empty-hint {
    font-size: $font-size-base;
    color: $text-hint;
  }
}

.results-list {
  padding: 0 $spacing-md;
}

.note-card {
  position: relative;
  padding: $spacing-lg;
  border-radius: $radius-lg;
  margin-bottom: $spacing-md;
  background: #FFFFFF;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.06);
  border: 1rpx solid rgba(0, 0, 0, 0.04);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:active {
    transform: scale(0.98);
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.08);
  }
}

.lock-badge {
  position: absolute;
  right: $spacing-lg;
  top: $spacing-lg;
  font-size: 28rpx;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 50%;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.note-category {
  display: inline-block;
  padding: 6rpx 18rpx;
  border-radius: $radius-sm;
  font-size: $font-size-xs;
  color: #FFFFFF;
  margin-bottom: $spacing-sm;
  font-weight: 500;
  letter-spacing: 1rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
}

.note-title {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-sm;
  line-height: 1.4;
  
  mark {
    background: linear-gradient(135deg, rgba($primary-color, 0.25) 0%, rgba($primary-color, 0.15) 100%);
    color: $primary-color;
    border-radius: 4rpx;
    padding: 2rpx 6rpx;
    font-weight: 600;
  }
}

.note-preview {
  font-size: $font-size-base;
  color: $text-secondary;
  line-height: 1.6;
  opacity: 0.85;
  
  mark {
    background: linear-gradient(135deg, rgba($primary-color, 0.25) 0%, rgba($primary-color, 0.15) 100%);
    color: $primary-color;
    border-radius: 4rpx;
    padding: 2rpx 6rpx;
    font-weight: 600;
  }
}

.ellipsis-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.note-footer {
  margin-top: $spacing-md;
}

.note-time {
  font-size: $font-size-xs;
  color: $text-hint;
  font-weight: 400;
}

.list-footer {
  height: 80rpx;
}
</style>