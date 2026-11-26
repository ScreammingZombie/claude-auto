package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ClaudeAutoRunRoot
import com.example.ui.theme.ClaudeAutoRunTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.util.SystemSettingsHelper

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val app = ClaudeAutoRunApp.from(this)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(app))
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                viewModel.refreshDiagnostics()
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !SystemSettingsHelper.hasNotificationPermission(this@MainActivity)
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDiagnostics()
            }

            ClaudeAutoRunTheme {
                ClaudeAutoRunRoot(
                    uiState = uiState,
                    onUpdateTime = viewModel::updateTime,
                    onToggleDay = viewModel::toggleDay,
                    onToggleAllDays = viewModel::toggleAllDays,
                    onUpdateConversationTitle = viewModel::updateConversationTitle,
                    onUpdateResumePrompt = viewModel::updateResumePrompt,
                    onResetDefaultPrompt = viewModel::resetDefaultPrompt,
                    onUpdateMaxDuration = viewModel::updateMaxDuration,
                    onUpdateTurnOffWifi = viewModel::updateTurnOffWifi,
                    onUpdateOpenBrowser = viewModel::updateOpenDefaultBrowser,
                    onSaveSchedule = viewModel::saveAndSchedule,
                    onRunNow = viewModel::runNow,
                    onStop = viewModel::stopAutomation,
                    onCancelSchedule = viewModel::cancelSchedule,
                    onClearLogs = viewModel::clearLogs,
                    onClearHistory = viewModel::clearHistory,
                    onRefreshDiagnostics = viewModel::refreshDiagnostics,
                    onOpenAccessibilitySettings = {
                        runCatching {
                            startActivity(SystemSettingsHelper.buildAccessibilitySettingsIntent())
                        }
                    },
                    onOpenExactAlarmSettings = {
                        runCatching {
                            startActivity(SystemSettingsHelper.buildExactAlarmSettingsIntent(this@MainActivity))
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (SystemSettingsHelper.hasNotificationPermission(this@MainActivity)) {
                                runCatching {
                                    startActivity(SystemSettingsHelper.buildNotificationSettingsIntent(this@MainActivity))
                                }
                            } else {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            runCatching {
                                startActivity(SystemSettingsHelper.buildNotificationSettingsIntent(this@MainActivity))
                            }
                        }
                    },
                    onOpenBatterySettings = {
                        runCatching {
                            startActivity(SystemSettingsHelper.buildBatteryOptimizationSettingsIntent(this@MainActivity))
                        }
                    },
                    onAddLimitPhrase = viewModel::addLimitPhrase,
                    onRemoveLimitPhrase = viewModel::removeLimitPhrase,
                    onResetDefaultPhrases = viewModel::resetDefaultLimitPhrases,
                    onDismissBanner = viewModel::dismissBanner,
                )
            }
        }
    }
}
