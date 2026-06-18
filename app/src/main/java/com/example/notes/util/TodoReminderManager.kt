package com.example.notes.util

import android.content.Context
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
 *
 * P130-FIX: 本管理器使用 WorkManager 调度一次性延迟任务。
 * WorkManager 不需要精确闹钟权限 (SCHEDULE_EXACT_ALARM) —— 它内部使用
 * JobScheduler/AlarmManager 的 setWindow() 或 setExactAndAllowWhileIdle()
 * (取决于约束条件), 由系统负责最佳执行时机。
 *
 * 之前错误地检查了 canScheduleExactAlarms(), 导致 vivo 等国产系统上
 * 弹出"需要开启精确闹钟权限"的误导性提示。已移除该检查。
 */
object TodoReminderManager {

    enum class ScheduleResult { SCHEDULED, TIME_PASSED, FAILED }

    suspend fun scheduleReminder(context: Context, todo: TodoEntity): ScheduleResult {
        val reminderTime = todo.reminderTime ?: return ScheduleResult.FAILED

        val currentTime = System.currentTimeMillis()
        val delay = reminderTime - currentTime

        if (delay <= 0) {
            return ScheduleResult.TIME_PASSED
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
            Timber.tag("TodoReminderManager").d("Scheduled todo reminder id=${todo.id}, delay=${delay}ms")
            ScheduleResult.SCHEDULED
        } catch (e: Exception) {
            Timber.tag("TodoReminderManager").e(e, "scheduleReminder failed")
            ScheduleResult.FAILED
        }
    }

    fun cancelReminder(context: Context, todoId: Long) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("todo_reminder_$todoId")
        Timber.tag("TodoReminderManager").d("Cancelled todo reminder id=$todoId")
    }

    suspend fun showScheduleResult(context: Context, result: ScheduleResult) {
        val message = when (result) {
            ScheduleResult.SCHEDULED -> "提醒已设置"
            ScheduleResult.TIME_PASSED -> "提醒时间已过"
            ScheduleResult.FAILED -> "提醒设置失败"
        }
        withContext(Dispatchers.Main) {
            context.toastShort(message)
        }
    }
}
