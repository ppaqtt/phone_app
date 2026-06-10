package com.example.notes.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notes.NotesApplication
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * F2: 回收站定期清理任务。
 *
 * 设计目标:
 * 1) 30 天前的已删笔记自动真删, 关联 note_images 通过外键 CASCADE 自动清理;
 * 2) 用 WorkManager OneTimeWork, 启动时和应用进入前台各调度一次,
 *    避免长期挂 PeriodicWork 的耗电;
 * 3) Worker 内部用 Application 拿 Repository, 不依赖 Hilt (目前没接入 Hilt)。
 */
class TrashJanitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NotesApplication ?: return Result.failure()
        return runCatching {
            val purged = app.repository.purgeOldTrash(daysOld = 30)
            Timber.tag("TrashJanitor").i("purged $purged old trashed notes")
            Result.success()
        }.getOrElse {
            Timber.tag("TrashJanitor").e(it, "purge failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "trash_janitor"

        /**
         * 调度一次延迟执行, 24h 后跑; 已存在同名 work 则忽略 (KEEP 策略)。
         * App.onCreate 或 MainActivity.onResume 时调一次即可, 重复调无副作用。
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrashJanitorWorker>()
                .setInitialDelay(24, TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** 立即执行一次 (调试 / 用户主动清空时用) */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrashJanitorWorker>()
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
