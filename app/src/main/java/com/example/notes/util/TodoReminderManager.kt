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
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 待办任务提醒管理器
 *
 * 功能3: 支持重复提醒 (每天 / 每周 / 每月)。实现思路:
 * 1) 待办触发时, TodoWorker.doWork 调用 scheduleNextIfNeeded
 *    计算下一次触发时间并重新用 WorkManager 排一次延迟任务。
 * 2) 未完成的待办会不断循环排下一次, 直到被标记为完成或被删除。
 */
object TodoReminderManager {

    enum class ScheduleResult { SCHEDULED, TIME_PASSED, FAILED }

    /** 功能3: 重复模式常量 (与 TodoEntity.reminderRepeat 对应) */
    const val REPEAT_NONE = "NONE"
    const val REPEAT_DAILY = "DAILY"
    const val REPEAT_WEEKLY = "WEEKLY"
    const val REPEAT_MONTHLY = "MONTHLY"

    /**
     * 调度单次待办提醒。如果当前时间已经过了 reminderTime, 返回 TIME_PASSED。
     */
    suspend fun scheduleReminder(context: Context, todo: TodoEntity): ScheduleResult {
        val reminderTime = todo.reminderTime ?: return ScheduleResult.FAILED

        val currentTime = System.currentTimeMillis()
        val delay = reminderTime - currentTime

        if (delay <= 0) {
            // 功能3: 如果到了"重复"模式，并且 reminderTime 已过期，直接尝试计算下一次时间
            if (todo.reminderRepeat != REPEAT_NONE) {
                val next = computeNextReminder(todo.reminderTime, todo.reminderRepeat)
                if (next != null && next > currentTime) {
                    return scheduleReminderAt(context, todo.copy(reminderTime = next), next)
                }
            }
            return ScheduleResult.TIME_PASSED
        }

        return scheduleReminderAt(context, todo, reminderTime)
    }

    private suspend fun scheduleReminderAt(
        context: Context,
        todo: TodoEntity,
        fireAt: Long
    ): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            val inputData = workDataOf(
                TodoWorker.KEY_TODO_ID to todo.id,
                TodoWorker.KEY_TODO_TITLE to todo.title,
                TodoWorker.KEY_TODO_CONTENT to todo.content,
                TodoWorker.KEY_RINGTONE_URI to (todo.ringtoneUri ?: "")
            )

            val workName = "todo_reminder_${todo.id}"
            val delayMillis = fireAt - System.currentTimeMillis()
            val workRequest = OneTimeWorkRequestBuilder<TodoWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("todo_${todo.id}")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)
            Timber.tag("TodoReminderManager").d(
                "Scheduled todo reminder id=${todo.id}, fireAt=$fireAt (delay=${delayMillis}ms)"
            )
            ScheduleResult.SCHEDULED
        } catch (e: Exception) {
            Timber.tag("TodoReminderManager").e(e, "scheduleReminder failed")
            ScheduleResult.FAILED
        }
    }

    /**
     * 功能3: 根据 repeat 模式计算下一次提醒时间。
     * 策略: 基于"上一次提醒时间"往前推算 N 个周期。若推算结果仍在当前时间之前, 继续累加,
     * 直到下一次提醒时间 > 当前时间。
     */
    fun computeNextReminder(lastReminder: Long?, repeat: String): Long? {
        if (lastReminder == null || repeat == REPEAT_NONE) return null
        val base = Calendar.getInstance().apply { timeInMillis = lastReminder }
        val now = Calendar.getInstance()
        var attempts = 0
        while (attempts < 1000) {
            when (repeat) {
                REPEAT_DAILY -> base.add(Calendar.DAY_OF_YEAR, 1)
                REPEAT_WEEKLY -> base.add(Calendar.WEEK_OF_YEAR, 1)
                REPEAT_MONTHLY -> base.add(Calendar.MONTH, 1)
                else -> return null
            }
            if (base.timeInMillis > now.timeInMillis) return base.timeInMillis
            attempts++
        }
        return null
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
