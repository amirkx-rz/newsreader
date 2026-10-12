package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.CategorizedDigest
import com.example.data.model.ChannelEntity
import com.example.data.model.SummaryEntity
import com.example.data.repository.TelegramDigestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppNavTab {
    DIGEST,
    CHANNELS,
    HISTORY
}

data class DigestUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val channels: List<ChannelEntity> = emptyList(),
    val unsummarizedCount: Int = 0,
    val latestSummary: SummaryEntity? = null,
    val parsedCategories: List<CategorizedDigest> = emptyList(),
    val selectedCategory: String? = null, // null = all
    val currentTab: AppNavTab = AppNavTab.DIGEST,
    val allHistory: List<SummaryEntity> = emptyList()
)

class TelegramDigestViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = TelegramDigestRepository(database)

    private val _uiState = MutableStateFlow(DigestUiState())
    val uiState: StateFlow<DigestUiState> = _uiState.asStateFlow()

    init {
        // Collect channels
        viewModelScope.launch {
            repository.allChannels.collect { channelList ->
                _uiState.update { it.copy(channels = channelList) }
            }
        }

        // Collect unsummarized post count
        viewModelScope.launch {
            repository.unsummarizedCount.collect { count ->
                _uiState.update { it.copy(unsummarizedCount = count) }
            }
        }

        // Collect latest summary
        viewModelScope.launch {
            repository.latestSummary.collect { latest ->
                val parsed = if (latest != null) {
                    repository.parseSummaryDigest(latest.fullDigestJson)
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        latestSummary = latest,
                        parsedCategories = parsed
                    )
                }
            }
        }

        // Collect history
        viewModelScope.launch {
            repository.allSummaries.collect { history ->
                _uiState.update { it.copy(allHistory = history) }
            }
        }

        // Seed default channels and demo news on first launch
        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
        }
    }

    fun selectTab(tab: AppNavTab) {
        _uiState.update { it.copy(currentTab = tab, errorMessage = null) }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun refreshChannels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                val newPosts = repository.refreshAllChannels()
                val message = if (newPosts > 0) {
                    "$newPosts پیام جدید از کانال‌ها دریافت شد"
                } else {
                    "کانال‌ها به‌روز هستند (پیام جدیدی یافت نشد)"
                }
                _uiState.update { it.copy(statusMessage = message, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = "خطا در به‌روزرسانی کانال‌ها: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun generateDigest() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    statusMessage = "در حال واکشی پیام‌های جدید و پردازش هوش مصنوعی...",
                    errorMessage = null
                )
            }

            try {
                val result = repository.generateDigest()
                if (result.isSuccess) {
                    val summary = result.getOrThrow()
                    val parsed = repository.parseSummaryDigest(summary.fullDigestJson)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "خلاصه اخبار جدید با موفقیت آماده شد!",
                            latestSummary = summary,
                            parsedCategories = parsed,
                            selectedCategory = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.exceptionOrNull()?.message ?: "خطا در پردازش خلاصه اخبار"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "خطا در خلاصه‌سازی: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun addChannel(input: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.addChannel(input)
            if (result.isSuccess) {
                val channel = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "کانال ${channel.title} با موفقیت افزوده شد"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "خطا در افزودن کانال"
                    )
                }
            }
        }
    }

    fun removeChannel(username: String) {
        viewModelScope.launch {
            repository.removeChannel(username)
            _uiState.update { it.copy(statusMessage = "کانال @$username حذف شد") }
        }
    }

    fun toggleChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.toggleChannel(channel)
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.seedDemoPosts()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusMessage = "پیام‌های تستی نمونه با موفقیت به صندوق اخبار خوانده‌نشده افزوده شد"
                )
            }
        }
    }

    fun deleteSummary(id: Long) {
        viewModelScope.launch {
            repository.deleteSummary(id)
            _uiState.update { it.copy(statusMessage = "خلاصه مورد نظر حذف شد") }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
