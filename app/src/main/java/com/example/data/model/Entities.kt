package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val username: String, // lowercase, without @
    val title: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val lastFetchedPostId: Long = 0,
    val isEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "posts",
    indices = [Index(value = ["channelUsername", "telegramPostId"], unique = true)]
)
data class PostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelUsername: String,
    val telegramPostId: Long,
    val content: String,
    val publishedTime: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSummarized: Boolean = false
)

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalPostsProcessed: Int,
    val channelsInvolved: String, // comma separated or display names
    val title: String,
    val fullDigestJson: String // JSON representation of CategorizedDigest list
)

data class CategorizedDigest(
    val category: String,
    val emoji: String,
    val items: List<DigestItem>
)

data class DigestItem(
    val headline: String,
    val summary: String,
    val sourceChannel: String,
    val importance: String = "عادی" // "مهم", "عادی", "ویژه"
)

data class ParsedTelegramPost(
    val id: Long,
    val text: String,
    val time: String = "",
    val channelUsername: String
)
