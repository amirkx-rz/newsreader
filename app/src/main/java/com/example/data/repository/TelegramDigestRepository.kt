package com.example.data.repository

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.CategorizedDigest
import com.example.data.model.ChannelEntity
import com.example.data.model.DigestItem
import com.example.data.model.PostEntity
import com.example.data.model.SummaryEntity
import com.example.data.remote.GeminiSummarizer
import com.example.data.remote.TelegramWebScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TelegramDigestRepository(
    private val database: AppDatabase,
    private val scraper: TelegramWebScraper = TelegramWebScraper(),
    private val summarizer: GeminiSummarizer = GeminiSummarizer()
) {
    companion object {
        private const val TAG = "TelegramDigestRepo"

        val DEFAULT_PRESET_CHANNELS = listOf(
            ChannelEntity(
                username = "digiato",
                title = "دیجیاتو | دنیای فناوری",
                description = "اخبار روز فناوری، هوش مصنوعی، موبایل و گجت‌ها",
                avatarUrl = null
            ),
            ChannelEntity(
                username = "akharinkhabar",
                title = "آخرین خبر",
                description = "سریع‌ترین و معتبرترین پایگاه خبری ایران و جهان",
                avatarUrl = null
            ),
            ChannelEntity(
                username = "varzesh3",
                title = "ورزش سه",
                description = "اخبار، نتایج زنده و حواشی ورزش ایران و جهان",
                avatarUrl = null
            )
        )
    }

    val allChannels: Flow<List<ChannelEntity>> = database.channelDao().getAllChannels()
    val unsummarizedCount: Flow<Int> = database.postDao().getUnsummarizedPostsCount()
    val allSummaries: Flow<List<SummaryEntity>> = database.summaryDao().getAllSummaries()
    val latestSummary: Flow<SummaryEntity?> = database.summaryDao().getLatestSummary()

    /**
     * Seed default channels if empty
     */
    suspend fun seedDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        val existing = database.channelDao().getAllChannels().first()
        if (existing.isEmpty()) {
            for (channel in DEFAULT_PRESET_CHANNELS) {
                database.channelDao().insertChannel(channel)
            }
            // Seed a few initial demo news posts for instant out-of-the-box exploration
            seedDemoPosts()
        }
    }

    suspend fun addChannel(input: String): Result<ChannelEntity> = withContext(Dispatchers.IO) {
        val username = TelegramWebScraper.normalizeUsername(input)
        if (username.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("نام کاربری نمی‌تواند خالی باشد"))
        }

        // Try fetching online channel info
        val onlineResult = scraper.fetchChannelInfo(username)
        val channelToSave = if (onlineResult.isSuccess) {
            onlineResult.getOrThrow()
        } else {
            // Fallback: save channel entity with username
            ChannelEntity(
                username = username,
                title = "@$username",
                description = "کانال تلگرام @$username"
            )
        }

        database.channelDao().insertChannel(channelToSave)

        // Immediately try fetching its latest posts
        fetchNewPostsForChannel(username)

        Result.success(channelToSave)
    }

    suspend fun toggleChannel(channel: ChannelEntity) = withContext(Dispatchers.IO) {
        database.channelDao().updateChannel(channel.copy(isEnabled = !channel.isEnabled))
    }

    suspend fun removeChannel(username: String) = withContext(Dispatchers.IO) {
        database.channelDao().deleteChannel(username)
    }

    suspend fun refreshAllChannels(): Int = withContext(Dispatchers.IO) {
        val enabledChannels = database.channelDao().getEnabledChannels()
        var totalNewPosts = 0
        for (ch in enabledChannels) {
            val count = fetchNewPostsForChannel(ch.username)
            totalNewPosts += count
        }
        totalNewPosts
    }

    private suspend fun fetchNewPostsForChannel(username: String): Int {
        val channel = database.channelDao().getChannel(username) ?: return 0
        val postsResult = scraper.fetchRecentPosts(username, afterPostId = channel.lastFetchedPostId)
        if (postsResult.isSuccess) {
            val parsedPosts = postsResult.getOrThrow()
            if (parsedPosts.isNotEmpty()) {
                val entities = parsedPosts.map { p ->
                    PostEntity(
                        channelUsername = p.channelUsername,
                        telegramPostId = p.id,
                        content = p.text,
                        publishedTime = p.time,
                        isSummarized = false
                    )
                }
                database.postDao().insertPosts(entities)
                val maxId = parsedPosts.maxOf { it.id }
                if (maxId > channel.lastFetchedPostId) {
                    database.channelDao().updateLastFetchedId(username, maxId)
                }
                return parsedPosts.size
            }
        }
        return 0
    }

    /**
     * Generates a new summary of all unread/unsummarized posts
     */
    suspend fun generateDigest(): Result<SummaryEntity> = withContext(Dispatchers.IO) {
        // First refresh online to get the very latest posts
        refreshAllChannels()

        val unsummarized = database.postDao().getUnsummarizedPosts()
        if (unsummarized.isEmpty()) {
            return@withContext Result.failure(Exception("هیچ پیام یا خبر جدیدی برای خلاصه کردن وجود ندارد."))
        }

        val aiResult = summarizer.summarizeAndCategorize(unsummarized)
        val categorizedList = aiResult.getOrDefault(emptyList())

        if (categorizedList.isEmpty()) {
            return@withContext Result.failure(Exception("خطا در تولید خلاصه اخبار. لطفاً مجدداً امتحان کنید."))
        }

        // Serialize categorized list to JSON string
        val jsonArray = JSONArray()
        for (cat in categorizedList) {
            val catObj = JSONObject().apply {
                put("category", cat.category)
                put("emoji", cat.emoji)
                val itemsArr = JSONArray()
                for (item in cat.items) {
                    val itemObj = JSONObject().apply {
                        put("headline", item.headline)
                        put("summary", item.summary)
                        put("sourceChannel", item.sourceChannel)
                        put("importance", item.importance)
                    }
                    itemsArr.put(itemObj)
                }
                put("items", itemsArr)
            }
            jsonArray.put(catObj)
        }

        val channelsInvolved = unsummarized.map { it.channelUsername }.distinct().joinToString("، ") { "@$it" }
        val currentTime = System.currentTimeMillis()

        val summaryEntity = SummaryEntity(
            timestamp = currentTime,
            totalPostsProcessed = unsummarized.size,
            channelsInvolved = channelsInvolved,
            title = "خلاصه اخبار امروز (${unsummarized.size} پیام)",
            fullDigestJson = jsonArray.toString()
        )

        val id = database.summaryDao().insertSummary(summaryEntity)

        // Mark all these posts as summarized
        val postIds = unsummarized.map { it.id }
        database.postDao().markPostsAsSummarized(postIds)

        Result.success(summaryEntity.copy(id = id))
    }

    /**
     * Parses the stored JSON into strongly typed CategorizedDigest
     */
    fun parseSummaryDigest(jsonStr: String): List<CategorizedDigest> {
        val list = mutableListOf<CategorizedDigest>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val catObj = arr.getJSONObject(i)
                val category = catObj.getString("category")
                val emoji = catObj.optString("emoji", "📰")
                val itemsArr = catObj.getJSONArray("items")

                val items = mutableListOf<DigestItem>()
                for (j in 0 until itemsArr.length()) {
                    val itObj = itemsArr.getJSONObject(j)
                    items.add(
                        DigestItem(
                            headline = itObj.getString("headline"),
                            summary = itObj.getString("summary"),
                            sourceChannel = itObj.optString("sourceChannel", ""),
                            importance = itObj.optString("importance", "عادی")
                        )
                    )
                }
                list.add(CategorizedDigest(category, emoji, items))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved digest JSON", e)
        }
        return list
    }

    /**
     * Seeds initial sample posts for instant testing & demonstration
     */
    suspend fun seedDemoPosts() = withContext(Dispatchers.IO) {
        val demoPosts = listOf(
            PostEntity(
                channelUsername = "digiato",
                telegramPostId = 101,
                content = "شرکت هوش مصنوعی آنتروپیک و گوگل از مدل‌های استدلالی جدیدی رونمایی کردند که توانایی حل مسائل پیچیده ریاضی و برنامه‌نویسی را تا ۴۰ درصد بهبود بخشیده است.",
                publishedTime = "10:30",
                isSummarized = false
            ),
            PostEntity(
                channelUsername = "akharinkhabar",
                telegramPostId = 102,
                content = "رئیس کل بانک مرکزی اعلام کرد سامانه جدید ارزی برای تأمین نیازهای وارداتی بازرگانان از هفته آینده آغاز به کار خواهد کرد و نرخ ارز در کانال توافقی تثبیت می‌شود.",
                publishedTime = "11:15",
                isSummarized = false
            ),
            PostEntity(
                channelUsername = "varzesh3",
                telegramPostId = 103,
                content = "تیم ملی والیبال در دومین مسابقه از مرحله گروهی با نمایشی درخشان موفق به شکست ۳ بر ۱ رقیب اروپایی خود شد و به مرحله حذفی صعود کرد.",
                publishedTime = "12:00",
                isSummarized = false
            ),
            PostEntity(
                channelUsername = "digiato",
                telegramPostId = 104,
                content = "فضاپیمای تحقیقاتی جدید ناسا تصاویری با وضوح فوق‌العاده از قمر اروپا ثبت کرد که نشان‌دهنده لایه‌های آب مایع زیر سطح یخ‌زده آن است.",
                publishedTime = "12:45",
                isSummarized = false
            ),
            PostEntity(
                channelUsername = "akharinkhabar",
                telegramPostId = 105,
                content = "سازمان هواشناسی کشور نسبت به کاهش دما و رگبار باران در استان‌های شمالی و غربی کشور هشدار سطح زرد صادر کرد.",
                publishedTime = "13:20",
                isSummarized = false
            )
        )
        database.postDao().insertPosts(demoPosts)
    }

    suspend fun deleteSummary(id: Long) = withContext(Dispatchers.IO) {
        database.summaryDao().deleteSummary(id)
    }
}
