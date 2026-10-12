package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppNavTab
import com.example.ui.TelegramDigestViewModel
import com.example.ui.screens.ChannelsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TelegramDigestViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val snackbarHostState = remember { SnackbarHostState() }

                    // Handle status message snackbar
                    LaunchedEffect(uiState.statusMessage) {
                        uiState.statusMessage?.let { msg ->
                            snackbarHostState.showSnackbar(
                                message = msg,
                                duration = SnackbarDuration.Short
                            )
                            viewModel.clearStatusMessage()
                        }
                    }

                    // Handle error message snackbar
                    LaunchedEffect(uiState.errorMessage) {
                        uiState.errorMessage?.let { msg ->
                            snackbarHostState.showSnackbar(
                                message = msg,
                                duration = SnackbarDuration.Long
                            )
                            viewModel.clearStatusMessage()
                        }
                    }

                    // Handle back button when not on main digest tab
                    BackHandler(enabled = uiState.currentTab != AppNavTab.DIGEST) {
                        viewModel.selectTab(AppNavTab.DIGEST)
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.app_logo_1791830326922),
                                            contentDescription = "آیکون برنامه",
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = when (uiState.currentTab) {
                                                AppNavTab.DIGEST -> "خلاصه اخبار تلگرام"
                                                AppNavTab.CHANNELS -> "کانال‌های دنبال‌شده"
                                                AppNavTab.HISTORY -> "آرشیو خلاصه‌ها"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                                NavigationBarItem(
                                    selected = uiState.currentTab == AppNavTab.DIGEST,
                                    onClick = { viewModel.selectTab(AppNavTab.DIGEST) },
                                    icon = {
                                        Icon(
                                            imageVector = if (uiState.currentTab == AppNavTab.DIGEST)
                                                Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                            contentDescription = "خلاصه اخبار"
                                        )
                                    },
                                    label = { Text("خلاصه اخبار") },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_digest")
                                )

                                NavigationBarItem(
                                    selected = uiState.currentTab == AppNavTab.CHANNELS,
                                    onClick = { viewModel.selectTab(AppNavTab.CHANNELS) },
                                    icon = {
                                        Icon(
                                            imageVector = if (uiState.currentTab == AppNavTab.CHANNELS)
                                                Icons.Filled.Dns else Icons.Outlined.Dns,
                                            contentDescription = "کانال‌ها"
                                        )
                                    },
                                    label = { Text("کانال‌ها") },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_channels")
                                )

                                NavigationBarItem(
                                    selected = uiState.currentTab == AppNavTab.HISTORY,
                                    onClick = { viewModel.selectTab(AppNavTab.HISTORY) },
                                    icon = {
                                        Icon(
                                            imageVector = if (uiState.currentTab == AppNavTab.HISTORY)
                                                Icons.Filled.History else Icons.Outlined.History,
                                            contentDescription = "آرشیو"
                                        )
                                    },
                                    label = { Text("آرشیو") },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_history")
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (uiState.currentTab) {
                                AppNavTab.DIGEST -> {
                                    HomeScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                }
                                AppNavTab.CHANNELS -> {
                                    ChannelsScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                }
                                AppNavTab.HISTORY -> {
                                    HistoryScreen(
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
