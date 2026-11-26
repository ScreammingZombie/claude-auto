package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SystemDiagnosticsState
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ClaudeCoral
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateOutlineSubtle
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TechCyanContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemScreen(
    diagnostics: SystemDiagnosticsState,
    limitPhrases: List<String>,
    onRefreshDiagnostics: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onAddLimitPhrase: (String) -> Unit,
    onRemoveLimitPhrase: (String) -> Unit,
    onResetDefaultPhrases: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newPhraseText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Sistema, permisos y guía",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Estado local de servicios Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                FilledTonalButton(
                    onClick = onRefreshDiagnostics,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Comprobar")
                }
            }
        }

        // Lista de requisitos del sistema
        item {
            SystemCheckCard(
                icon = Icons.Filled.AccessibilityNew,
                title = "Servicio de Accesibilidad",
                isReady = diagnostics.isAccessibilityEnabled,
                statusText = if (diagnostics.isAccessibilityEnabled) "ACTIVO" else "INACTIVO",
                description = "Necesario para alternar Wi-Fi en Ajustes rápidos, localizar la conversación en el navegador y detectar el límite de Claude.",
                actionLabel = if (diagnostics.isAccessibilityEnabled) "Ajustes" else "Activar ahora",
                onAction = onOpenAccessibilitySettings,
                extraGuidance = "Cómo activarlo manualmente: Ajustes del teléfono > Accesibilidad > Aplicaciones instaladas (o Descargadas) > Claude AutoRun > Activar y confirmar consentimiento.",
                testTag = "accessibility_check_card",
            )
        }

        item {
            SystemCheckCard(
                icon = Icons.Filled.Alarm,
                title = "Alarmas exactas (AlarmManager)",
                isReady = diagnostics.canScheduleExactAlarms,
                statusText = if (diagnostics.canScheduleExactAlarms) "PERMITIDO" else "RESTRINGIDO",
                description = "Permite iniciar exactamente a la hora programada incluso con el teléfono en reposo.",
                actionLabel = "Configurar",
                onAction = onOpenExactAlarmSettings,
                testTag = "exact_alarm_check_card",
            )
        }

        item {
            SystemCheckCard(
                icon = Icons.Filled.Notifications,
                title = "Notificaciones (Foreground Service)",
                isReady = diagnostics.hasNotificationPermission,
                statusText = if (diagnostics.hasNotificationPermission) "PERMITIDAS" else "DENEGADAS",
                description = "Muestra el estado en curso con botón Detener y te avisa cuando Claude alcanza el límite.",
                actionLabel = "Permitir",
                onAction = onRequestNotificationPermission,
                testTag = "notifications_check_card",
            )
        }

        item {
            SystemCheckCard(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Optimización de batería",
                isReady = diagnostics.isIgnoringBatteryOptimizations,
                statusText = if (diagnostics.isIgnoringBatteryOptimizations) "SIN RESTRICCIÓN" else "OPTIMIZADA",
                description = "Evita que el sistema cierre el servicio o retrase la alarma durante sesiones largas.",
                actionLabel = "Ajustes batería",
                onAction = onOpenBatterySettings,
                extraGuidance = "En Xiaomi/MIUI/HyperOS: activa 'Inicio automático' y 'Sin restricciones'. En Samsung One UI: añade la app a 'Aplicaciones nunca suspendidas'. En Oppo/Realme/Huawei: desactiva gestión automática en segundo plano.",
                testTag = "battery_check_card",
            )
        }

        // Conectividad y navegador detectado
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
                border = BorderStroke(1.dp, SlateOutline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = TechCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Conectividad y navegador", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    StatusLine("Wi-Fi actual", if (diagnostics.isWifiEnabled) "Encendido" else "Apagado")
                    StatusLine("Internet validado", if (diagnostics.hasValidatedInternet) "Conectado" else "Sin conexión")
                    StatusLine(
                        "Navegador predeterminado",
                        diagnostics.defaultBrowserName ?: "Selector del sistema / Sin navegador fijo",
                    )
                }
            }
        }

        // Frases configurables para detección de límite
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
                border = BorderStroke(1.dp, SlateOutline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Public, contentDescription = null, tint = ClaudeCoral, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Frases de límite de sesión", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        TextButton(onClick = onResetDefaultPhrases) {
                            Text("Restaurar")
                        }
                    }

                    Text(
                        text = "Solo se disparan avisos reales de límite; el texto de tu instrucción y menciones sueltas de la palabra 'límite' se ignoran.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newPhraseText,
                            onValueChange = { newPhraseText = it },
                            label = { Text("Nueva frase de límite") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_limit_phrase_input"),
                        )
                        FilledTonalButton(
                            onClick = {
                                if (newPhraseText.isNotBlank()) {
                                    onAddLimitPhrase(newPhraseText)
                                    newPhraseText = ""
                                }
                            },
                            modifier = Modifier.heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Añadir frase")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        limitPhrases.forEach { phrase ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveLimitPhrase(phrase) },
                                label = { Text(phrase, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Quitar $phrase",
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = SlateSurface,
                                    labelColor = TextPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Seguridad y privacidad local
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TechCyanContainer.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, TechCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = TechCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Seguridad y funcionamiento local", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Text(
                        text = "• No almacena contraseñas ni automatiza inicios de sesión.\n• Utiliza la sesión que ya tengas abierta en tu navegador predeterminado.\n• No recopila datos ni los envía a servidores externos.\n• Toda configuración, registro e historial se guarda localmente en tu dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemCheckCard(
    icon: ImageVector,
    title: String,
    isReady: Boolean,
    statusText: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    extraGuidance: String? = null,
    testTag: String,
) {
    val accent = if (isReady) EmeraldSuccess else AmberWarning
    val badgeBg = if (isReady) EmeraldContainer else AmberContainer

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
        border = BorderStroke(1.dp, SlateOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isReady) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                        )
                    }
                }
            }

            Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            if (extraGuidance != null) {
                HorizontalDivider(color = SlateOutlineSubtle)
                Text(text = extraGuidance, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            color = TextPrimary,
        )
    }
}
