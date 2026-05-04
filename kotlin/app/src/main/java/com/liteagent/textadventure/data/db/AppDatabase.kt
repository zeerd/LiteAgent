package com.liteagent.textadventure.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 应用程序的 Room 数据库定义。
 * 包含对话消息和故事历史两个表。
 */
@Database(
    entities = [ConversationEntity::class, StoryHistoryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 获取对话数据的 DAO。
     */
    abstract fun conversationDao(): ConversationDao

    /**
     * 获取故事历史数据的 DAO。
     */
    abstract fun storyHistoryDao(): StoryHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库单例实例。
         */
        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "text_adventure_database"
                )
                // 在版本不匹配时进行破坏性迁移
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { INSTANCE = it }
            }
    }
}
