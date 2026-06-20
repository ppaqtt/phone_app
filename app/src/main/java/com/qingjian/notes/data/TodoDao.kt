package com.qingjian.notes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /** 观察所有待办（未完成的在前，按优先级和创建时间排序） */
    @Query("SELECT * FROM todos ORDER BY is_completed ASC, priority DESC, created_at DESC")
    fun observeAll(): Flow<List<TodoEntity>>

    /** 观察未完成的待办 */
    @Query("SELECT * FROM todos WHERE is_completed = 0 ORDER BY priority DESC, created_at DESC")
    fun observeActive(): Flow<List<TodoEntity>>

    /** 观察已完成的待办 */
    @Query("SELECT * FROM todos WHERE is_completed = 1 ORDER BY completed_at DESC")
    fun observeCompleted(): Flow<List<TodoEntity>>

    /** 按 ID 观察单个待办 */
    @Query("SELECT * FROM todos WHERE id = :id")
    fun observeById(id: Long): Flow<TodoEntity?>

    /** 按 ID 获取单个待办（非响应式） */
    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    /** 插入待办 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    /** 更新待办 */
    @Update
    suspend fun update(todo: TodoEntity)

    /** 删除待办 */
    @Delete
    suspend fun delete(todo: TodoEntity)

    /** 按 ID 删除待办 */
    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 切换完成状态 */
    @Query("UPDATE todos SET is_completed = :completed, completed_at = :completedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, updatedAt: Long = System.currentTimeMillis())

    /** 设置提醒时间 */
    @Query("UPDATE todos SET reminder_time = :reminderTime, updated_at = :updatedAt WHERE id = :id")
    suspend fun setReminderTime(id: Long, reminderTime: Long?, updatedAt: Long = System.currentTimeMillis())

    /** 功能3: 设置提醒重复模式 */
    @Query("UPDATE todos SET reminder_repeat = :reminderRepeat, updated_at = :updatedAt WHERE id = :id")
    suspend fun setReminderRepeat(id: Long, reminderRepeat: String, updatedAt: Long = System.currentTimeMillis())

    /** 功能3: 同时更新提醒时间和重复模式 */
    @Query("UPDATE todos SET reminder_time = :reminderTime, reminder_repeat = :reminderRepeat, updated_at = :updatedAt WHERE id = :id")
    suspend fun setReminderTimeAndRepeat(id: Long, reminderTime: Long?, reminderRepeat: String, updatedAt: Long = System.currentTimeMillis())

    /** 设置铃声 */
    @Query("UPDATE todos SET ringtone_uri = :ringtoneUri, updated_at = :updatedAt WHERE id = :id")
    suspend fun setRingtone(id: Long, ringtoneUri: String?, updatedAt: Long = System.currentTimeMillis())

    /** 设置优先级 */
    @Query("UPDATE todos SET priority = :priority, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int, updatedAt: Long = System.currentTimeMillis())

    /** 获取所有有待提醒的待办（用于调度） */
    @Query("SELECT * FROM todos WHERE reminder_time IS NOT NULL AND is_completed = 0")
    suspend fun getAllWithReminders(): List<TodoEntity>

    /** 统计未完成待办数 */
    @Query("SELECT COUNT(*) FROM todos WHERE is_completed = 0")
    fun observeActiveCount(): Flow<Int>

    /** 统计已过期且未完成的待办（提醒时间已过但未完成） */
    @Query("SELECT COUNT(*) FROM todos WHERE is_completed = 0 AND reminder_time IS NOT NULL AND reminder_time < :currentTime")
    fun observeOverdueCount(currentTime: Long = System.currentTimeMillis()): Flow<Int>

    /** 清除所有已完成待办 */
    @Query("DELETE FROM todos WHERE is_completed = 1")
    suspend fun clearCompleted()

    /** 删除所有待办 */
    @Query("DELETE FROM todos")
    suspend fun clearAll()
}
