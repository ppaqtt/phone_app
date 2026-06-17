package com.example.notes.util

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
import com.example.notes.data.TodoEntity
import com.example.notes.work.TodoWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 待办任务提醒管理器
 */
object TodoReminderManager {

    enum class ScheduleResult { SCHEDULED, TIME_PASSED, FAILED, PERMISSION_DENIED }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return true
        return alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.tag("TodoReminderManager").w(it, "openExactAlarmSettings failed") }
    }

    suspend fun scheduleReminder(context: Context, todo: TodoEntity): ScheduleResult {
        val reminderTime = todo.reminderTime ?: return ScheduleResult.FAILED

        val currentTime = System.currentTimeMillis()
        val delay = reminderTime - currentTime

        if (delay <= 0) {
            return ScheduleResult.TIME_PASSED
        }

        if (!canScheduleExact(context)) {
            Timber.tag("TodoReminderManager").w("精确闹钟权限被撤, todoId=${todo.id}")
            return ScheduleResult.PERMISSION_DENIED
        }

        return try {
            val inputData = workDataOf(
                TodoWorker.KEY_TODO_ID to todo.id,
                TodoWorker.KEY_TODO_TITLE to todo.title,
                TodoWorker.KEY_TODO_CONTENT to todo.content,
                TodoWorker.KEY_RINGTONE_URI to (todo.ringtoneUri ?: "")
            )

            val workName = "todo_reminder_${todo.id}"
            val workRequest = OneTimeWorkRequestBuilder<TodoWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("todo_${todo.id}")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)
            ScheduleResult.SCHEDULED
        } catch (e: Exception) {
            Timber.tag("TodoReminderManager").e(e, "scheduleReminder failed")
            ScheduleResult.FAILED
        }
    }

    fun cancelReminder(context: Context, todoId: Long) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("todo_reminder_$todoId")
    }

    suspend fun showScheduleResult(context: Context, result: ScheduleResult) {
        val message = when (result) {
            ScheduleResult.SCHEDULED -> "提醒已设置"
            ScheduleResult.TIME_PASSED -> "提醒时间已过"
            ScheduleResult.FAILED -> "提醒设置失败"
            ScheduleResult.PERMISSION_DENIED -> "需要开启精确闹钟权限"
        }
        withContext(Dispatchers.Main) {
            context.toastShort(message)
        }
        if (result == ScheduleResult.PERMISSION_DENIED) {
            openExactAlarmSettings(context)
        }
    }
}
