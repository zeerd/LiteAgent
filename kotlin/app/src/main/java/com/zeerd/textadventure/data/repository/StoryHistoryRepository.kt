package com.zeerd.textadventure.data.repository

import android.util.Log
import com.zeerd.textadventure.data.db.*
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
    companion object {
        private const val TAG = "TextAdventure-StoryHistoryRepository"
    }
    /**
     * 获取所有故事历史记录的流，按时间倒序排列。
     */
    fun getAllStories(): Flow<List<StoryHistoryEntity>> {
        Log.v(TAG, ">>> getAllStories() IN")
        val result = storyHistoryDao.getAllStories()
        Log.v(TAG, "<<< getAllStories() OUT - Flow returned")
        return result
    }

    /**
     * 根据故事 ID 获取特定的故事历史实体。
     */
    suspend fun getStoryById(storyId: String): StoryHistoryEntity? {
        Log.v(TAG, ">>> getStoryById() IN - storyId=$storyId")
        val result = storyHistoryDao.getStoryById(storyId)
        Log.v(TAG, "<<< getStoryById() OUT - ${if (result != null) "found" else "null"}")
        return result
    }

    /**
     * 添加一个新的故事历史记录。
     */
    suspend fun addStory(entry: StoryHistoryEntity): String {
        Log.v(TAG, ">>> addStory() IN - storyId=${entry.id}")
        storyHistoryDao.insertStory(entry)
        Log.v(TAG, "<<< addStory() OUT - storyId=${entry.id}")
        return entry.id
    }

    /**
     * 更新现有的故事历史记录。
     */
    suspend fun updateStory(entry: StoryHistoryEntity) {
        Log.v(TAG, ">>> updateStory() IN - storyId=${entry.id}")
        storyHistoryDao.updateStory(entry)
        Log.v(TAG, "<<< updateStory() OUT")
    }

    /**
     * 根据 ID 删除一个故事历史记录。
     */
    suspend fun deleteStory(storyId: String) {
        Log.v(TAG, ">>> deleteStory() IN - storyId=$storyId")
        storyHistoryDao.deleteStoryById(storyId)
        Log.v(TAG, "<<< deleteStory() OUT")
    }

    /**
     * 删除所有故事历史记录。
     */
    suspend fun deleteAllStories() {
        Log.v(TAG, ">>> deleteAllStories() IN")
        storyHistoryDao.deleteAllStories()
        Log.v(TAG, "<<< deleteAllStories() OUT")
    }

    /**
     * 获取故事总数的流。
     */
    fun getStoryCount(): Flow<Int> {
        Log.v(TAG, ">>> getStoryCount() IN")
        val result = storyHistoryDao.getStoryCount()
        Log.v(TAG, "<<< getStoryCount() OUT - Flow returned")
        return result
    }
}
