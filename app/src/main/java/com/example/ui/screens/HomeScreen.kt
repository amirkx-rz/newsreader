package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DigestUiState
import com.example.ui.TelegramDigestViewModel
import com.example.ui.components.CategorySection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: DigestUiState,
    viewModel: TelegramDigestViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_lazy_column"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Hero Action Section
        item {
            HeroActionCard(
                unsummarizedCount = uiState.unsummarizedCount,
                channelsCount = uiState.channels.count { it.isEnabled },
                isLoading = uiState.isLoading,
                isRefreshing = uiState.isRefreshing,
                onGenerateDigest = { viewModel.generateDigest() },
                onRefresh = { viewModel.refreshChannels() },
                onSeedDemo = { viewModel.seedDemoData() }
            )
        }

        // Active filter chips
        if (uiState.parsedCategories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("همه موضوعات") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    uiState.parsedCategories.forEach { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category.category,
                            onClick = { viewModel.selectCategory(category.category) },
                            label = { Text("${category.emoji} ${category.category}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Digest list content
        if (uiState.parsedCategories.isEmpty()) {
            item {
                EmptyDigestPlaceholder(
                    unsummarizedCount = uiState.unsummarizedCount,
                    onGenerate = { viewModel.generateDigest() },
                    onSeed = { viewModel.seedDemoData() }
                )
            }
        } else {
            val filteredCategories = if (uiState.selectedCategory == null) {
                uiState.parsedCategories
            } else {
                uiState.parsedCategories.filter { it.category == uiState.selectedCategory }
            }

            items(filteredCategories) { category ->
                CategorySection(category = category)
            }
        }
    }
}

@Composable
fun HeroActionCard(
    unsummarizedCount: Int,
    channelsCount: Int,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onGenerateDigest: () -> Unit,
    onRefresh: () -> Unit,
    onSeedDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("hero_action_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "خلاصه هوشمند تلگرام",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$channelsCount کانال فعال در حال رصد",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing && !isLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("refresh_channels_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "به‌روزرسانی پیام‌ها",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (unsummarizedCount > 0) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (unsummarizedCount > 0)
                                "⚡ $unsummarizedCount پیام جدید آماده خلاصه‌سازی"
                            else
                                "✓ تمام پیام‌های دریافتی تاکنون خلاصه شده‌اند",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (unsummarizedCount > 0) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Generate Button
                Button(
                    onClick = onGenerateDigest,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_digest_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال استخراج و تحلیل هوش مصنوعی...")
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (unsummarizedCount > 0) "خلاصه‌سازی $unsummarizedCount خبر جدید" else "بررسی و خلاصه‌سازی اخبار امروز",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary test/demo button
                OutlinedButton(
                    onClick = onSeedDemo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("seed_demo_button")
                ) {
                    Icon(imageVector = Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن پیام تستی برای بررسی عملکرد")
                }
            }
        }
    }
}

@Composable
fun EmptyDigestPlaceholder(
    unsummarizedCount: Int,
    onGenerate: () -> Unit,
    onSeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "هنوز خلاصه‌ای ایجاد نشده است",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "دکمه «خلاصه‌سازی اخبار» را بزنید تا اخبار کانال‌های منتخب شما تحلیل شده و در قالب بولت‌پوینت‌های مرتب نمایش داده شوند.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
