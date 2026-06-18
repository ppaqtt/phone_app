package com.example.notes.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.notes.MainActivity
import com.example.notes.R
import timber.log.Timber

/**
 * 待办任务提醒工作器
 *
 * P125-FIX: 修复自定义铃声不生效问题。
 * Android 8.0 (API 26) 之后, 通知声音必须在 NotificationChannel 上设置,
 * 而不能在 NotificationCompat.Builder 上设置。本实现为每个唯一铃声
 * 创建独立 channel (channelId = "todo_reminder_channel_<ringtoneHash>"),
 * 确保不同待办的不同铃声互不干扰。
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

        fun channelIdFor(ringtoneUri: String?): String {
            if (ringtoneUri.isNullOrBlank()) return CHANNEL_ID
            return CHANNEL_ID + "_" + Integer.toHexString(ringtoneUri.hashCode())
        }
    }

    override suspend fun doWork(): Result {
        val todoId = inputData.getLong(KEY_TODO_ID, 0L)
        val title = inputData.getString(KEY_TODO_TITLE) ?: "待办提醒"
        val content = inputData.getString(KEY_TODO_CONTENT) ?: ""
        val ringtoneUriString = inputData.getString(KEY_RINGTONE_URI)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // P130-FIX: 不要静默返回, 写日志并尝试跳设置页引导用户
                Timber.tag("TodoWorker").w(
                    "POST_NOTIFICATIONS denied, todoId=$todoId. " +
                        "User will not see/hear the reminder."
                )
                NotificationPermission.openAppSettings(context)
                return Result.failure()
            }
        }

        val actualChannelId = createNotificationChannel(ringtoneUriString)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            todoId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, actualChannelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val soundUri = if (!ringtoneUriString.isNullOrBlank()) {
                runCatching { Uri.parse(ringtoneUriString) }
                    .getOrElse { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            builder.setSound(soundUri)
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

    private fun createNotificationChannel(ringtoneUriString: String?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return CHANNEL_ID
        }

        val channelId = channelIdFor(ringtoneUriString)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        val existing = notificationManager.getNotificationChannel(channelId)
        if (existing != null) {
            Timber.tag("TodoWorker").d("Reusing existing channel: $channelId")
            return channelId
        }

        val name = "待办提醒"
        val descriptionText = "待办任务提醒通知"
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = descriptionText
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC

            if (!ringtoneUriString.isNullOrBlank()) {
                val soundUri = runCatching { Uri.parse(ringtoneUriString) }.getOrNull()
                if (soundUri != null) {
                    setSound(soundUri, audioAttributes)
                    Timber.tag("TodoWorker").d("Set custom ringtone: $soundUri")
                } else {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        audioAttributes
                    )
                }
            } else {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    audioAttributes
                )
            }
        }

        notificationManager.createNotificationChannel(channel)
        Timber.tag("TodoWorker").d("Created new channel: $channelId")
        return channelId
    }
}
