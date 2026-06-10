package com.example.notes.data

import android.content.Context
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
    version = 4,
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
                // 正常升级路径绝不删数据. 旧版 v1 / v2 / v3 → v4 的字段差异
                // 都是可选字段 (priority/color/reminder_time 等都有默认值),
                // Room 会通过 schema 校验自动处理,不需要再走 destructive.
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
