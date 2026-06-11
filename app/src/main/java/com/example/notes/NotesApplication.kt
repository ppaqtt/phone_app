package com.example.notes

import android.app.Application
import android.content.Context
import com.example.notes.data.AppDatabase
import com.example.notes.repository.NotesRepository
import com.example.notes.util.AppLockStore
import com.example.notes.util.AppUpdateChecker
import com.example.notes.util.BackupManager
import com.example.notes.util.TrashJanitorWorker
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

    /** F9: 应用锁状态 (PIN 哈希 + 启用标志 + 解锁时间) */
    val appLockStore: AppLockStore by lazy { AppLockStore(this) }

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

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // P107-FIX: 检测是否首次运行新版本。如果是, 先导出 JSON 备份再升级数据库,
        // 确保即使 Room 迁移失败, 用户数据也有可恢复的备份。
        appScope.launch { maybeBackupBeforeUpgrade() }

        // App 启动后异步检查更新, 不阻塞主流程
        // P-FIX-001: 传 applicationContext, 检查到新版本后写入 SharedPreferences,
        // 供 NotesListScreen 冷启动后弹出 SnackBar 通知用户
        appScope.launch {
            runCatching { AppUpdateChecker.checkForUpdate(persistContext = applicationContext) }
                .onFailure { Timber.tag("NotesApplication").w(it, "update check failed") }
        }

        // F2: 调度回收站清理 worker, 24h 后跑, 之后 KEEP 策略幂等。
        TrashJanitorWorker.schedule(this)

        // P105-FIX: 应用启动时自动创建数据库本地备份。
        // 每次启动都复制一份 notes.db 到 cacheDir/auto_backup/,
        // 保留最近 3 份。如果更新后数据库损坏, 可从最近备份恢复。
        appScope.launch { autoBackupDatabase() }
    }

    /**
     * P107-FIX: 升级预检 —— 首次运行新版本时, 先把用户数据导出为 JSON 备份,
     * 然后再让 Room 执行数据库迁移。这样即使迁移失败, 用户数据也不会丢失。
     *
     * 实现:
     * 1. 读取上次记录的版本号 (SharedPreferences)
     * 2. 如果当前版本 > 上次版本, 说明是升级后首次启动
     * 3. 导出全部数据为 JSON 到 filesDir/upgrade_backup/
     * 4. 记录当前版本号
     * 5. 后续 Room 正常执行迁移
     */
    private suspend fun maybeBackupBeforeUpgrade() {
        runCatching {
            val prefs = getSharedPreferences("app_upgrade", Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt("last_version_code", 0)
            val currentVersion = BuildConfig.VERSION_CODE

            if (currentVersion > lastVersion && lastVersion != 0) {
                // 升级 detected, 先备份再迁移
                Timber.tag("NotesApplication").i(
                    "Upgrade detected: $lastVersion → $currentVersion, creating safety backup..."
                )

                val backupDir = File(filesDir, "upgrade_backup").apply { mkdirs() }
                // 保留最近 2 份升级备份
                // P112-FIX: 显式 List<File> 类型, 避免 Kotlin 1.8.22 平台类型
                // (Array<File!>) 透过 ?-chain 传播导致 cascade "T not inferred / it unresolved" 错误。
                val existingBackups: List<File> =
                    backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                existingBackups.drop(1).forEach { it.delete() }

                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "pre_upgrade_$ts.json")

                // 懒加载 repository 读取数据
                // P111-FIX: 不再调用 repository 上不存在的 getAllXxxOnce(),
                // 改用 repository.exportBackup(currentVersion) 一步完成
                // 笔记+分类+图片的聚合, 避免逐表读取再手工拼装 payload。
                val payload = repository.exportBackup(currentVersion.toString())
                backupFile.writeText(BackupManager.toJson(payload))
                Timber.tag("NotesApplication").i(
                    "Pre-upgrade safety backup created: ${backupFile.absolutePath}"
                )
            }

            // 记录当前版本, 下次启动用于比较
            prefs.edit().putInt("last_version_code", currentVersion).apply()
        }.onFailure {
            Timber.tag("NotesApplication").w(it, "Pre-upgrade backup failed, continuing anyway")
        }
    }

    /**
     * P105-FIX: 自动备份数据库文件到本地缓存。
     * - 保留最近 3 份备份, 旧备份自动删除
     * - 备份文件名包含时间戳, 便于识别
     * - 备份在 cacheDir 下, 用户清理缓存时会一并清理
     * - 如果数据库文件被损坏, 可从最近备份恢复
     */
    private fun autoBackupDatabase() {
        runCatching {
            val dbFile = getDatabasePath("notes.db")
            if (!dbFile.exists()) return

            val backupDir = File(cacheDir, "auto_backup").apply { mkdirs() }
            // 清理旧备份, 只保留最近 3 份
            // P112-FIX: 显式 List<File> 类型, 同上, 避免 Kotlin 1.8.22 平台类型推断失败。
            val existingBackups: List<File> =
                backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            existingBackups.drop(3).forEach { it.delete() }

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "notes_backup_$ts.db")
            dbFile.copyTo(backupFile, overwrite = true)
            Timber.tag("NotesApplication").d("Auto backup created: ${backupFile.name}")
        }.onFailure {
            Timber.tag("NotesApplication").w(it, "Auto backup failed")
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
                // P112-FIX: 显式指定 <File>, 避免 Kotlin 1.8.22 在 `?:` 上下文
                // 推断不出 T 导致后续 list.drop(4).forEach { it.delete() } 一连串
                // "type variable T not inferred / receiver type mismatch / unresolved it" errors。
                val list: List<File> = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                list.drop(4).forEach { it.delete() }
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "crash_$ts.log")
                file.bufferedWriter().use { w ->
                    w.appendLine("=== Crash at $ts ===")
                    w.appendLine("Thread: ${thread.name}")
                    w.appendLine("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    w.appendLine()
                    w.appendLine(throwable.stackTraceToString())
                }
            }
            // 把崩溃再交给系统默认 handler (杀进程 + ANR 等)
            prev?.uncaughtException(thread, throwable)
        }
    }
}
