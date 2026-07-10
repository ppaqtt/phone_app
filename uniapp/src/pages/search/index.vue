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
.search-header {
  padding: $spacing-md;
  padding-top: calc(env(safe-area-inset-top) + #{$spacing-md});
  background: $card-bg;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $bg-color;
  border-radius: 100rpx;
}

.back-btn {
  font-size: $font-size-xxl;
  color: $text-secondary;
  padding: 0 $spacing-xs;
  line-height: 1;
}

.search-icon {
  font-size: $font-size-lg;
}

.search-input {
  flex: 1;
  font-size: $font-size-base;
  color: $text-primary;
  background: transparent;
}

.clear-btn {
  font-size: $font-size-xl;
  color: $text-hint;
  padding: $spacing-xs;
}

.search-results {
  height: calc(100vh - 180rpx);
}

.search-history {
  padding: $spacing-md;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.section-title {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.clear-all {
  font-size: $font-size-sm;
  color: $primary-color;
}

.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.history-tag {
  padding: $spacing-xs $spacing-md;
  background: $bg-color;
  border-radius: 100rpx;
  font-size: $font-size-sm;
  color: $text-secondary;
  
  &:active {
    background: rgba($primary-color, 0.1);
    color: $primary-color;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx $spacing-lg;
  
  .empty-icon {
    font-size: 120rpx;
    margin-bottom: $spacing-lg;
  }
  
  .empty-text {
    font-size: $font-size-lg;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
  }
  
  .empty-hint {
    font-size: $font-size-sm;
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
  box-shadow: $shadow-sm;
}

.lock-badge {
  position: absolute;
  right: $spacing-md;
  top: $spacing-md;
  font-size: $font-size-base;
}

.note-category {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: $radius-sm;
  font-size: $font-size-xs;
  color: #FFFFFF;
  margin-bottom: $spacing-sm;
}

.note-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
  
  mark {
    background: rgba($primary-color, 0.2);
    color: $primary-color;
    border-radius: 4rpx;
    padding: 0 4rpx;
  }
}

.note-preview {
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: 1.6;
  
  mark {
    background: rgba($primary-color, 0.2);
    color: $primary-color;
    border-radius: 4rpx;
    padding: 0 4rpx;
  }
}

.note-footer {
  margin-top: $spacing-md;
}

.note-time {
  font-size: $font-size-xs;
  color: $text-hint;
}

.list-footer {
  height: 80rpx;
}
</style>