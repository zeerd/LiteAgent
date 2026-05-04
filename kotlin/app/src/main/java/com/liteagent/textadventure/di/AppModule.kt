package com.liteagent.textadventure.di

import android.content.Context
import androidx.room.Room
import com.liteagent.textadventure.data.db.*
import com.liteagent.textadventure.data.local.AppSettingsRepository
import com.liteagent.textadventure.data.repository.*
import com.liteagent.textadventure.service.LiteRtLmService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

/**
 * 数据库依赖注入模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * 提供 Room 数据库实例。
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "text_adventure_database"
        )
        // 版本不匹配时清空旧表并重新创建
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    /**
     * 提供对话数据的 DAO。
     */
    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    /**
     * 提供故事历史的 DAO。
     */
    @Provides
    @Singleton
    fun provideStoryHistoryDao(database: AppDatabase): StoryHistoryDao {
        return database.storyHistoryDao()
    }
}

/**
 * 服务依赖注入模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    /**
     * 提供 LiteRT-LM 服务单例。
     */
    @Provides
    @Singleton
    fun provideLiteRtLmService(@ApplicationContext context: Context): LiteRtLmService {
        return LiteRtLmService(context)
    }
}

/**
 * 存储库依赖注入模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    /**
     * 提供应用设置存储库。
     */
    @Provides
    @Singleton
    fun provideAppSettingsRepository(@ApplicationContext context: Context): AppSettingsRepository {
        return AppSettingsRepository(context)
    }
}
