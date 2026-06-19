package com.example.notes.repository

import com.example.notes.data.TodoDao
import com.example.notes.data.TodoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 待办任务数据仓库
 */
class TodoRepository(
    private val todoDao: TodoDao
) {
    /** 观察所有待办 */
    fun observeAll(): Flow<List<TodoEntity>> = todoDao.observeAll()

    /** 观察未完成的待办 */
    fun observeActive(): Flow<List<TodoEntity>> = todoDao.observeActive()

    /** 观察已完成的待办 */
    fun observeCompleted(): Flow<List<TodoEntity>> = todoDao.observeCompleted()

    /** 观察单个待办 */
    fun observeById(id: Long): Flow<TodoEntity?> = todoDao.observeById(id)

    /** 获取单个待办（非响应式） */
    suspend fun getById(id: Long): TodoEntity? = todoDao.getById(id)

    /** 创建或更新待办 */
    suspend fun save(todo: TodoEntity): Long {
        return if (todo.id == 0L) {
            todoDao.insert(todo)
        } else {
            todoDao.update(todo.copy(updatedAt = System.currentTimeMillis()))
            todo.id
        }
    }

    /** 删除待办 */
    suspend fun delete(todo: TodoEntity) = todoDao.delete(todo)

    /** 按 ID 删除待办 */
    suspend fun deleteById(id: Long) = todoDao.deleteById(id)

    /** 切换完成状态 */
    suspend fun toggleCompleted(id: Long) {
        val todo = todoDao.getById(id) ?: return
        val newCompleted = !todo.isCompleted
        val completedAt = if (newCompleted) System.currentTimeMillis() else null
        todoDao.setCompleted(id, newCompleted, completedAt)
    }

    /** 设置完成状态 */
    suspend fun setCompleted(id: Long, completed: Boolean) {
        val completedAt = if (completed) System.currentTimeMillis() else null
        todoDao.setCompleted(id, completed, completedAt)
    }

    /** 设置提醒时间 */
    suspend fun setReminderTime(id: Long, reminderTime: Long?) =
        todoDao.setReminderTime(id, reminderTime)

    /** 功能3: 设置提醒重复模式 */
    suspend fun setReminderRepeat(id: Long, reminderRepeat: String) =
        todoDao.setReminderRepeat(id, reminderRepeat)

    /** 功能3: 同时更新提醒时间和重复模式 */
    suspend fun setReminderTimeAndRepeat(id: Long, reminderTime: Long?, reminderRepeat: String) =
        todoDao.setReminderTimeAndRepeat(id, reminderTime, reminderRepeat)

    /** 设置铃声 */
    suspend fun setRingtone(id: Long, ringtoneUri: String?) =
        todoDao.setRingtone(id, ringtoneUri)

    /** 设置优先级 */
    suspend fun setPriority(id: Long, priority: Int) =
        todoDao.setPriority(id, priority)

    /** 获取所有有待提醒的待办 */
    suspend fun getAllWithReminders(): List<TodoEntity> = todoDao.getAllWithReminders()

    /** 观察未完成待办数 */
    fun observeActiveCount(): Flow<Int> = todoDao.observeActiveCount()

    /** 观察已过期待办数 */
    fun observeOverdueCount(): Flow<Int> = todoDao.observeOverdueCount()

    /** 清除所有已完成待办 */
    suspend fun clearCompleted() = todoDao.clearCompleted()

    /** 删除所有待办 */
    suspend fun clearAll() = todoDao.clearAll()
}
