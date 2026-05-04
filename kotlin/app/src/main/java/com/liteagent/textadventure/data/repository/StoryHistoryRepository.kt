package com.liteagent.textadventure.data.repository

import com.liteagent.textadventure.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 故事历史记录存储库，负责管理过去冒险故事的元数据。
 */
@Singleton
class StoryHistoryRepository @Inject constructor(
    private val storyHistoryDao: StoryHistoryDao
) {
    /**
     * 获取所有故事历史记录的流，按时间倒序排列。
     */
    fun getAllStories(): Flow<List<StoryHistoryEntity>> =
        storyHistoryDao.getAllStories()

    /**
     * 根据故事 ID 获取特定的故事历史实体。
     */
    suspend fun getStoryById(storyId: String): StoryHistoryEntity? {
        return storyHistoryDao.getStoryById(storyId)
    }

    /**
     * 添加一个新的故事历史记录。
     */
    suspend fun addStory(entry: StoryHistoryEntity): String {
        storyHistoryDao.insertStory(entry)
        return entry.id
    }

    /**
     * 更新现有的故事历史记录。
     */
    suspend fun updateStory(entry: StoryHistoryEntity) {
        storyHistoryDao.updateStory(entry)
    }

    /**
     * 根据 ID 删除一个故事历史记录。
     */
    suspend fun deleteStory(storyId: String) {
        storyHistoryDao.deleteStoryById(storyId)
    }

    /**
     * 删除所有故事历史记录。
     */
    suspend fun deleteAllStories() {
        storyHistoryDao.deleteAllStories()
    }

    /**
     * 获取故事总数的流。
     */
    fun getStoryCount(): Flow<Int> = storyHistoryDao.getStoryCount()
}
