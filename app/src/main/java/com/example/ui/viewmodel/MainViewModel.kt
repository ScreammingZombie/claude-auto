package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ClaudeAutoRunApp
import com.example.automation.AutomationCoordinator
import com.example.data.AutoRunRepository
import com.example.model.ActivityLogEntry
import com.example.model.AutoRunConfig
import com.example.model.AutomationRuntimeState
import com.example.model.AutomationStatus
import com.example.model.DefaultAutomationText
import com.example.model.ExecutionHistoryItem
import com.example.model.ScheduleDays
import com.example.model.SystemDiagnosticsState
import com.example.scheduler.AlarmScheduler
import com.example.scheduler.ScheduleCalculator
import com.example.service.AutoRunForegroundService
import com.example.util.SystemSettingsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val draftConfig: AutoRunConfig = AutoRunConfig(),
    val savedConfig: AutoRunConfig = AutoRunConfig(),
    val runtimeState: AutomationRuntimeState = AutomationRuntimeState(),
    val logs: List<ActivityLogEntry> = emptyList(),
    val history: List<ExecutionHistoryItem> = emptyList(),
    val diagnostics: SystemDiagnosticsState = SystemDiagnosticsState(),
    val feedbackBanner: String? = null,
    val isFeedbackError: Boolean = false,
) {
    val effectiveStatus: AutomationStatus
        get() = when {
            runtimeState.status.isActiveExecution -> runtimeState.status
            runtimeState.status == AutomationStatus.ERROR ||
                runtimeState.status == AutomationStatus.LIMITE_DETECTADO ||
                runtimeState.status == AutomationStatus.COMPLETADA -> runtimeState.status
            savedConfig.isScheduleActive && savedConfig.nextExecutionEpochMillis != null ->
                AutomationStatus.PROGRAMADA
            else -> AutomationStatus.INACTIVA
        }
}

