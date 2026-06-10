package com.example.notes

import android.app.Application
import android.util.Log
import com.example.notes.data.AppDatabase
import com.example.notes.repository.NotesRepository
import com.example.notes.util.AppUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application class that owns the dependency graph. Kept intentionally simple —
 * a production app would use Hilt or Koin. Here we expose singletons through
 * lazy properties so the rest of the app can grab them without DI plumbing.
 */
class NotesApplication : Application() {

    // P9: 数据库走 by lazy, 首次访问(主屏 onCreate)才构建,
    // 避免 Application.onCreate 同步构建拖慢冷启动
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: NotesRepository by lazy {
        // P54: 注入 database, 让 Repository 可以用 withTransaction 做跨 DAO 原子操作
        NotesRepository(
            database = database,
            noteDao = database.noteDao(),
            categoryDao = database.categoryDao(),
            noteImageDao = database.noteImageDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // P103: 装全局 UncaughtExceptionHandler, 崩溃时:
        // 1. 写 stack trace 到 cacheDir/crash/, 下次启动可读 / 上传
        // 2. 调链上原本的 handler (通常是 system 杀进程) 保证不破坏系统行为
        // 旧版无任何全局处理, 崩了就崩了, 用户反馈时无 log 可看。
        installCrashHandler()

        // App 启动后异步检查更新, 不阻塞主流程
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            runCatching { AppUpdateChecker.checkForUpdate(this@NotesApplication) }
                .onFailure { Log.w("NotesApplication", "update check failed: ${it.message}") }
        }
    }

    /**
     * P103: 把未捕获异常 stack 写到 cacheDir/crash/ 下, 一个崩溃一个文件,
     * 保留最近 5 个。记录后继续调用系统默认 handler (杀进程 + ANR 流程),
     * 不破坏 Android 自身的崩溃体验。
     */
    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(cacheDir, "crash").apply { mkdirs() }
                val list = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyArray()
                list.drop(4).forEach { it.delete() }
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "crash_$ts.log")
                file.bufferedWriter().use { w ->
                    w.appendLine("=== Crash at $ts ===")
                    w.appendLine("Thread: ${thread.name}")
                    w.appendLine("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    w.appendLine()
                    w.appendLine(Log.getStackTraceString(throwable))
                }
            }
            // 把崩溃再交给系统默认 handler (杀进程 + ANR 等)
            prev?.uncaughtException(thread, throwable)
        }
    }
}
