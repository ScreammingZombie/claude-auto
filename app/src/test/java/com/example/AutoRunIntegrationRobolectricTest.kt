package com.example

import android.app.AlarmManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.automation.AutomationCoordinator
import com.example.data.AutoRunRepository
import com.example.model.ActivityLogEntry
import com.example.model.AutoRunConfig
import com.example.model.AutomationStatus
import com.example.model.ExecutionHistoryItem
import com.example.scheduler.AlarmScheduler
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoRunIntegrationRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun pumpMainLooperUntil(timeoutMs: Long = 3000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `repository persists and restores config logs and history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AutoRunRepository(context)

        val customConfig = AutoRunConfig(
            hour = 22,
            minute = 45,
            selectedDays = setOf(Calendar.MONDAY, Calendar.THURSDAY),
            conversationTitle = "Proyecto Kernel",
            resumePrompt = "Sigue con el análisis",
            maxDurationHours = 2,
            maxDurationMinutes = 15,
            turnOffWifiOnFinish = false,
            openDefaultBrowser = true,
            isScheduleActive = true,
            nextExecutionEpochMillis = 1_800_000_000_000L,
        )
        repo.saveConfig(customConfig)
        val loaded = repo.getConfig()

        assertEquals(22, loaded.hour)
        assertEquals(45, loaded.minute)
        assertEquals(setOf(Calendar.MONDAY, Calendar.THURSDAY), loaded.selectedDays)
        assertEquals("Proyecto Kernel", loaded.conversationTitle)
        assertEquals("Sigue con el análisis", loaded.resumePrompt)
        assertEquals(2, loaded.maxDurationHours)
        assertEquals(15, loaded.maxDurationMinutes)
        assertFalse(loaded.turnOffWifiOnFinish)
        assertTrue(loaded.isScheduleActive)

        repo.appendLog(ActivityLogEntry(status = AutomationStatus.EJECUTANDO, message = "Prueba log"))
        assertTrue(repo.logsFlow.first().any { it.message == "Prueba log" })
        repo.clearLogs()
        assertTrue(repo.logsFlow.first().isEmpty())

        repo.upsertHistoryItem(
            ExecutionHistoryItem(
                startTimeMillis = 1000L,
                finishReason = "Completada prueba",
            ),
        )
        assertEquals(1, repo.historyFlow.first().size)
        repo.clearHistory()
        assertTrue(repo.historyFlow.first().isEmpty())

        assertTrue(repo.tryClaimAlarmMinuteKey("2026-11-25 08:00"))
        assertFalse(repo.tryClaimAlarmMinuteKey("2026-11-25 08:00"))
    }

    @Test
    fun `alarm scheduler schedules replaces without duplicates and cancels alarm`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        val scheduler = AlarmScheduler(context)

        val config = AutoRunConfig(
            hour = 9,
            minute = 30,
            selectedDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
        )
        val first = scheduler.scheduleNextAlarm(config)
        assertTrue(first.scheduled)
        assertNotNull(first.nextExecutionMillis)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        // Volver a programar reemplaza el PendingIntent sin duplicar alarmas
        val second = scheduler.scheduleNextAlarm(config.copy(hour = 10, minute = 0))
        assertTrue(second.scheduled)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        scheduler.cancelAlarm()
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `coordinator reports clear error when accessibility service is inactive`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AutoRunRepository(context)
        repo.clearLogs()
        repo.clearHistory()
        val coordinator = AutomationCoordinator(context, repo, Dispatchers.IO)

        coordinator.startAutomation("Prueba")
        delay(800)

        val state = coordinator.runtimeState.value
        assertEquals(AutomationStatus.ERROR, state.status)
        val logs = repo.logsFlow.first()
        assertTrue(logs.any { it.isError && it.message.contains("Accesibilidad", ignoreCase = true) })
        val history = repo.historyFlow.first()
        assertTrue(history.isNotEmpty())
        assertTrue(history.first().errorsFound.isNotEmpty())
    }

    @Test
    fun `viewmodel runNow and stopAutomation handle lifecycle cleanly`() {
        val app = ApplicationProvider.getApplicationContext<ClaudeAutoRunApp>()
        val vm = MainViewModel(
            application = app,
            repository = app.repository,
            alarmScheduler = app.alarmScheduler,
            coordinator = app.coordinator,
            startServiceAction = { src -> app.coordinator.startAutomation(src) },
            stopServiceAction = { app.coordinator.stopAutomation("Detenida en test") },
        )

        vm.updateTime(14, 20)
        vm.updateConversationTitle("Test Chat")
        vm.saveAndSchedule()
        pumpMainLooperUntil { vm.uiState.value.effectiveStatus == AutomationStatus.PROGRAMADA }
        assertEquals(AutomationStatus.PROGRAMADA, vm.uiState.value.effectiveStatus)

        vm.runNow()
        pumpMainLooperUntil { vm.uiState.value.savedConfig.conversationTitle == "Test Chat" }
        vm.stopAutomation()
        pumpMainLooperUntil { !vm.uiState.value.runtimeState.status.isActiveExecution }
        assertFalse(vm.uiState.value.runtimeState.status.isActiveExecution)

        vm.cancelSchedule()
        pumpMainLooperUntil { !vm.uiState.value.savedConfig.isScheduleActive }
        assertFalse(vm.uiState.value.savedConfig.isScheduleActive)
    }

    @Test
    fun `compose ui saves cancels schedule edits fields and navigates tabs`() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Claude AutoRun").assertIsDisplayed()
        composeRule.onNodeWithTag("status_badge").assertIsDisplayed()

        // Editar título de conversación y guardar programación
        composeRule.onNodeWithTag("control_lazy_column")
            .performScrollToKey("conversation")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("conversation_title_input")
            .performTextReplacement("Refactor API")

        composeRule.onNodeWithTag("control_lazy_column")
            .performScrollToKey("actions")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("save_schedule_button")
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("control_lazy_column").performScrollToKey("status")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("status_badge").assertIsDisplayed()

        // Cancelar programación
        composeRule.onNodeWithTag("control_lazy_column").performScrollToKey("actions")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cancel_schedule_button")
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodesWithText("Programación cancelada.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("control_lazy_column").performScrollToKey("status")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("next_execution_text")
            .assertTextEquals("Sin programación activa")

        // Navegar a Historial y Sistema
        composeRule.onNodeWithTag("tab_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_history_button").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_system").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("accessibility_check_card").assertIsDisplayed()
    }
}
