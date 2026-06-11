package com.example.notes.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.notes.data.NoteEntity
import com.example.notes.work.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import timber.log.Timber

object ReminderManager {

    /** 调度结果: 成功 / 时间已过 / 调度失败 */
    enum class ScheduleResult { SCHEDULED, TIME_PASSED, FAILED }

    /**
     * 调度一次提醒。
     * 行为:
     *  1. 计算 delay, 若 <= 0 返回 TIME_PASSED
     *  2. 入队唯一 Worker (同名 note id, REPLACE 策略), 失败返回 FAILED
     *
     * P94: 加重试策略 EXPONENTIAL, 最多 3 次, 初始 30s 退避。
     * 设备进入 Doze / 系统重启等情况下, WorkManager 会按策略重新拉起,
     * 避免偶发情况下提醒漏掉。
     *
     * @return [ScheduleResult] 用于调用方给用户反馈
     */
    suspend fun scheduleReminder(context: Context, note: NoteEntity, reminderTime: Long): ScheduleResult {
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

            // P-FIX-001: 用 enqueueUniqueWork 替代普通 enqueue, 同一 note id 只会
            // 保留最新的一个 Worker, REPLACE 策略确保新设置会覆盖旧提醒, 防止重复通知。
            val workName = "note_reminder_${note.id}"
            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("note_${note.id}")
                // P94: 指数退避, 最多 3 次, Doze / 资源紧张情况下能补提醒
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)
            ScheduleResult.SCHEDULED
        } catch (e: Exception) {
            Timber.tag("ReminderManager").e(e, "scheduleReminder failed")
            ScheduleResult.FAILED
        }
    }

    fun cancelReminder(context: Context, noteId: Long) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("note_reminder_$noteId")
    }

    /** 给用户提示调度结果, 自动切换到主线程避免 Toast 在 IO 线程崩溃 */
    suspend fun showScheduleResult(context: Context, result: ScheduleResult) {
        val message = when (result) {
            ScheduleResult.SCHEDULED -> "已设置提醒"
            ScheduleResult.TIME_PASSED -> "提醒时间已过, 请重新选择"
            ScheduleResult.FAILED -> "提醒设置失败, 请稍后重试"
        }
        withContext(Dispatchers.Main) {
            context.toastShort(message)
        }
    }
}
