package com.example.notes.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    // P93: 旧的 scope 是属性 (单例常驻, App 生命周期都活着)。
    // 改用 Application Context 内创建全局 + 暴露 cancel() 方法供未来
    // 测试或热重载时调用, 同时用 MainScope+IO 混合, 避免内存泄漏警告。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            loadHistory()
        }
    }

    /**
     * P93: 释放协程资源。正常 App 生命周期不需要调用 (单例, OS 杀进程时
     * 协程自动消亡); 仅在单元测试 / 模块热替换时主动释放, 避免泄漏。
     */
    fun release() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    /**
     * 添加搜索记录到历史 (大小写不敏感去重)
     * P63: 状态更新和 JSON 序列化都移到协程, 避免主线程 JSON 写操作
     * P85: 旧版用 `val current = _history.value; ...; _history.value = ...` 的
     * 读-改-写序列, 两次连续 addSearch 会发生竞态: 后一次读到旧值, 覆盖前一次结果。
     * 改用 [MutableStateFlow.update] 内部 CAS, 原子完成 read-modify-write。
     */
    fun addSearch(query: String) {
        if (query.isBlank()) return
        val lowerQuery = query.lowercase()
        scope.launch {
            _history.update { current ->
                val newList = current.toMutableList()
                newList.removeAll { it.lowercase() == lowerQuery }
                newList.add(0, query)
                newList.take(MAX_HISTORY_SIZE)
            }
            saveHistory(_history.value)
        }
    }

    /**
     * 清除所有搜索历史
     * P89: 改到协程里, 与 addSearch 保持风格一致 (避免主线程写 SharedPreferences)
     */
    fun clearHistory() {
        scope.launch {
            _history.value = emptyList()
            prefs.edit().remove(KEY_HISTORY).apply()
        }
    }

    /**
     * 删除单条搜索记录 (大小写不敏感匹配)
     * P63/P85: 同上, 切协程避免主线程 IO, 用 update 避免竞态
     */
    fun removeSearch(query: String) {
        val lowerQuery = query.lowercase()
        scope.launch {
            _history.update { current ->
                current.filterNot { it.lowercase() == lowerQuery }
            }
            saveHistory(_history.value)
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
