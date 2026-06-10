package com.example.notes.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        loadHistory()
    }

    /**
     * 添加搜索记录到历史
     */
    fun addSearch(query: String) {
        if (query.isBlank()) return
        val current = _history.value.toMutableList()
        // 移除重复项
        current.remove(query)
        // 添加到开头
        current.add(0, query)
        // 限制数量
        val trimmed = current.take(MAX_HISTORY_SIZE)
        _history.value = trimmed
        saveHistory(trimmed)
    }

    /**
     * 清除所有搜索历史
     */
    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /**
     * 删除单条搜索记录
     */
    fun removeSearch(query: String) {
        val current = _history.value.toMutableList()
        current.remove(query)
        _history.value = current
        saveHistory(current)
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
            _history.value = emptyList()
        }
    }

    private fun saveHistory(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
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
