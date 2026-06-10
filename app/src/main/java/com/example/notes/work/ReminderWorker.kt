package com.example.notes.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.notes.NotesApplication
import com.example.notes.R
import com.example.notes.util.ReminderRepeat
import timber.log.Timber

class ReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "notes_reminder_channel"
        const val KEY_NOTE_ID = "note_id"
        const val KEY_NOTE_TITLE = "note_title"
        const val KEY_NOTE_CONTENT = "note_content"
    }

    override suspend fun doWork(): Result {
        val noteId = inputData.getLong(KEY_NOTE_ID, 0L)
        val title = inputData.getString(KEY_NOTE_TITLE) ?: "笔记提醒"
        val content = inputData.getString(KEY_NOTE_CONTENT) ?: ""

        // P6: Android 13+ 需检查 POST_NOTIFICATIONS 运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // 无权限, 静默跳过 (Settings 里有引导)
                return Result.success()
            }
        }

        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // P6: 用 try-catch 包裹, 极端设备上 notify 可能抛 SecurityException
        runCatching {
            with(NotificationManagerCompat.from(context)) {
                notify(noteId.toInt(), notification)
            }
        }.onFailure { e ->
            Timber.tag("ReminderWorker").w(e, "notify failed")
        }

        // F15: 重复提醒 - 若 repeat != NONE, 自动排下一次
        if (noteId > 0L) {
            runCatching {
                val app = applicationContext as NotesApplication
                val note = app.repository.getNoteOnce(noteId)
                if (note != null) {
                    val repeat = ReminderRepeat.fromString(note.reminderRepeat)
                    if (repeat != ReminderRepeat.NONE && note.reminderTime != null) {
                        val nextTime = repeat.nextTriggerTime(note.reminderTime)
                        // 防止无限循环 — 若下次时间已过则放弃
                        if (nextTime > System.currentTimeMillis()) {
                            app.repository.updateReminder(noteId, nextTime, repeat.name)
                            com.example.notes.util.ReminderManager.scheduleReminder(
                                context = applicationContext,
                                note = note.copy(
                                    reminderTime = nextTime,
                                    reminderRepeat = repeat.name
                                ),
                                reminderTime = nextTime
                            )
                            Timber.tag("ReminderWorker")
                                .i("rescheduled next reminder for note=$noteId at $nextTime (${repeat.name})")
                        }
                    }
                }
            }.onFailure { e ->
                Timber.tag("ReminderWorker").e(e, "reschedule failed")
            }
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "笔记提醒"
            val descriptionText = "笔记提醒通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
