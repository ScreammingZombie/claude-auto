package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActivityLogEntry
import com.example.model.AutomationStatus
import com.example.model.ScheduleDays
import com.example.scheduler.ScheduleCalculator
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ClaudeCoral
import com.example.ui.theme.ClaudeCoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateOutlineSubtle
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.SlateSurfaceHigh
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TechCyanContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(
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
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val config = uiState.draftConfig
    val status = uiState.effectiveStatus
    val isActiveRun = uiState.runtimeState.status.isActiveExecution
    val nextExecutionText = if (uiState.savedConfig.isScheduleActive) {
        ScheduleCalculator.formatNextExecution(uiState.savedConfig.nextExecutionEpochMillis)
    } else {
        "Sin programación activa"
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = config.hour,
            initialMinute = config.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            containerColor = SlateSurfaceElevated,
            title = {
                Text(
                    "Seleccionar hora de inicio",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateTime(timePickerState.hour, timePickerState.minute)
                        showTimePickerDialog = false
                    },
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    Column(modifier = modifier) {
        AnimatedVisibility(visible = uiState.feedbackBanner != null) {
            uiState.feedbackBanner?.let { banner ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FeedbackBannerCard(
                        message = banner,
                        isError = uiState.isFeedbackError,
                        onDismiss = onDismissBanner,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("control_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

        if (!uiState.diagnostics.isAccessibilityEnabled || !uiState.diagnostics.canScheduleExactAlarms) {
            item(key = "alerts") {
                ReadinessAlertCard(
                    accessibilityEnabled = uiState.diagnostics.isAccessibilityEnabled,
                    exactAlarmsAllowed = uiState.diagnostics.canScheduleExactAlarms,
                    onOpenAccessibility = onOpenAccessibilitySettings,
                    onOpenExactAlarm = onOpenExactAlarmSettings,
                )
            }
        }

        // 2. Tarjeta de Estado y Próxima Ejecución
        item(key = "status") {
            StatusHeroCard(
                status = status,
                detail = uiState.runtimeState.statusDetail,
                nextExecutionText = nextExecutionText,
                isActiveRun = isActiveRun,
                maxDurationEndMillis = uiState.runtimeState.maxDurationEndMillis,
            )
        }

        // 3. Panel de Acciones Principales
        item(key = "actions") {
            ActionControlPanel(
                isActiveRun = isActiveRun,
                isScheduleActive = uiState.savedConfig.isScheduleActive,
                onSaveSchedule = onSaveSchedule,
                onRunNow = onRunNow,
                onStop = onStop,
                onCancelSchedule = onCancelSchedule,
            )
        }

        // 4. Programación: Hora y Días de la semana
        item(key = "schedule") {
            SectionCard(
                title = "Programación horaria",
                icon = Icons.Filled.Schedule,
                accent = TechCyan,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Hora de ejecución",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", config.hour, config.minute),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                            ),
                            color = TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showTimePickerDialog = true }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .testTag("time_display"),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = { showTimePickerDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pick_time_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                contentDescription = "Elegir hora",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cambiar")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SlateOutlineSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Días de la semana",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                    )
                    FilterChip(
                        selected = config.allDaysSelected,
                        onClick = onToggleAllDays,
                        label = { Text("Todos los días") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TechCyanContainer,
                            selectedLabelColor = TechCyan,
                        ),
                        modifier = Modifier.testTag("all_days_chip"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScheduleDays.ALL_DAYS.forEach { day ->
                        val selected = config.selectedDays.contains(day.calendarDay)
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleDay(day.calendarDay) },
                            label = {
                                Text(
                                    text = day.shortLabel,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ClaudeCoralContainer,
                                selectedLabelColor = ClaudeCoral,
                            ),
                            modifier = Modifier.testTag("day_chip_${day.calendarDay}"),
                        )
                    }
                }
            }
        }

        // 5. Conversación e Instrucción de Reanudación
        item(key = "conversation") {
            SectionCard(
                title = "Claude AI · Conversación e instrucción",
                icon = Icons.AutoMirrored.Filled.Chat,
                accent = ClaudeCoral,
            ) {
                OutlinedTextField(
                    value = config.conversationTitle,
                    onValueChange = onUpdateConversationTitle,
                    label = { Text("Título (o parte) de la conversación") },
                    placeholder = { Text("Vacío = continuar chat abierto actual") },
                    singleLine = true,
                    colors = darkFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conversation_title_input"),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Instrucción de reanudación",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                    )
                    TextButton(
                        onClick = onResetDefaultPrompt,
                        modifier = Modifier.testTag("reset_prompt_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Restaurar instrucción predeterminada",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Predeterminada")
                    }
                }

                OutlinedTextField(
                    value = config.resumePrompt,
                    onValueChange = onUpdateResumePrompt,
                    minLines = 4,
                    maxLines = 8,
                    colors = darkFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resume_prompt_input"),
                )
            }
        }

        // 6. Tiempo máximo y opciones de red/navegador
        item {
            SectionCard(
                title = "Límites y conectividad",
                icon = Icons.Filled.Timer,
                accent = AmberWarning,
            ) {
                Text(
                    text = "Tiempo máximo de ejecución (seguridad)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DurationStepper(
                        label = "Horas",
                        value = config.maxDurationHours,
                        onDecrement = {
                            onUpdateMaxDuration(
                                (config.maxDurationHours - 1).coerceAtLeast(0),
                                config.maxDurationMinutes,
                            )
                        },
                        onIncrement = {
                            onUpdateMaxDuration(
                                (config.maxDurationHours + 1).coerceAtMost(12),
                                config.maxDurationMinutes,
                            )
                        },
                        minusTag = "max_hours_minus",
                        plusTag = "max_hours_plus",
                        valueTag = "max_hours_value",
                        modifier = Modifier.weight(1f),
                    )
                    DurationStepper(
                        label = "Minutos",
                        value = config.maxDurationMinutes,
                        onDecrement = {
                            val next = if (config.maxDurationMinutes >= 5) {
                                config.maxDurationMinutes - 5
                            } else {
                                0
                            }
                            onUpdateMaxDuration(config.maxDurationHours, next)
                        },
                        onIncrement = {
                            val next = (config.maxDurationMinutes + 5).coerceAtMost(55)
                            onUpdateMaxDuration(config.maxDurationHours, next)
                        },
                        minusTag = "max_minutes_minus",
                        plusTag = "max_minutes_plus",
                        valueTag = "max_minutes_value",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SlateOutlineSubtle)
                Spacer(modifier = Modifier.height(8.dp))

                ToggleSettingRow(
                    icon = Icons.Filled.WifiOff,
                    title = "Apagar Wi-Fi al finalizar",
                    subtitle = "Usa Ajustes rápidos al detectar límite o timeout",
                    checked = config.turnOffWifiOnFinish,
                    onCheckedChange = onUpdateTurnOffWifi,
                    testTag = "turn_off_wifi_switch",
                )

                ToggleSettingRow(
                    icon = Icons.Filled.Language,
                    title = "Abrir navegador predeterminado",
                    subtitle = "Navega a https://claude.ai con tu sesión iniciada",
                    checked = config.openDefaultBrowser,
                    onCheckedChange = onUpdateOpenBrowser,
                    testTag = "open_browser_switch",
                )
            }
        }

        // 7. Registro de actividad en vivo
        item {
            ActivityLogCard(
                logs = uiState.logs,
                onClearLogs = onClearLogs,
            )
        }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun StatusHeroCard(
    status: AutomationStatus,
    detail: String,
    nextExecutionText: String,
    isActiveRun: Boolean,
    maxDurationEndMillis: Long?,
) {
    val (accentColor, containerColor) = statusColors(status)
    val pulseTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(containerColor.copy(alpha = 0.55f), SlateSurface),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(if (isActiveRun) pulseAlpha else 1f)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ESTADO ACTUAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = containerColor,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                        ),
                        color = accentColor,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("status_badge"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.testTag("status_detail_text"),
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SlateOutlineSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = TechCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Próxima ejecución:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = nextExecutionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = TechCyan,
                    modifier = Modifier.testTag("next_execution_text"),
                )
            }

            if (isActiveRun && maxDurationEndMillis != null) {
                val limitStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(maxDurationEndMillis))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Corte automático de seguridad a las $limitStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning,
                )
            }
        }
    }
}

