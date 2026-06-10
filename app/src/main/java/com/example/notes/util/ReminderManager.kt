package com.example.notes.util

import android.content.Context
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.notes.data.NoteEntity
import com.example.notes.work.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object ReminderManager {

    /** 调度结果: 成功 / 时间已过 / 调度失败 */
    enum class ScheduleResult { SCHEDULED, TIME_PASSED, FAILED }

    /**
     * 调度一次提醒。
     * 行为:
     *  1. 先按 noteId 取消可能存在的旧 Worker (幂等, 防止重复提醒)
     *  2. 计算 delay, 若 <= 0 返回 TIME_PASSED
     *  3. 入队新 Worker, 失败返回 FAILED
     *
     * P94: 加重试策略 EXPONENTIAL, 最多 3 次, 初始 30s 退避。
     * 设备进入 Doze / 系统重启等情况下, WorkManager 会按策略重新拉起,
     * 避免偶发情况下提醒漏掉。
     *
     * @return [ScheduleResult] 用于调用方给用户反馈
     */
    suspend fun scheduleReminder(context: Context, note: NoteEntity, reminderTime: Long): ScheduleResult {
        // 1. 先取消同 note 的旧提醒 (幂等)
        cancelReminder(context, note.id)

        val currentTime = System.currentTimeMillis()
        val delay = reminderTime - currentTime

        if (delay <= 0) {
            return ScheduleResult.TIME_PASSED
        }

        return try {
            val inputData = workDataOf(
                ReminderWorker.KEY_NOTE_ID to note.id,
                ReminderWorker.KEY_NOTE_TITLE to note.title,
                ReminderWorker.KEY_NOTE_CONTENT to note.content
            )

            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("note_${note.id}")
                // P94: 指数退避, 最多 3 次, Doze / 资源紧张情况下能补提醒
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            ScheduleResult.SCHEDULED
        } catch (e: Exception) {
            android.util.Log.e("ReminderManager", "scheduleReminder failed", e)
            ScheduleResult.FAILED
        }
    }

    fun cancelReminder(context: Context, noteId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag("note_$noteId")
    }

    /** 给用户提示调度结果, 自动切换到主线程避免 Toast 在 IO 线程崩溃 */
    suspend fun showScheduleResult(context: Context, result: ScheduleResult) {
        val message = when (result) {
            ScheduleResult.SCHEDULED -> "已设置提醒"
            ScheduleResult.TIME_PASSED -> "提醒时间已过, 请重新选择"
            ScheduleResult.FAILED -> "提醒设置失败, 请稍后重试"
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
