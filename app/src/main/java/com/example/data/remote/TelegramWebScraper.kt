package com.example.data.remote

import android.util.Log
import com.example.data.model.ChannelEntity
import com.example.data.model.ParsedTelegramPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class TelegramWebScraper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        private const val TAG = "TelegramWebScraper"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        /**
         * Cleans user input like https://t.me/digiato, @digiato, or t.me/s/digiato
         * into a pure username "digiato".
         */
        fun normalizeUsername(input: String): String {
            var cleaned = input.trim()
            if (cleaned.startsWith("https://")) cleaned = cleaned.removePrefix("https://")
            if (cleaned.startsWith("http://")) cleaned = cleaned.removePrefix("http://")
            if (cleaned.startsWith("t.me/s/")) cleaned = cleaned.removePrefix("t.me/s/")
            if (cleaned.startsWith("t.me/")) cleaned = cleaned.removePrefix("t.me/")
            if (cleaned.startsWith("telegram.me/")) cleaned = cleaned.removePrefix("telegram.me/")
            if (cleaned.startsWith("@")) cleaned = cleaned.removePrefix("@")
            // Remove trailing slashes or queries
            cleaned = cleaned.split("/", "?", "#")[0].trim()
            return cleaned.lowercase()
        }
    }

    /**
     * Fetches public channel info (Title, description, avatar)
     */
    suspend fun fetchChannelInfo(username: String): Result<ChannelEntity> = withContext(Dispatchers.IO) {
        val cleanUser = normalizeUsername(username)
        if (cleanUser.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("نام کاربری کانال نامعتبر است"))
        }

        try {
            val url = "https://t.me/s/$cleanUser"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "fa,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("خطا در دریافت اطلاعات کانال از تلگرام: کد ${response.code}")
                    )
                }

                val html = response.body?.string().orEmpty()
                val doc = Jsoup.parse(html)

                val titleEl = doc.selectFirst(".tgme_channel_info_header_title span, .tgme_page_title span, .tgme_channel_info_header_title")
                val title = titleEl?.text()?.takeIf { it.isNotBlank() } ?: "@$cleanUser"

                val descEl = doc.selectFirst(".tgme_channel_info_description, .tgme_page_description")
                val description = descEl?.text() ?: ""

                val avatarEl = doc.selectFirst(".tgme_page_photo_image, .tgme_channel_info_header img")
                val avatarUrl = avatarEl?.attr("src")

                // Determine last post ID if any exist
                val messageElements = doc.select(".tgme_widget_message[data-post]")
                var maxPostId = 0L
                for (el in messageElements) {
                    val dataPost = el.attr("data-post") // e.g. "digiato/12345"
                    val idPart = dataPost.substringAfterLast("/").toLongOrNull() ?: 0L
                    if (idPart > maxPostId) maxPostId = idPart
                }

                Result.success(
                    ChannelEntity(
                        username = cleanUser,
                        title = title,
                        description = description,
                        avatarUrl = avatarUrl,
                        lastFetchedPostId = maxPostId
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channel info for $cleanUser", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches recent messages from public channel preview
     */
    suspend fun fetchRecentPosts(
        username: String,
        afterPostId: Long = 0L
    ): Result<List<ParsedTelegramPost>> = withContext(Dispatchers.IO) {
        val cleanUser = normalizeUsername(username)
        try {
            // If afterPostId is given, Telegram supports after parameter: ?after={id}
            val url = if (afterPostId > 0) {
                "https://t.me/s/$cleanUser?after=$afterPostId"
            } else {
                "https://t.me/s/$cleanUser"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "fa,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("خطا در واکشی پیام‌ها: کد ${response.code}")
                    )
                }

                val html = response.body?.string().orEmpty()
                val doc = Jsoup.parse(html)
                val messageElements = doc.select(".tgme_widget_message[data-post]")
                val posts = mutableListOf<ParsedTelegramPost>()

                for (el in messageElements) {
                    val dataPost = el.attr("data-post")
                    val postId = dataPost.substringAfterLast("/").toLongOrNull() ?: continue
                    if (postId <= afterPostId) continue

                    // Extract text content cleanly
                    val textEl = el.selectFirst(".tgme_widget_message_text")
                    val rawText = textEl?.wholeText()?.trim().orEmpty()

                    if (rawText.isBlank()) continue

                    val timeEl = el.selectFirst("time")
                    val timeStr = timeEl?.text().orEmpty()

                    posts.add(
                        ParsedTelegramPost(
                            id = postId,
                            text = rawText,
                            time = timeStr,
                            channelUsername = cleanUser
                        )
                    )
                }

                Result.success(posts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching posts for $cleanUser", e)
            Result.failure(e)
        }
    }
}
