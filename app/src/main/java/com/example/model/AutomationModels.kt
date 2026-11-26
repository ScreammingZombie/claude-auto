package com.example.model

import java.util.Calendar
import java.util.UUID

enum class AutomationStatus(val label: String) {
    INACTIVA("INACTIVA"),
    PROGRAMADA("PROGRAMADA"),
    CONECTANDO("CONECTANDO"),
    EJECUTANDO("EJECUTANDO"),
    ESPERANDO_LIMITE("ESPERANDO LÍMITE"),
    LIMITE_DETECTADO("LÍMITE DETECTADO"),
    FINALIZANDO("FINALIZANDO"),
    COMPLETADA("COMPLETADA"),
    ERROR("ERROR");

    val isActiveExecution: Boolean
        get() = this == CONECTANDO ||
            this == EJECUTANDO ||
            this == ESPERANDO_LIMITE ||
            this == LIMITE_DETECTADO ||
            this == FINALIZANDO
}

enum class ExecutionStep {
    IDLE,
    CHECKING_WIFI,
    ENABLING_WIFI_OPENING_QS,
    ENABLING_WIFI_TOGGLING,
    WAITING_FOR_INTERNET,
    OPENING_BROWSER,
    WAITING_CLAUDE_LOAD,
    FINDING_CONVERSATION,
    SENDING_RESUME_PROMPT,
    WAITING_FOR_LIMIT,
    FINISHING_HOME,
    DISABLING_WIFI_OPENING_QS,
    DISABLING_WIFI_TOGGLING,
    COMPLETED,
    FAILED,
}

data class DayOption(
    val calendarDay: Int,
    val shortLabel: String,
    val fullName: String,
)

object ScheduleDays {
    val ALL_DAYS: List<DayOption> = listOf(
        DayOption(Calendar.MONDAY, "L", "Lunes"),
        DayOption(Calendar.TUESDAY, "M", "Martes"),
        DayOption(Calendar.WEDNESDAY, "X", "Miércoles"),
        DayOption(Calendar.THURSDAY, "J", "Jueves"),
        DayOption(Calendar.FRIDAY, "V", "Viernes"),
        DayOption(Calendar.SATURDAY, "S", "Sábado"),
        DayOption(Calendar.SUNDAY, "D", "Domingo"),
    )

    val ALL_CALENDAR_DAYS: Set<Int> = ALL_DAYS.map { it.calendarDay }.toSet()
}

object DefaultAutomationText {
    const val DEFAULT_RESUME_PROMPT =
        "Continúa la tarea que quedó pausada exactamente desde donde se interrumpió. Sigue trabajando de manera autónoma y utiliza el tiempo disponible de esta sesión hasta que aparezca el mensaje que indica que alcanzaste el límite de uso o de sesión. No abandones la tarea antes de ese momento. Si necesitas que te proporcione algo para continuar, indícalo claramente."

    val DEFAULT_LIMIT_PHRASES: List<String> = listOf(
        "You've reached your usage limit",
        "You have reached your limit",
        "Usage limit reached",
        "Session limit reached",
        "Try again later",
        "Come back later",
        "Has alcanzado tu límite",
        "Alcanzaste el límite de uso",
        "Límite de sesión alcanzado",
        "Vuelve a intentarlo más tarde",
        "Has llegado al límite",
    )
}

data class AutoRunConfig(
    val hour: Int = 8,
    val minute: Int = 0,
    val selectedDays: Set<Int> = ScheduleDays.ALL_CALENDAR_DAYS,
    val conversationTitle: String = "",
    val resumePrompt: String = DefaultAutomationText.DEFAULT_RESUME_PROMPT,
    val maxDurationHours: Int = 1,
    val maxDurationMinutes: Int = 30,
    val turnOffWifiOnFinish: Boolean = true,
    val openDefaultBrowser: Boolean = true,
    val isScheduleActive: Boolean = false,
    val nextExecutionEpochMillis: Long? = null,
    val limitPhrases: List<String> = DefaultAutomationText.DEFAULT_LIMIT_PHRASES,
) {
    val maxDurationTotalMillis: Long
        get() = ((maxDurationHours * 60L) + maxDurationMinutes).coerceAtLeast(1L) * 60_000L

    val allDaysSelected: Boolean
        get() = selectedDays.containsAll(ScheduleDays.ALL_CALENDAR_DAYS)
}

data class ActivityLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val status: AutomationStatus,
    val message: String,
    val isError: Boolean = false,
)

data class ExecutionHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val initialWifiState: String = "Desconocido",
    val connectionResult: String = "Pendiente",
    val browserOpened: String = "No iniciado",
    val conversationFound: String = "No buscada",
    val instructionSent: String = "No enviada",
    val limitMessageDetected: String = "Ninguno",
    val finishReason: String = "En curso",
    val finalWifiState: String = "Sin cambios",
    val errorsFound: List<String> = emptyList(),
)

data class AutomationRuntimeState(
    val status: AutomationStatus = AutomationStatus.INACTIVA,
    val step: ExecutionStep = ExecutionStep.IDLE,
    val statusDetail: String = "Listo para ejecutar o programar",
    val runStartedAtMillis: Long? = null,
    val maxDurationEndMillis: Long? = null,
    val activeHistoryId: String? = null,
)

data class SystemDiagnosticsState(
    val isAccessibilityEnabled: Boolean = false,
    val canScheduleExactAlarms: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val hasValidatedInternet: Boolean = false,
    val defaultBrowserName: String? = null,
)
