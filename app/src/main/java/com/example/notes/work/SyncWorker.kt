package com.example.notes.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notes.BuildConfig
import com.example.notes.NotesApplication
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 后台自动同步 (每 6 小时)。仅在用户开启"自动同步"且配置完整时执行。
 * 失败按指数退避重试, 最多重试 3 次, 避免离线时无限打服务器。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NotesApplication ?: return Result.failure()
        val config = app.syncConfigStore.config.value
        if (!config.enabled || !config.autoSync || !config.isConfigured) {
            return Result.success()
        }

        return runCatching {
            app.webDavSyncManager.sync(BuildConfig.VERSION_NAME)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                Timber.tag("SyncWorker").w(e, "auto sync failed")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        )
    }

    companion object {
        private const val UNIQUE_NAME = "webdav_auto_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
