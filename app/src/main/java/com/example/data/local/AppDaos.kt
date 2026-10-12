package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChannelEntity
import com.example.data.model.PostEntity
import com.example.data.model.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY addedAt DESC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isEnabled = 1")
    suspend fun getEnabledChannels(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE username = :username LIMIT 1")
    suspend fun getChannel(username: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Update
    suspend fun updateChannel(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE username = :username")
    suspend fun deleteChannel(username: String)

    @Query("UPDATE channels SET lastFetchedPostId = :lastId WHERE username = :username")
    suspend fun updateLastFetchedId(username: String, lastId: Long)
}

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPosts(posts: List<PostEntity>): List<Long>

    @Query("SELECT * FROM posts WHERE isSummarized = 0 ORDER BY telegramPostId ASC")
    suspend fun getUnsummarizedPosts(): List<PostEntity>

    @Query("SELECT COUNT(*) FROM posts WHERE isSummarized = 0")
    fun getUnsummarizedPostsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM posts WHERE isSummarized = 0")
    suspend fun getUnsummarizedCountDirect(): Int

    @Query("UPDATE posts SET isSummarized = 1 WHERE id IN (:postIds)")
    suspend fun markPostsAsSummarized(postIds: List<Long>)

    @Query("SELECT * FROM posts WHERE channelUsername = :channelUsername ORDER BY telegramPostId DESC LIMIT 20")
    fun getRecentPostsForChannel(channelUsername: String): Flow<List<PostEntity>>

    @Query("DELETE FROM posts WHERE isSummarized = 1 AND timestamp < :olderThanTimestamp")
    suspend fun cleanOldPosts(olderThanTimestamp: Long)
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    fun getAllSummaries(): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSummary(): Flow<SummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryEntity): Long

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun deleteSummary(id: Long)
}
