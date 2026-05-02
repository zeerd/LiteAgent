package com.liteagent.textadventure.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(entry: StoryHistoryEntity)
    
    @Update
    suspend fun updateStory(entry: StoryHistoryEntity)
    
    @Delete
    suspend fun deleteStory(entry: StoryHistoryEntity)
    
    @Query("SELECT * FROM story_history_table ORDER BY last_active DESC")
    fun getAllStories(): Flow<List<StoryHistoryEntity>>
    
    @Query("SELECT * FROM story_history_table WHERE id = :storyId")
    suspend fun getStoryById(storyId: String): StoryHistoryEntity?
    
    @Query("SELECT * FROM story_history_table WHERE id = :storyId")
    fun getStoryByIdFlow(storyId: String): Flow<StoryHistoryEntity?>
    
    @Query("DELETE FROM story_history_table WHERE id = :storyId")
    suspend fun deleteStoryById(storyId: String)
    
    @Query("DELETE FROM story_history_table")
    suspend fun deleteAllStories()
    
    @Query("SELECT COUNT(*) FROM story_history_table")
    fun getStoryCount(): Flow<Int>
}
