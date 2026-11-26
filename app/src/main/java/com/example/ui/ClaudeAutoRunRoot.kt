package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.screens.ControlScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SystemScreen
import com.example.ui.screens.statusColors
import com.example.ui.theme.ClaudeCoral
import com.example.ui.theme.ClaudeCoralContainer
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateOutlineSubtle
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainUiState

enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    CONTROL("Control", Icons.Filled.Tune, Icons.Outlined.Tune, "tab_control"),
    HISTORY("Historial", Icons.Filled.History, Icons.Outlined.History, "tab_history"),
    SYSTEM("Sistema", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_system"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeAutoRunRoot(
    uiState: MainUiState,
    onUpdateTime: (Int, Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onToggleAllDays: () -> Unit,
    onUpdateConversationTitle: (String) -> Unit,
    onUpdateResumePrompt: (String) -> Unit,
    onResetDefaultPrompt: () -> Unit,
    onUpdateMaxDuration: (Int, Int) -> Unit,
    onUpdateTurnOffWifi: (Boolean) -> Unit,
    onUpdateOpenBrowser: (Boolean) -> Unit,
    onSaveSchedule: () -> Unit,
    onRunNow: () -> Unit,
    onStop: () -> Unit,
    onCancelSchedule: () -> Unit,
    onClearLogs: () -> Unit,
    onClearHistory: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onAddLimitPhrase: (String) -> Unit,
    onRemoveLimitPhrase: (String) -> Unit,
    onResetDefaultPhrases: () -> Unit,
    onDismissBanner: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.CONTROL) }
    val status = uiState.effectiveStatus
    val (statusAccent, statusBg) = statusColors(status)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ClaudeCoral.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(TechCyan.copy(alpha = 0.06f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.15f),
                        radius = size.maxDimension * 0.5f,
                    ),
                )
            },
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SlateSurface.copy(alpha = 0.92f),
                        titleContentColor = TextPrimary,
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ClaudeCoralContainer,
                                border = BorderStroke(1.dp, ClaudeCoral.copy(alpha = 0.5f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = "Claude AutoRun",
                                    tint = ClaudeCoral,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Claude AutoRun",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "AUTOMATIZACIÓN LOCAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusBg,
                            border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.55f)),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = statusAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = SlateSurface,
                        tonalElevation = 0.dp,
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ClaudeCoral,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = ClaudeCoralContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = SlateSurface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(Modifier.size(8.dp))
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ClaudeCoral,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = ClaudeCoralContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(SlateOutlineSubtle),
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val contentModifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)

                    when (selectedTab) {
                        AppTab.CONTROL -> ControlScreen(
                            uiState = uiState,
                            onUpdateTime = onUpdateTime,
                            onToggleDay = onToggleDay,
                            onToggleAllDays = onToggleAllDays,
                            onUpdateConversationTitle = onUpdateConversationTitle,
                            onUpdateResumePrompt = onUpdateResumePrompt,
                            onResetDefaultPrompt = onResetDefaultPrompt,
                            onUpdateMaxDuration = onUpdateMaxDuration,
                            onUpdateTurnOffWifi = onUpdateTurnOffWifi,
                            onUpdateOpenBrowser = onUpdateOpenBrowser,
                            onSaveSchedule = onSaveSchedule,
                            onRunNow = onRunNow,
                            onStop = onStop,
                            onCancelSchedule = onCancelSchedule,
                            onClearLogs = onClearLogs,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onDismissBanner = onDismissBanner,
                            modifier = contentModifier,
                        )

                        AppTab.HISTORY -> HistoryScreen(
                            history = uiState.history,
                            onClearHistory = onClearHistory,
                            modifier = contentModifier,
                        )

                        AppTab.SYSTEM -> SystemScreen(
                            diagnostics = uiState.diagnostics,
                            limitPhrases = uiState.draftConfig.limitPhrases,
                            onRefreshDiagnostics = onRefreshDiagnostics,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenBatterySettings = onOpenBatterySettings,
                            onAddLimitPhrase = onAddLimitPhrase,
                            onRemoveLimitPhrase = onRemoveLimitPhrase,
                            onResetDefaultPhrases = onResetDefaultPhrases,
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }
}
