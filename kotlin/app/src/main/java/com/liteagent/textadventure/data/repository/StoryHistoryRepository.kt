package com.liteagent.textadventure.data.repository

import com.liteagent.textadventure.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryHistoryRepository @Inject constructor(
    private val storyHistoryDao: StoryHistoryDao
) {
    fun getAllStories(): Flow<List<StoryHistoryEntity>> =
        storyHistoryDao.getAllStories()

    suspend fun getStoryById(storyId: String): StoryHistoryEntity? {
        return storyHistoryDao.getStoryById(storyId)
    }

    suspend fun addStory(entry: StoryHistoryEntity): String {
        storyHistoryDao.insertStory(entry)
        return entry.id
    }

    suspend fun updateStory(entry: StoryHistoryEntity) {
        storyHistoryDao.updateStory(entry)
    }

    suspend fun deleteStory(storyId: String) {
        storyHistoryDao.deleteStoryById(storyId)
    }

    suspend fun deleteAllStories() {
        storyHistoryDao.deleteAllStories()
    }

    fun getStoryCount(): Flow<Int> = storyHistoryDao.getStoryCount()
}