class MainViewModel(
    application: Application,
    private val repository: AutoRunRepository,
    private val alarmScheduler: AlarmScheduler,
    private val coordinator: AutomationCoordinator,
    private val startServiceAction: (String) -> Unit = { source ->
        AutoRunForegroundService.start(application, source)
    },
    private val stopServiceAction: () -> Unit = {
        AutoRunForegroundService.stop(application)
    },
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var initializedFromPersistence = false

    init {
        refreshDiagnostics()

        viewModelScope.launch {
            repository.configFlow.collectLatest { saved ->
                _uiState.update { current ->
                    val nextDraft = if (!initializedFromPersistence) {
                        initializedFromPersistence = true
                        saved
                    } else {
                        current.draftConfig.copy(
                            isScheduleActive = saved.isScheduleActive,
                            nextExecutionEpochMillis = saved.nextExecutionEpochMillis,
                            limitPhrases = saved.limitPhrases,
                        )
                    }
                    current.copy(
                        savedConfig = saved,
                        draftConfig = nextDraft,
                    )
                }
            }
        }

        viewModelScope.launch {
            coordinator.runtimeState.collectLatest { runtime ->
                _uiState.update { it.copy(runtimeState = runtime) }
            }
        }

        viewModelScope.launch {
            repository.logsFlow.collectLatest { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }

        viewModelScope.launch {
            repository.historyFlow.collectLatest { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun refreshDiagnostics() {
        val diag = SystemSettingsHelper.collectDiagnostics(getApplication())
        _uiState.update { it.copy(diagnostics = diag) }
    }

    fun updateTime(hour: Int, minute: Int) {
        _uiState.update { state ->
            state.copy(
                draftConfig = state.draftConfig.copy(
                    hour = hour.coerceIn(0, 23),
                    minute = minute.coerceIn(0, 59),
                ),
            )
        }
    }

    fun toggleDay(calendarDay: Int) {
        _uiState.update { state ->
            val currentDays = state.draftConfig.selectedDays.toMutableSet()
            if (currentDays.contains(calendarDay)) {
                currentDays.remove(calendarDay)
            } else {
                currentDays.add(calendarDay)
            }
            state.copy(draftConfig = state.draftConfig.copy(selectedDays = currentDays))
        }
    }

    fun toggleAllDays() {
        _uiState.update { state ->
            val nextDays = if (state.draftConfig.allDaysSelected) {
                emptySet()
            } else {
                ScheduleDays.ALL_CALENDAR_DAYS
            }
            state.copy(draftConfig = state.draftConfig.copy(selectedDays = nextDays))
        }
    }

    fun updateConversationTitle(title: String) {
        _uiState.update { state ->
            state.copy(draftConfig = state.draftConfig.copy(conversationTitle = title))
        }
    }

    fun updateResumePrompt(prompt: String) {
        _uiState.update { state ->
            state.copy(draftConfig = state.draftConfig.copy(resumePrompt = prompt))
        }
    }

    fun resetDefaultPrompt() {
        _uiState.update { state ->
            state.copy(
                draftConfig = state.draftConfig.copy(
                    resumePrompt = DefaultAutomationText.DEFAULT_RESUME_PROMPT,
                ),
                feedbackBanner = "Instrucción predeterminada restaurada.",
                isFeedbackError = false,
            )
        }
    }

    fun updateMaxDuration(hours: Int, minutes: Int) {
        val safeHours = hours.coerceIn(0, 12)
        var safeMinutes = minutes.coerceIn(0, 59)
        if (safeHours == 0 && safeMinutes < 1) {
            safeMinutes = 1
        }
        _uiState.update { state ->
            state.copy(
                draftConfig = state.draftConfig.copy(
                    maxDurationHours = safeHours,
                    maxDurationMinutes = safeMinutes,
                ),
            )
        }
    }

    fun updateTurnOffWifi(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(draftConfig = state.draftConfig.copy(turnOffWifiOnFinish = enabled))
        }
    }

    fun updateOpenDefaultBrowser(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(draftConfig = state.draftConfig.copy(openDefaultBrowser = enabled))
        }
    }

    fun addLimitPhrase(phrase: String) {
        val cleaned = phrase.trim()
        if (cleaned.length < 6) {
            setFeedback("Ingresa una frase de al menos 6 caracteres para evitar falsos positivos.", true)
            return
        }
        viewModelScope.launch {
            val current = _uiState.value.draftConfig
            val updatedList = (current.limitPhrases + cleaned).distinct()
            val updated = current.copy(limitPhrases = updatedList)
            repository.saveConfig(updated)
            _uiState.update { it.copy(draftConfig = updated) }
            setFeedback("Frase de límite añadida.", false)
        }
    }

    fun removeLimitPhrase(phrase: String) {
        viewModelScope.launch {
            val current = _uiState.value.draftConfig
            val updatedList = current.limitPhrases.filterNot { it == phrase }
            if (updatedList.isEmpty()) {
                setFeedback("Debe quedar al menos una frase de detección.", true)
                return@launch
            }
            val updated = current.copy(limitPhrases = updatedList)
            repository.saveConfig(updated)
            _uiState.update { it.copy(draftConfig = updated) }
        }
    }

    fun resetDefaultLimitPhrases() {
        viewModelScope.launch {
            val current = _uiState.value.draftConfig
            val updated = current.copy(limitPhrases = DefaultAutomationText.DEFAULT_LIMIT_PHRASES)
            repository.saveConfig(updated)
            _uiState.update { it.copy(draftConfig = updated) }
            setFeedback("Frases predeterminadas restauradas.", false)
        }
    }

    fun saveAndSchedule() {
        val draft = _uiState.value.draftConfig
        if (draft.selectedDays.isEmpty()) {
            setFeedback("Selecciona al menos un día de la semana antes de guardar.", true)
            return
        }

        viewModelScope.launch {
            val result = alarmScheduler.scheduleNextAlarm(draft)
            val configToSave = draft.copy(
                isScheduleActive = result.scheduled,
                nextExecutionEpochMillis = result.nextExecutionMillis,
            )
            repository.saveConfig(configToSave)
            coordinator.markScheduleUpdated(result.scheduled, result.nextExecutionMillis)
            repository.appendLog(
                ActivityLogEntry(
                    status = if (result.scheduled) AutomationStatus.PROGRAMADA else AutomationStatus.ERROR,
                    message = result.message,
                    isError = !result.scheduled,
                ),
            )
            _uiState.update {
                it.copy(
                    draftConfig = configToSave,
                    savedConfig = configToSave,
                    feedbackBanner = result.message,
                    isFeedbackError = !result.scheduled,
                )
            }
            refreshDiagnostics()
        }
    }

    fun cancelSchedule() {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm()
            val updated = _uiState.value.draftConfig.copy(
                isScheduleActive = false,
                nextExecutionEpochMillis = null,
            )
            repository.saveConfig(updated)
            coordinator.markScheduleUpdated(false, null)
            repository.appendLog(
                ActivityLogEntry(
                    status = AutomationStatus.INACTIVA,
                    message = "Programación automática cancelada.",
                ),
            )
            _uiState.update {
                it.copy(
                    draftConfig = updated,
                    savedConfig = updated,
                    feedbackBanner = "Programación cancelada.",
                    isFeedbackError = false,
                )
            }
        }
    }

    fun runNow() {
        viewModelScope.launch {
            val saved = _uiState.value.savedConfig
            val draftToPersist = _uiState.value.draftConfig.copy(
                isScheduleActive = saved.isScheduleActive,
                nextExecutionEpochMillis = saved.nextExecutionEpochMillis,
            )
            repository.saveConfig(draftToPersist)
            _uiState.update {
                it.copy(
                    draftConfig = draftToPersist,
                    savedConfig = draftToPersist,
                    feedbackBanner = "Iniciando ejecución inmediata...",
                    isFeedbackError = false,
                )
            }
            runCatching {
                startServiceAction("Ejecución manual")
            }.onFailure {
                coordinator.startAutomation("Ejecución manual")
            }
        }
    }

    fun stopAutomation() {
        coordinator.stopAutomation("Detenida manualmente por el usuario")
        runCatching { stopServiceAction() }
        setFeedback("Automatización detenida.", false)
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            setFeedback("Registro de actividad limpiado.", false)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            setFeedback("Historial de ejecuciones eliminado.", false)
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(feedbackBanner = null) }
    }

    fun formatNextRun(): String {
        val next = _uiState.value.savedConfig.nextExecutionEpochMillis
        val active = _uiState.value.savedConfig.isScheduleActive
        return if (active) ScheduleCalculator.formatNextExecution(next) else "Sin programación activa"
    }

    private fun setFeedback(message: String, isError: Boolean) {
        _uiState.update { it.copy(feedbackBanner = message, isFeedbackError = isError) }
    }

    class Factory(private val app: ClaudeAutoRunApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                application = app,
                repository = app.repository,
                alarmScheduler = app.alarmScheduler,
                coordinator = app.coordinator,
            ) as T
        }
    }
}
