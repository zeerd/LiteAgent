package com.liteagent.textadventure.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 故事历史记录的数据库访问对象接口。
 */
@Dao
interface StoryHistoryDao {
    /**
     * 插入一个新故事历史，冲突时替换。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(entry: StoryHistoryEntity)

    /**
     * 更新一个故事历史的信息。
     */
    @Update
    suspend fun updateStory(entry: StoryHistoryEntity)

    /**
     * 删除一个故事历史条目。
     */
    @Delete
    suspend fun deleteStory(entry: StoryHistoryEntity)

    /**
     * 获取所有故事历史记录流，按最后活跃时间倒序排列。
     */
    @Query("SELECT * FROM story_history_table ORDER BY last_active DESC")
    fun getAllStories(): Flow<List<StoryHistoryEntity>>

    /**
     * 同步查询指定 ID 的故事历史。
     */
    @Query("SELECT * FROM story_history_table WHERE id = :storyId")
    suspend fun getStoryById(storyId: String): StoryHistoryEntity?

    /**
     * 查询指定 ID 的故事历史流。
     */
    @Query("SELECT * FROM story_history_table WHERE id = :storyId")
    fun getStoryByIdFlow(storyId: String): Flow<StoryHistoryEntity?>

    /**
     * 根据 ID 删除特定故事历史。
     */
    @Query("DELETE FROM story_history_table WHERE id = :storyId")
    suspend fun deleteStoryById(storyId: String)

    /**
     * 清空所有故事历史记录。
     */
    @Query("DELETE FROM story_history_table")
    suspend fun deleteAllStories()

    /**
     * 获取故事历史记录的总数流。
     */
    @Query("SELECT COUNT(*) FROM story_history_table")
    fun getStoryCount(): Flow<Int>
}
