package com.example.notes.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * 搜索历史管理器
 * 使用 SharedPreferences 持久化存储搜索历史
 */
class SearchHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history

    // P8: loadHistory 改为协程 + 切到 IO 线程, 避免主线程 JSON 解析
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    init {
        scope.launch {
            loadHistory()
        }
    }

    /**
     * 添加搜索记录到历史 (大小写不敏感去重)
     * P63: 状态更新和 JSON 序列化都移到协程, 避免主线程 JSON 写操作
     */
    fun addSearch(query: String) {
        if (query.isBlank()) return
        scope.launch {
            val current = _history.value.toMutableList()
            val lowerQuery = query.lowercase()
            current.removeAll { it.lowercase() == lowerQuery }
            current.add(0, query)
            val trimmed = current.take(MAX_HISTORY_SIZE)
            _history.value = trimmed
            saveHistory(trimmed)
        }
    }

    /**
     * 清除所有搜索历史
     */
    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /**
     * 删除单条搜索记录 (大小写不敏感匹配)
     * P63: 同上, 切协程避免主线程 IO
     */
    fun removeSearch(query: String) {
        scope.launch {
            val lowerQuery = query.lowercase()
            val current = _history.value.toMutableList()
            current.removeAll { it.lowercase() == lowerQuery }
            _history.value = current
            saveHistory(current)
        }
    }

    private fun loadHistory() {
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        try {
            val array = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            _history.value = list
        } catch (e: Exception) {
            Log.w(TAG, "搜索历史 JSON 解析失败, 已重置. error=${e.message}")
            _history.value = emptyList()
        }
    }

    private fun saveHistory(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val TAG = "SearchHistoryManager"
        private const val PREFS_NAME = "search_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_SIZE = 20

        @Volatile
        private var instance: SearchHistoryManager? = null

        fun getInstance(context: Context): SearchHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: SearchHistoryManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
