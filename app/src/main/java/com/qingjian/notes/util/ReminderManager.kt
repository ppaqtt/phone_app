package com.qingjian.notes.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.qingjian.notes.data.NoteEntity
import com.qingjian.notes.work.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import timber.log.Timber

object ReminderManager {

    /** 调度结果: 成功 / 时间已过 / 调度失败 / 精确闹钟权限被撤 */
    enum class ScheduleResult { SCHEDULED, TIME_PASSED, FAILED, PERMISSION_DENIED }

    /**
     * Android 12 (API 31) 起, 即便使用 WorkManager, 部分 OEM 厂商在 Doze 模式
     * 下会因 SCHEDULE_EXACT_ALARM 权限被撤而推迟 WorkManager 拉起。检查
     * [AlarmManager.canScheduleExactAlarms] 在被撤时引导用户去设置。
     *
     * 注意: WorkManager 本身不强制需要此权限, 但缺失会导致提醒漂移到
     * 下一次维护窗口 (可能晚 15 分钟以上), 严重影响用户体验。
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return true
        return alarmManager.canScheduleExactAlarms()
    }

    /** 引导用户到精确闹钟设置页 (Android 12+ 有效) */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.tag("ReminderManager").w(it, "openExactAlarmSettings failed") }
    }

    /**
     * 调度一次提醒。
     * 行为:
     *  1. 计算 delay, 若 <= 0 返回 TIME_PASSED
     *  2. Android 12+ 检查精确闹钟权限, 被撤返回 PERMISSION_DENIED
     *  3. 入队唯一 Worker (同名 note id, REPLACE 策略), 失败返回 FAILED
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

        // Android 12+ 检查精确闹钟权限
        if (!canScheduleExact(context)) {
            Timber.tag("ReminderManager").w("精确闹钟权限被撤, noteId=${note.id}")
            return ScheduleResult.PERMISSION_DENIED
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
            ScheduleResult.PERMISSION_DENIED -> "需要开启精确闹钟权限, 正在打开设置..."
        }
        withContext(Dispatchers.Main) {
            context.toastShort(message)
        }
        // 权限被撤时一并跳到精确闹钟设置页
        if (result == ScheduleResult.PERMISSION_DENIED) {
            openExactAlarmSettings(context)
        }
    }
}
