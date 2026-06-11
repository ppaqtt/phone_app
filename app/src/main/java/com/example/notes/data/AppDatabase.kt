package com.example.notes.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库初始化。
 * 启用外键约束 (SQLite 默认关闭), 防止删除分类时存在孤儿 category_id。
 */
@Database(
    entities = [NoteEntity::class, CategoryEntity::class, NoteImageEntity::class],
    // v9 → v10: 给 notes 加 tags / reminder_time / priority / created_at 索引,
    //           给 note_images 加 position 索引 (手动 MIGRATION_9_10)
    // v8 → v9: 给 notes 加 is_pinned / updated_at / deleted_at 单列索引 (手动 MIGRATION_8_9)
    // F12: v7 → v8 给 categories 加 parent_id 字段 (嵌套分类)
    // F15: v6 → v7 给 notes 加 reminder_repeat 字段
    // F2: v5 → v6 给 notes 加 deleted_at 字段
    // P98: v4 → v5 给 notes.category_id 加外键
    version = 10,
    // P106-FIX: AutoMigration 和手动 Migration 不能同时覆盖同一路径。
    // v8→v9 和 v9→v10 都有手动 Migration, 故从 autoMigrations 中移除,
    // 避免 Room 运行时冲突。保留 v4→v5 到 v7→v8 的 AutoMigration,
    // 这些版本只有字段增删, Room 可以自动处理。
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8)
    ],
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun noteImageDao(): NoteImageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        /**
         * v8 → v9: 给 notes 表增 3 个单列索引。
         * 用 IF NOT EXISTS 保证幂等, 万一 Room 已先于我们建好索引也不会失败。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_is_pinned` ON `notes` (`is_pinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updated_at` ON `notes` (`updated_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_deleted_at` ON `notes` (`deleted_at`)")
            }
        }

        /**
         * v9 → v10: 给 notes 表增 4 个单列索引, 给 note_images 增 position 索引。
         * 加速:
         * - WHERE tags LIKE '%...%' (标签搜索)
         * - WHERE reminder_time IS NOT NULL (提醒计数)
         * - ORDER BY priority DESC (优先级排序)
         * - ORDER BY created_at DESC/ASC (创建时间排序)
         * - ORDER BY position ASC (图片顺序查询)
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_tags` ON `notes` (`tags`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_reminder_time` ON `notes` (`reminder_time`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_priority` ON `notes` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_created_at` ON `notes` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_images_position` ON `note_images` (`position`)")
            }
        }

        /**
         * P104-FIX: 降级保护 —— 用户安装旧版 APK 时数据库版本号会"降低",
         * Room 默认抛 IllegalStateException 导致崩溃。
         * 这里把降级视为"无操作": 保留所有数据, 仅让 SQLite 继续工作。
         * 旧版代码不认识新版字段/索引, 但 SELECT * 只会返回认识的列,
         * 不会破坏数据。当用户再次升级回新版时, 字段和索引都在。
         */
        private val MIGRATION_10_9 = object : Migration(10, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 降级时删除新版索引, 让旧版 Room 不报错
                db.execSQL("DROP INDEX IF EXISTS `index_notes_tags`")
                db.execSQL("DROP INDEX IF EXISTS `index_notes_reminder_time`")
                db.execSQL("DROP INDEX IF EXISTS `index_notes_priority`")
                db.execSQL("DROP INDEX IF EXISTS `index_notes_created_at`")
                db.execSQL("DROP INDEX IF EXISTS `index_note_images_position`")
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "notes.db"
            )
                // v8 → v9, v9 → v10 手动迁移: 给 notes/note_images 加查询索引
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_9)
                // 启用 SQLite 外键约束, 保护 category_id 引用完整性
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                })
                .build()
    }
}
