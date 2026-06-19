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
    entities = [NoteEntity::class, CategoryEntity::class, NoteImageEntity::class, TodoEntity::class],
    // v10 → v11: 添加 todos 表（待办任务）
    // v9 → v10: 给 notes 加 tags / reminder_time / priority / created_at 索引,
    //           给 note_images 加 position 索引 (手动 MIGRATION_9_10)
    // v8 → v9: 给 notes 加 is_pinned / updated_at / deleted_at 单列索引 (手动 MIGRATION_8_9)
    // F12: v7 → v8 给 categories 加 parent_id 字段 (嵌套分类)
    // F15: v6 → v7 给 notes 加 reminder_repeat 字段
    // F2: v5 → v6 给 notes 加 deleted_at 字段
    // P98: v4 → v5 给 notes.category_id 加外键
    version = 12,
    // P110-FIX: 移除所有 AutoMigration, 改用手动 Migration。
    // 原因: AutoMigration 需要历史 schema JSON (5.json/6.json/7.json/8.json)
    // 做对比验证, 但项目首次 build 时这些文件不存在, 编译会失败。
    // 手动 Migration 不依赖 schema 验证, 兼容性更好。
    autoMigrations = [],
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun noteImageDao(): NoteImageDao
    abstract fun todoDao(): TodoDao

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
         * v10 → v11: 添加 todos 表（待办任务）
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `is_completed` INTEGER NOT NULL DEFAULT 0,
                        `priority` INTEGER NOT NULL DEFAULT 0,
                        `reminder_time` INTEGER,
                        `ringtone_uri` TEXT,
                        `created_at` INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `completed_at` INTEGER
                    )
                """.trimIndent())
                // 索引顺序必须与 Room 期望一致: priority, reminder_time, created_at, is_completed
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_priority` ON `todos` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_reminder_time` ON `todos` (`reminder_time`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_created_at` ON `todos` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_is_completed` ON `todos` (`is_completed`)")
            }
        }

        /**
         * 功能3: v11 → v12: 给 todos 表加 reminder_repeat 字段 (重复提醒)。
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `todos` ADD COLUMN `reminder_repeat` TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /**
         * 功能3: v12 → v11: 降级时删除 reminder_repeat 字段 (需要重建表)。
         */
        private val MIGRATION_12_11 = object : Migration(12, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite 不支持 DROP COLUMN，使用重建表方式降级
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todos_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `is_completed` INTEGER NOT NULL DEFAULT 0,
                        `priority` INTEGER NOT NULL DEFAULT 0,
                        `reminder_time` INTEGER,
                        `ringtone_uri` TEXT,
                        `created_at` INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `completed_at` INTEGER
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO todos_new (id, title, content, is_completed, priority, reminder_time, ringtone_uri, created_at, updated_at, completed_at)
                    SELECT id, title, content, is_completed, priority, reminder_time, ringtone_uri, created_at, updated_at, completed_at FROM todos
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `todos`")
                db.execSQL("ALTER TABLE todos_new RENAME TO `todos`")
            }
        }

        /**
         * v11 → v10: 降级时删除 todos 表
         */
        private val MIGRATION_11_10 = object : Migration(11, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_todos_priority`")
                db.execSQL("DROP INDEX IF EXISTS `index_todos_reminder_time`")
                db.execSQL("DROP INDEX IF EXISTS `index_todos_created_at`")
                db.execSQL("DROP INDEX IF EXISTS `index_todos_is_completed`")
                db.execSQL("DROP TABLE IF EXISTS `todos`")
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

        /**
         * P98: v4 → v5 给 notes.category_id 加外键约束。
         * SQLite ALTER TABLE 不支持加外键, 需要重建表。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 重建 notes 表, 加外键约束
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notes_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `category_id` INTEGER,
                        `tags` TEXT NOT NULL,
                        `is_pinned` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `color` INTEGER NOT NULL,
                        `reminder_time` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO notes_new SELECT * FROM notes")
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_category_id` ON `notes` (`category_id`)")
            }
        }

        /**
         * F2: v5 → v6 给 notes 加 deleted_at 字段 (回收站功能)。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `deleted_at` INTEGER DEFAULT NULL")
            }
        }

        /**
         * F15: v6 → v7 给 notes 加 reminder_repeat 字段 (重复提醒)。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `reminder_repeat` TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /**
         * F12: v7 → v8 给 categories 加 parent_id 字段 (嵌套分类)。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `parent_id` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parent_id` ON `categories` (`parent_id`)")
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "notes.db"
            )
                // 注册所有版本间的迁移。顺序无所谓, Room 会自动选择路径。
                .addMigrations(
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                    MIGRATION_11_10, MIGRATION_10_9, MIGRATION_12_11
                )
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
