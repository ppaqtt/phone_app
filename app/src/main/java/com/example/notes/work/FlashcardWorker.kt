package com.example.notes.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notes.MainActivity
import com.example.notes.NotesApplication
import com.example.notes.R
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每天上午 8 点检查一次到期闪卡, 有则发通知提醒复习。
 * 复用 WorkManager 的周期任务, 不额外占用 AlarmManager。
 */
class FlashcardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NotesApplication ?: return Result.failure()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val due = runCatching { app.flashcardRepository.countDue() }.getOrElse { 0 }
        if (due <= 0) return Result.success()

        createChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("今天有 $due 张卡片待复习")
            .setContentText("花几分钟回顾一下, 把笔记变成长期记忆")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onFailure { e ->
            Timber.tag("FlashcardWorker").w(e, "notify failed")
        }

        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "闪卡复习",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每天提醒待复习的笔记闪卡"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "flashcard_review_channel"
        private const val NOTIFICATION_ID = 9001
        private const val UNIQUE_NAME = "flashcard_daily_review"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FlashcardWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNextMorning(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun delayUntilNextMorning(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