@Composable
private fun ActionControlPanel(
    isActiveRun: Boolean,
    isScheduleActive: Boolean,
    onSaveSchedule: () -> Unit,
    onRunNow: () -> Unit,
    onStop: () -> Unit,
    onCancelSchedule: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onSaveSchedule,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TechCyan,
                    contentColor = Color(0xFF06131D),
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("save_schedule_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GUARDAR PROGRAMACIÓN",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onRunNow,
                enabled = !isActiveRun,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClaudeCoral,
                    contentColor = Color(0xFF1A0D07),
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("run_now_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EJECUTAR AHORA",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActiveRun) CoralError else CoralErrorContainer,
                    contentColor = if (isActiveRun) Color(0xFF1F080A) else CoralError,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("stop_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DETENER",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            OutlinedButton(
                onClick = onCancelSchedule,
                enabled = isScheduleActive,
                border = BorderStroke(1.dp, if (isScheduleActive) SlateOutline else SlateOutlineSubtle),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("cancel_schedule_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CANCELAR PROGRAMACIÓN",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReadinessAlertCard(
    accessibilityEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenExactAlarm: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AmberContainer.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Permisos recomendados pendientes",
                    style = MaterialTheme.typography.titleSmall,
                    color = AmberWarning,
                )
            }
            if (!accessibilityEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Servicio de Accesibilidad inactivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenAccessibility) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Activar")
                    }
                }
            }
            if (!exactAlarmsAllowed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Alarmas exactas desactivadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenExactAlarm) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Permitir")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackBannerCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    val bg = if (isError) CoralErrorContainer else EmeraldContainer
    val fg = if (isError) CoralError else EmeraldSuccess
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feedback_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("OK", color = fg)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
        border = BorderStroke(1.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DurationStepper(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    minusTag: String,
    plusTag: String,
    valueTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SlateSurface,
        border = BorderStroke(1.dp, SlateOutlineSubtle),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SlateSurfaceHigh)
                        .testTag(minusTag),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Restar $label", tint = TextPrimary)
                }
                Text(
                    text = String.format(Locale.US, "%02d", value),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = TextPrimary,
                    modifier = Modifier.testTag(valueTag),
                )
                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SlateSurfaceHigh)
                        .testTag(plusTag),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Sumar $label", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SlateSurface,
                checkedTrackColor = ClaudeCoral,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun ActivityLogCard(
    logs: List<ActivityLogEntry>,
    onClearLogs: () -> Unit,
) {
    SectionCard(
        title = "Registro de actividad",
        icon = Icons.Filled.Terminal,
        accent = EmeraldSuccess,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${logs.size} eventos recientes",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            if (logs.isNotEmpty()) {
                TextButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button"),
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Limpiar registro")
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (logs.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SlateSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sin eventos aún. Guarda una programación o pulsa EJECUTAR AHORA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
        } else {
            val timeFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SlateSurface,
                border = BorderStroke(1.dp, SlateOutlineSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activity_log_list"),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    logs.take(30).forEach { entry ->
                        val color = if (entry.isError) CoralError else statusColors(entry.status).first
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = timeFormat.format(Date(entry.timestampMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                                Text(
                                    text = "[${entry.status.label}]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = color,
                                )
                            }
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = if (entry.isError) CoralError else TextPrimary,
                            )
                        }
                        HorizontalDivider(color = SlateOutlineSubtle.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ClaudeCoral,
    unfocusedBorderColor = SlateOutline,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = ClaudeCoral,
    unfocusedLabelColor = TextSecondary,
    cursorColor = ClaudeCoral,
)

fun statusColors(status: AutomationStatus): Pair<Color, Color> {
    return when (status) {
        AutomationStatus.INACTIVA -> TextSecondary to SlateSurfaceHigh
        AutomationStatus.PROGRAMADA -> TechCyan to TechCyanContainer
        AutomationStatus.CONECTANDO -> AmberWarning to AmberContainer
        AutomationStatus.EJECUTANDO -> ClaudeCoral to ClaudeCoralContainer
        AutomationStatus.ESPERANDO_LIMITE -> TechCyan to TechCyanContainer
        AutomationStatus.LIMITE_DETECTADO -> EmeraldSuccess to EmeraldContainer
        AutomationStatus.FINALIZANDO -> AmberWarning to AmberContainer
        AutomationStatus.COMPLETADA -> EmeraldSuccess to EmeraldContainer
        AutomationStatus.ERROR -> CoralError to CoralErrorContainer
    }
}
