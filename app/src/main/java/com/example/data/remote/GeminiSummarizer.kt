package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.CategorizedDigest
import com.example.data.model.DigestItem
import com.example.data.model.PostEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiSummarizer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GeminiSummarizer"
        private const val MODEL = "gemini-3.5-flash"
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    }

    suspend fun summarizeAndCategorize(posts: List<PostEntity>): Result<List<CategorizedDigest>> =
        withContext(Dispatchers.IO) {
            if (posts.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val apiKey = BuildConfig.GEMINI_API_KEY.trim()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                Log.w(TAG, "Gemini API key is not set or placeholder. Falling back to local smart summarizer.")
                return@withContext Result.success(generateFallbackCategorization(posts))
            }

            try {
                // Combine posts into a structured textual input
                val postsTextBuilder = StringBuilder()
                posts.take(40).forEachIndexed { index, post ->
                    postsTextBuilder.append("--- خبر شماره ${index + 1} از کانال @${post.channelUsername} ---\n")
                    postsTextBuilder.append(post.content.take(600))
                    postsTextBuilder.append("\n\n")
                }

                val systemInstruction = """
شما یک دستیار هوشمند و سردبیر ارشد خبری به زبان فارسی هستید. وظیفه شما این است که اخبار دریافت شده از کانال‌های تلگرامی را بررسی کرده:
1. پیام‌های تبلیغاتی، تکراری، لینک‌های اسپم و غیرخبری را کاملاً حذف کنید.
2. اخبار را بر اساس موضوع در دسته‌های زیر دسته‌بندی کنید:
   - "سیاسی و بین‌الملل" (با ایموجی 🌐)
   - "اقتصادی، ارز و طلا" (با ایموجی 💰)
   - "فناوری و استارتاپ" (با ایموجی 💻)
   - "ورزشی" (با ایموجی ⚽)
   - "حوادث و عمومی" (با ایموجی 📰)
3. برای هر خبر یک عنوان کوتاه جذاب (headline)، خلاصه‌ای در یک الی دو جمله رسا و بدون ابهام (summary)، نام کانال منبع (sourceChannel) و درجه اهمیت ("مهم" یا "عادی") تعیین کنید.
4. خروجی باید حتماً و منحصراً یک آرایه معتبر JSON با ساختار زیر باشد:
[
  {
    "category": "نام دسته",
    "emoji": "ایموجی",
    "items": [
      {
        "headline": "عنوان کوتاه",
        "summary": "خلاصه در ۱ یا ۲ جمله",
        "sourceChannel": "نام_کانال",
        "importance": "مهم"
      }
    ]
  }
]
""".trimIndent()

                val prompt = "لطفاً اخبار زیر را بخوان و بر اساس دستورالعمل خلاصه‌سازی و دسته‌بندی کن:\n\n$postsTextBuilder"

                val requestJson = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)

                    val systemInstructionObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        }
                        put("parts", partsArray)
                    }
                    put("systemInstruction", systemInstructionObj)

                    val generationConfig = JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.3)
                    }
                    put("generationConfig", generationConfig)
                }

                val requestUrl = "$BASE_URL?key=$apiKey"
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(requestUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty()
                        Log.e(TAG, "Gemini API error code: ${response.code}, body: $errBody")
                        return@withContext Result.success(generateFallbackCategorization(posts))
                    }

                    val respBody = response.body?.string().orEmpty()
                    val respJson = JSONObject(respBody)
                    val candidates = respJson.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val rawText = parts?.optJSONObject(0)?.optString("text").orEmpty()

                    val parsedList = parseGeminiJsonResult(rawText)
                    if (parsedList.isNotEmpty()) {
                        Result.success(parsedList)
                    } else {
                        Result.success(generateFallbackCategorization(posts))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Gemini summarization", e)
                Result.success(generateFallbackCategorization(posts))
            }
        }

    private fun parseGeminiJsonResult(jsonText: String): List<CategorizedDigest> {
        val list = mutableListOf<CategorizedDigest>()
        try {
            val trimmed = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val catObj = array.optJSONObject(i) ?: continue
                val category = catObj.optString("category", "عمومی")
                val emoji = catObj.optString("emoji", "📰")
                val itemsArray = catObj.optJSONArray("items") ?: JSONArray()

                val itemsList = mutableListOf<DigestItem>()
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.optJSONObject(j) ?: continue
                    val headline = itemObj.optString("headline", "")
                    val summary = itemObj.optString("summary", "")
                    val sourceChannel = itemObj.optString("sourceChannel", "")
                    val importance = itemObj.optString("importance", "عادی")

                    if (headline.isNotBlank() || summary.isNotBlank()) {
                        itemsList.add(
                            DigestItem(
                                headline = headline.ifBlank { "خبر مهم" },
                                summary = summary,
                                sourceChannel = sourceChannel,
                                importance = importance
                            )
                        )
                    }
                }

                if (itemsList.isNotEmpty()) {
                    list.add(CategorizedDigest(category, emoji, itemsList))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini JSON output: $jsonText", e)
        }
        return list
    }

    /**
     * Fallback classifier when API key is missing or offline, ensuring uninterrupted UX
     */
    private fun generateFallbackCategorization(posts: List<PostEntity>): List<CategorizedDigest> {
        val categorized = mutableMapOf<String, MutableList<DigestItem>>()

        val keywordsEconomy = listOf("دلار", "ارز", "طلا", "سکه", "بورس", "تورم", "بانک", "قیمت", "اقتصاد", "یورو")
        val keywordsTech = listOf("اینترنت", "گوشی", "هوش مصنوعی", "سامسونگ", "اپل", "فناوری", "گوگل", "برنامه", "سایبر", "رایانه")
        val keywordsSport = listOf("فوتبال", "استقلال", "پرسپولیس", "لیگ", "گل", "ورزش", "تیم ملی", "بازی", "داور", "قهرمان")
        val keywordsPolitics = listOf("دولت", "مجلس", "رئیس جمهور", "مذاکره", "وزیر", "سیاست", "تهران", "بین‌الملل", "سفارت")

        for (post in posts) {
            val text = post.content.trim()
            if (text.length < 15) continue // Skip very short messages

            // Simple classification heuristic
            val category = when {
                keywordsEconomy.any { text.contains(it) } -> "اقتصادی و بازار"
                keywordsTech.any { text.contains(it) } -> "فناوری و دیجیتال"
                keywordsSport.any { text.contains(it) } -> "ورزشی"
                keywordsPolitics.any { text.contains(it) } -> "سیاسی و بین‌الملل"
                else -> "حوادث و عمومی"
            }

            val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: "خبر تلگرامی"
            val headline = if (firstLine.length > 55) firstLine.take(55) + "..." else firstLine
            val summary = if (text.length > 180) text.take(180) + "..." else text

            val list = categorized.getOrPut(category) { mutableListOf() }
            if (list.size < 5) {
                list.add(
                    DigestItem(
                        headline = headline,
                        summary = summary,
                        sourceChannel = post.channelUsername,
                        importance = if (text.contains("فوری") || text.contains("مهم")) "مهم" else "عادی"
                    )
                )
            }
        }

        return categorized.map { (cat, items) ->
            val emoji = when (cat) {
                "اقتصادی و بازار" -> "💰"
                "فناوری و دیجیتال" -> "💻"
                "ورزشی" -> "⚽"
                "سیاسی و بین‌الملل" -> "🌐"
                else -> "📰"
            }
            CategorizedDigest(category = cat, emoji = emoji, items = items)
        }
    }
}
