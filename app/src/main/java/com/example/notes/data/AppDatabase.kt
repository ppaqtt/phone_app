package com.example.notes.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库初始化。
 * 启用外键约束 (SQLite 默认关闭), 防止删除分类时存在孤儿 category_id。
 */
@Database(
    entities = [NoteEntity::class, CategoryEntity::class, NoteImageEntity::class],
    // F15: v6 → v7 给 notes 加 reminder_repeat 字段
    // F2: v5 → v6 给 notes 加 deleted_at 字段
    // P98: v4 → v5 给 notes.category_id 加外键
    version = 7,
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7)
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

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "notes.db"
            )
                // 仅在版本号被人工调低时清空(防止调试时 downgrade 崩溃),
                // 正常升级路径绝不删数据. v4 → v5 加外键通过 AutoMigration 完成。
                .fallbackToDestructiveMigrationOnDowngrade()
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
