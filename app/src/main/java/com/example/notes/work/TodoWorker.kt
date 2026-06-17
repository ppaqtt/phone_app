package com.example.notes.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.notes.NotesApplication
import com.example.notes.R
import timber.log.Timber

/**
 * 待办任务提醒工作器
 * 支持自定义铃声
 */
class TodoWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "todo_reminder_channel"
        const val KEY_TODO_ID = "todo_id"
        const val KEY_TODO_TITLE = "todo_title"
        const val KEY_TODO_CONTENT = "todo_content"
        const val KEY_RINGTONE_URI = "ringtone_uri"
    }

    override suspend fun doWork(): Result {
        val todoId = inputData.getLong(KEY_TODO_ID, 0L)
        val title = inputData.getString(KEY_TODO_TITLE) ?: "待办提醒"
        val content = inputData.getString(KEY_TODO_CONTENT) ?: ""
        val ringtoneUriString = inputData.getString(KEY_RINGTONE_URI)

        // P6: Android 13+ 需检查 POST_NOTIFICATIONS 运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success()
            }
        }

        createNotificationChannel()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // 设置自定义铃声
        if (!ringtoneUriString.isNullOrBlank()) {
            try {
                val ringtoneUri = Uri.parse(ringtoneUriString)
                builder.setSound(ringtoneUri)
            } catch (e: Exception) {
                Timber.tag("TodoWorker").w(e, "Failed to parse ringtone URI, using default")
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
        } else {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        val notification = builder.build()

        runCatching {
            with(NotificationManagerCompat.from(context)) {
                notify(todoId.toInt(), notification)
            }
        }.onFailure { e ->
            Timber.tag("TodoWorker").w(e, "notify failed")
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "待办提醒"
            val descriptionText = "待办任务提醒通知"
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
