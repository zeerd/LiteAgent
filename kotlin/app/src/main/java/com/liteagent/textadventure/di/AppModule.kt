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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "text_adventure_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideStoryHistoryDao(database: AppDatabase): StoryHistoryDao {
        return database.storyHistoryDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideLiteRtLmService(@ApplicationContext context: Context): LiteRtLmService {
        return LiteRtLmService(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAppSettingsRepository(@ApplicationContext context: Context): AppSettingsRepository {
        return AppSettingsRepository(context)
    }
}
