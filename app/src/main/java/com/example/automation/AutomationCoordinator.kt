package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.AutoRunRepository
import com.example.model.ActivityLogEntry
import com.example.model.AutoRunConfig
import com.example.model.AutomationRuntimeState
import com.example.model.AutomationStatus
import com.example.model.ExecutionHistoryItem
import com.example.model.ExecutionStep
import com.example.util.ConnectivityHelper
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AccessibilityBridge {
    fun isConnected(): Boolean
    fun performGlobalAction(action: Int): Boolean
    fun getWindowRoots(): List<AccessibilityNodeInfo>
}

class AutomationCoordinator(
    private val context: Context,
    private val repository: AutoRunRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stepMutex = Mutex()

    private val _runtimeState = MutableStateFlow(AutomationRuntimeState())
    val runtimeState: StateFlow<AutomationRuntimeState> = _runtimeState.asStateFlow()

    @Volatile
    private var accessibilityBridge: AccessibilityBridge? = null

    @Volatile
    private var activeConfig: AutoRunConfig? = null

    @Volatile
    private var currentHistory: ExecutionHistoryItem? = null

    @Volatile
    private var runJob: Job? = null

    @Volatile
    private var watchdogJob: Job? = null

    @Volatile
    private var onStopServiceCallback: (() -> Unit)? = null

    // Guardas anti-bucle y anti-duplicado
    private var lastActionAtMillis: Long = 0L
    private var wifiToggleAttempts: Int = 0
    private var sidebarOpenAttempts: Int = 0
    private var searchTyped: Boolean = false
    private var sendAttemptCount: Int = 0
    private var promptSentThisRun: Boolean = false
    private var awaitingSendVerification: Boolean = false
    private var currentPhaseEnteredAtMillis: Long = 0L

    fun registerAccessibilityBridge(bridge: AccessibilityBridge) {
        accessibilityBridge = bridge
        scope.launch {
            addLog(
                AutomationStatus.INACTIVA,
                "Servicio de Accesibilidad conectado y listo.",
            )
        }
    }

    fun unregisterAccessibilityBridge() {
        accessibilityBridge = null
        scope.launch {
            if (isActive()) {
                addLog(
                    AutomationStatus.ERROR,
                    "El Servicio de Accesibilidad se desconectó durante la ejecución.",
                    isError = true,
                )
            }
        }
    }

    fun setServiceStopCallback(callback: (() -> Unit)?) {
        onStopServiceCallback = callback
    }

    fun isActive(): Boolean = _runtimeState.value.status.isActiveExecution

    fun markScheduleUpdated(isActive: Boolean, nextMillis: Long?) {
        if (isActive()) return
        _runtimeState.value = AutomationRuntimeState(
            status = if (isActive && nextMillis != null) {
                AutomationStatus.PROGRAMADA
            } else {
                AutomationStatus.INACTIVA
            },
            step = ExecutionStep.IDLE,
            statusDetail = if (isActive && nextMillis != null) {
                "Automatización programada"
            } else {
                "En espera de configuración o ejecución manual"
            },
        )
    }

    fun startAutomation(triggerSource: String) {
        if (isActive()) {
            scope.launch {
                addLog(
                    _runtimeState.value.status,
                    "Solicitud ignorada ($triggerSource): ya hay una automatización en curso.",
                )
            }
            return
        }

        resetRunFlags()
        val now = System.currentTimeMillis()
        runJob = scope.launch {
            val config = repository.getConfig()
            activeConfig = config
            val initialWifi = if (ConnectivityHelper.isWifiEnabled(context)) "Encendido" else "Apagado"
            val history = ExecutionHistoryItem(
                startTimeMillis = now,
                initialWifiState = initialWifi,
            )
            currentHistory = history
            repository.upsertHistoryItem(history)

            val maxEnd = now + config.maxDurationTotalMillis
            updateState(
                status = AutomationStatus.CONECTANDO,
                step = ExecutionStep.CHECKING_WIFI,
                detail = "Iniciando ($triggerSource) · Comprobando Wi-Fi e Internet",
                startedAt = now,
                maxEnd = maxEnd,
                historyId = history.id,
            )
            addLog(
                AutomationStatus.CONECTANDO,
                "Automatización iniciada ($triggerSource). Wi-Fi inicial: $initialWifi.",
            )

            launchMaxDurationWatchdog(config)
            executeConnectivityAndBrowserPhase(config)
        }
    }

    fun stopAutomation(reason: String = "Detenida por el usuario") {
        val wasActive = isActive()
        runJob?.cancel()
        watchdogJob?.cancel()
        runJob = null
        watchdogJob = null

        scope.launch {
            val config = activeConfig ?: repository.getConfig()
            val statusToSet = if (config.isScheduleActive && config.nextExecutionEpochMillis != null) {
                AutomationStatus.PROGRAMADA
            } else {
                AutomationStatus.INACTIVA
            }
            if (wasActive) {
                updateHistory {
                    it.copy(
                        endTimeMillis = System.currentTimeMillis(),
                        finishReason = reason,
                        finalWifiState = if (ConnectivityHelper.isWifiEnabled(context)) "Encendido" else "Apagado",
                    )
                }
                addLog(AutomationStatus.FINALIZANDO, reason)
            }
            _runtimeState.value = AutomationRuntimeState(
                status = statusToSet,
                step = ExecutionStep.IDLE,
                statusDetail = reason,
            )
            onStopServiceCallback?.invoke()
        }
    }

    fun onAccessibilityWindowUpdated() {
        if (!isActive()) return
        val step = _runtimeState.value.step
        if (step == ExecutionStep.WAITING_CLAUDE_LOAD ||
            step == ExecutionStep.FINDING_CONVERSATION ||
            step == ExecutionStep.SENDING_RESUME_PROMPT ||
            step == ExecutionStep.WAITING_FOR_LIMIT
        ) {
            scope.launch {
                stepMutex.withLock {
                    if (isActive()) {
                        processBrowserStepLocked()
                    }
                }
            }
        }
    }

    private fun launchMaxDurationWatchdog(config: AutoRunConfig) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(config.maxDurationTotalMillis)
            if (isActive() && _runtimeState.value.step != ExecutionStep.COMPLETED) {
                addLog(
                    AutomationStatus.FINALIZANDO,
                    "Tiempo máximo configurado alcanzado (${config.maxDurationHours}h ${config.maxDurationMinutes}m).",
                )
                beginFinishing(
                    finishReason = "Tiempo máximo de ejecución alcanzado",
                    terminalStatus = AutomationStatus.COMPLETADA,
                    isError = false,
                )
            }
        }
    }

    private suspend fun executeConnectivityAndBrowserPhase(config: AutoRunConfig) {
        val alreadyHasInternet = ConnectivityHelper.hasValidatedInternet(context)
        if (alreadyHasInternet) {
            updateHistory { it.copy(connectionResult = "Conectado a Internet (previo)") }
            addLog(AutomationStatus.CONECTANDO, "Conexión a Internet ya disponible.")
            proceedToOpenBrowser(config)
            return
        }

        val bridge = accessibilityBridge
        if (bridge == null || !bridge.isConnected()) {
            failSafely(
                errorMessage = "El Servicio de Accesibilidad no está activo. Actívalo en Ajustes > Accesibilidad > Claude AutoRun para controlar Wi-Fi y Claude.",
                connectionResult = "Sin Servicio de Accesibilidad",
            )
            return
        }

        if (!ConnectivityHelper.isWifiEnabled(context)) {
            updateState(
                status = AutomationStatus.CONECTANDO,
                step = ExecutionStep.ENABLING_WIFI_OPENING_QS,
                detail = "Abriendo Ajustes rápidos para activar Wi-Fi",
            )
            addLog(AutomationStatus.CONECTANDO, "Wi-Fi apagado. Abriendo Ajustes rápidos del sistema...")
            bridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            delay(1300)

            updateState(
                status = AutomationStatus.CONECTANDO,
                step = ExecutionStep.ENABLING_WIFI_TOGGLING,
                detail = "Buscando interruptor de Wi-Fi en Ajustes rápidos",
            )

            var wifiConfirmed = false
            val deadline = System.currentTimeMillis() + WIFI_ENABLE_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline && isActive() && wifiToggleAttempts < 4) {
                if (ConnectivityHelper.isWifiEnabled(context)) {
                    wifiConfirmed = true
                    break
                }
                val roots = bridge.getWindowRoots()
                when (val result = QuickSettingsWifiController.trySetWifiState(roots, desiredEnabled = true)) {
                    is WifiToggleAttemptResult.AlreadyInDesiredState -> {
                        addLog(AutomationStatus.CONECTANDO, result.detail)
                        wifiConfirmed = true
                        break
                    }
                    is WifiToggleAttemptResult.ClickedTileOrSwitch -> {
                        wifiToggleAttempts++
                        addLog(AutomationStatus.CONECTANDO, result.detail)
                        delay(2000)
                        if (ConnectivityHelper.isWifiEnabled(context)) {
                            wifiConfirmed = true
                            break
                        }
                    }
                    is WifiToggleAttemptResult.OpenedInternetPanel -> {
                        wifiToggleAttempts++
                        addLog(AutomationStatus.CONECTANDO, result.detail)
                        delay(1500)
                    }
                    is WifiToggleAttemptResult.NotFound -> {
                        wifiToggleAttempts++
                        addLog(AutomationStatus.CONECTANDO, "${result.detail} Reintentando ($wifiToggleAttempts/4)...")
                        delay(1500)
                    }
                }
            }

            // Cerrar panel de Ajustes rápidos
            bridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            delay(700)

            if (!wifiConfirmed && !ConnectivityHelper.isWifiEnabled(context)) {
                failSafely(
                    errorMessage = "No se pudo activar el Wi-Fi desde Ajustes rápidos. Abre Ajustes rápidos manualmente y enciende Wi-Fi.",
                    connectionResult = "Error al activar Wi-Fi",
                )
                return
            }
            addLog(AutomationStatus.CONECTANDO, "Wi-Fi activado correctamente.")
        }

        // Esperar conexión real a Internet
        updateState(
            status = AutomationStatus.CONECTANDO,
            step = ExecutionStep.WAITING_FOR_INTERNET,
            detail = "Esperando conexión real a Internet...",
        )
        addLog(AutomationStatus.CONECTANDO, "Esperando validación de Internet...")

        val internetDeadline = System.currentTimeMillis() + INTERNET_WAIT_TIMEOUT_MS
        var connected = false
        while (System.currentTimeMillis() < internetDeadline && isActive()) {
            if (ConnectivityHelper.hasValidatedInternet(context)) {
                connected = true
                break
            }
            delay(1500)
        }

        if (!connected) {
            failSafely(
                errorMessage = "Tiempo de espera agotado: Wi-Fi activo pero sin conexión a Internet.",
                connectionResult = "Timeout sin Internet",
            )
            return
        }

        updateHistory { it.copy(connectionResult = "Conectado a Internet") }
        addLog(AutomationStatus.CONECTANDO, "Conexión a Internet verificada.")
        proceedToOpenBrowser(config)
    }

    private suspend fun proceedToOpenBrowser(config: AutoRunConfig) {
        val bridge = accessibilityBridge
        if (bridge == null || !bridge.isConnected()) {
            failSafely(
                errorMessage = "El Servicio de Accesibilidad es necesario para interactuar con Claude en el navegador.",
                browserOpened = "Servicio de Accesibilidad inactivo",
            )
            return
        }

        updateState(
            status = AutomationStatus.EJECUTANDO,
            step = ExecutionStep.OPENING_BROWSER,
            detail = if (config.openDefaultBrowser) {
                "Abriendo navegador predeterminado en claude.ai"
            } else {
                "Esperando ventana de Claude en el navegador"
            },
        )

        if (config.openDefaultBrowser) {
            val browserLabel = ConnectivityHelper.resolveDefaultBrowserLabel(context) ?: "Navegador predeterminado"
            val launched = runCatching {
                context.startActivity(ConnectivityHelper.buildOpenClaudeIntent(context))
            }.isSuccess

            if (!launched) {
                failSafely(
                    errorMessage = "No se pudo abrir ningún navegador para https://claude.ai.",
                    browserOpened = "Error al abrir navegador",
                )
                return
            }
            updateHistory { it.copy(browserOpened = "Sí ($browserLabel)") }
            addLog(AutomationStatus.EJECUTANDO, "Navegador abierto ($browserLabel) en ${ConnectivityHelper.CLAUDE_URL}.")
        } else {
            updateHistory { it.copy(browserOpened = "Desactivado por usuario") }
            addLog(AutomationStatus.EJECUTANDO, "Opción 'Abrir navegador' desactivada; esperando ventana de Claude.")
        }

        currentPhaseEnteredAtMillis = System.currentTimeMillis()
        updateState(
            status = AutomationStatus.EJECUTANDO,
            step = ExecutionStep.WAITING_CLAUDE_LOAD,
            detail = "Esperando carga de Claude AI...",
        )

        // Bucle de respaldo por si el navegador no emite eventos frecuentes de contenido
        while (isActive() &&
            _runtimeState.value.step in listOf(
                ExecutionStep.WAITING_CLAUDE_LOAD,
                ExecutionStep.FINDING_CONVERSATION,
                ExecutionStep.SENDING_RESUME_PROMPT,
                ExecutionStep.WAITING_FOR_LIMIT,
            )
        ) {
            delay(1800)
            if (!isActive()) break
            stepMutex.withLock {
                if (isActive()) {
                    processBrowserStepLocked()
                }
            }
        }
    }

    private suspend fun processBrowserStepLocked() {
        val bridge = accessibilityBridge ?: return
        if (!bridge.isConnected()) return
        val config = activeConfig ?: return
        val roots = bridge.getWindowRoots()
        if (roots.isEmpty()) return

        val now = System.currentTimeMillis()
        val currentStep = _runtimeState.value.step

        // Siempre inspeccionar textos visibles primero para seguridad/CAPTCHA y límite
        val visibleTexts = ClaudePageAutomator.extractVisibleTexts(roots)
        val hasComposer = ClaudePageAutomator.findMessageComposer(roots) != null

        // 1. Comprobar sesión cerrada / CAPTCHA antes de interactuar
        if (currentStep != ExecutionStep.WAITING_FOR_LIMIT) {
            val authResult = AuthAndCaptchaDetector.evaluate(visibleTexts, hasComposer)
            if (authResult is AuthAndCaptchaResult.InterventionRequired) {
                failSafely(
                    errorMessage = authResult.reason,
                    finishReason = "Requiere intervención manual (sesión o CAPTCHA)",
                )
                return
            }
        }

        // 2. Comprobar detección de límite en cualquier momento una vez enviada la instrucción (o esperando límite)
        if (promptSentThisRun || currentStep == ExecutionStep.WAITING_FOR_LIMIT) {
            val limitResult = LimitDetector.detect(
                visibleTexts = visibleTexts,
                configuredPhrases = config.limitPhrases,
                sentResumePrompt = config.resumePrompt,
            )
            if (limitResult.detected) {
                val phrase = limitResult.matchedPhrase ?: "Límite de sesión detectado"
                addLog(
                    AutomationStatus.LIMITE_DETECTADO,
                    "Límite detectado en pantalla: \"$phrase\" (${limitResult.rawSnippet.orEmpty()})",
                )
                updateHistory {
                    it.copy(
                        limitMessageDetected = phrase,
                    )
                }
                updateState(
                    status = AutomationStatus.LIMITE_DETECTADO,
                    step = ExecutionStep.FINISHING_HOME,
                    detail = "Límite detectado: $phrase",
                )
                beginFinishing(
                    finishReason = "Límite de sesión detectado ($phrase)",
                    terminalStatus = AutomationStatus.LIMITE_DETECTADO,
                    isError = false,
                )
                return
            }
        }

        // Respetar enfriamiento entre clics para evitar bucles
        if (now - lastActionAtMillis < ACTION_COOLDOWN_MS) {
            return
        }

        when (currentStep) {
            ExecutionStep.WAITING_CLAUDE_LOAD -> {
                if (!ClaudePageAutomator.isLikelyBrowserOrClaudeWindow(roots)) {
                    if (now - currentPhaseEnteredAtMillis > BROWSER_LOAD_TIMEOUT_MS) {
                        failSafely(
                            errorMessage = "No se detectó la ventana de Claude AI tras esperar 30 segundos.",
                            finishReason = "Timeout esperando navegador/Claude",
                        )
                    }
                    return
                }

                if (config.conversationTitle.isBlank()) {
                    if (hasComposer) {
                        updateHistory { it.copy(conversationFound = "Conversación abierta actual") }
                        addLog(AutomationStatus.EJECUTANDO, "Sin título configurado: continuando en conversación abierta.")
                        currentPhaseEnteredAtMillis = now
                        updateState(
                            status = AutomationStatus.EJECUTANDO,
                            step = ExecutionStep.SENDING_RESUME_PROMPT,
                            detail = "Localizando campo de entrada de Claude...",
                        )
                    } else if (now - currentPhaseEnteredAtMillis > BROWSER_LOAD_TIMEOUT_MS) {
                        failSafely(
                            errorMessage = "Claude cargó, pero no se encontró el cuadro de mensaje activo.",
                            finishReason = "Cuadro de mensaje no encontrado",
                        )
                    }
                } else {
                    currentPhaseEnteredAtMillis = now
                    updateState(
                        status = AutomationStatus.EJECUTANDO,
                        step = ExecutionStep.FINDING_CONVERSATION,
                        detail = "Buscando conversación: '${config.conversationTitle}'",
                    )
                }
            }

            ExecutionStep.FINDING_CONVERSATION -> {
                val targetTitle = config.conversationTitle.trim()
                if (ClaudePageAutomator.isCurrentConversationMatching(roots, targetTitle) && hasComposer) {
                    updateHistory { it.copy(conversationFound = "Ya abierta: $targetTitle") }
                    addLog(AutomationStatus.EJECUTANDO, "Conversación '$targetTitle' ya está abierta.")
                    currentPhaseEnteredAtMillis = now
                    updateState(
                        status = AutomationStatus.EJECUTANDO,
                        step = ExecutionStep.SENDING_RESUME_PROMPT,
                        detail = "Preparando envío de instrucción...",
                    )
                    return
                }

                when (val match = ClaudePageAutomator.findConversationInTree(roots, targetTitle)) {
                    is ConversationMatchResult.SingleMatch -> {
                        lastActionAtMillis = now
                        val clicked = ClaudePageAutomator.clickNodeOrParent(match.candidate.payload)
                        if (clicked) {
                            updateHistory {
                                it.copy(conversationFound = "${match.matchType}: ${match.candidate.title}")
                            }
                            addLog(
                                AutomationStatus.EJECUTANDO,
                                "Conversación abierta (${match.matchType}): '${match.candidate.title}'.",
                            )
                            currentPhaseEnteredAtMillis = now
                            updateState(
                                status = AutomationStatus.EJECUTANDO,
                                step = ExecutionStep.SENDING_RESUME_PROMPT,
                                detail = "Esperando campo de entrada en '${match.candidate.title}'",
                            )
                        }
                    }

                    is ConversationMatchResult.AmbiguousMatch -> {
                        val names = match.candidates.take(4).joinToString(", ") { "'${it.title}'" }
                        failSafely(
                            errorMessage = "Coincidencias ambiguas para '$targetTitle': $names. Especifica un título más preciso o abre el chat manualmente.",
                            conversationFound = "Ambigua: $names",
                            finishReason = "Detenida por coincidencia ambigua",
                        )
                    }

                    ConversationMatchResult.NoMatch -> {
                        if (!searchTyped && ClaudePageAutomator.tryTypeInChatSearch(roots, targetTitle)) {
                            searchTyped = true
                            lastActionAtMillis = now
                            addLog(AutomationStatus.EJECUTANDO, "Texto '$targetTitle' escrito en búsqueda de chats.")
                        } else if (sidebarOpenAttempts < 3 && ClaudePageAutomator.tryOpenSidebarOrSearch(roots)) {
                            sidebarOpenAttempts++
                            lastActionAtMillis = now
                            addLog(AutomationStatus.EJECUTANDO, "Abriendo lista/búsqueda de chats ($sidebarOpenAttempts/3)...")
                        } else if (now - currentPhaseEnteredAtMillis > CONVERSATION_SEARCH_TIMEOUT_MS) {
                            failSafely(
                                errorMessage = "No se encontró ninguna conversación coincidente con '$targetTitle'.",
                                conversationFound = "No encontrada: '$targetTitle'",
                                finishReason = "Conversación no encontrada",
                            )
                        }
                    }
                }
            }

            ExecutionStep.SENDING_RESUME_PROMPT -> {
                if (promptSentThisRun) {
                    updateState(
                        status = AutomationStatus.ESPERANDO_LIMITE,
                        step = ExecutionStep.WAITING_FOR_LIMIT,
                        detail = "Monitoreando ventana hasta detectar límite de sesión...",
                    )
                    return
                }

                if (awaitingSendVerification) {
                    if (ClaudePageAutomator.verifyPromptDispatched(roots, config.resumePrompt)) {
                        promptSentThisRun = true
                        awaitingSendVerification = false
                        updateHistory { it.copy(instructionSent = "Enviada y verificada") }
                        addLog(
                            AutomationStatus.ESPERANDO_LIMITE,
                            "Instrucción enviada y verificada. Esperando límite de sesión...",
                        )
                        updateState(
                            status = AutomationStatus.ESPERANDO_LIMITE,
                            step = ExecutionStep.WAITING_FOR_LIMIT,
                            detail = "Esperando respuesta de Claude hasta límite de sesión",
                        )
                    } else if (now - lastActionAtMillis > 5000L && sendAttemptCount >= 2) {
                        failSafely(
                            errorMessage = "No se pudo confirmar el envío de la instrucción en Claude.",
                            instructionSent = "No confirmada tras enviar",
                        )
                    }
                    return
                }

                if (sendAttemptCount >= 2) {
                    failSafely(
                        errorMessage = "No se pudo enviar la instrucción tras $sendAttemptCount intentos.",
                        instructionSent = "Error al enviar",
                    )
                    return
                }

                when (ClaudePageAutomator.inputAndSendPrompt(roots, config.resumePrompt)) {
                    PromptSendAttempt.SentClicked -> {
                        sendAttemptCount++
                        lastActionAtMillis = now
                        awaitingSendVerification = true
                        addLog(
                            AutomationStatus.EJECUTANDO,
                            "Instrucción introducida y botón Enviar pulsado. Verificando envío...",
                        )
                    }

                    PromptSendAttempt.ComposerNotFound -> {
                        if (now - currentPhaseEnteredAtMillis > BROWSER_LOAD_TIMEOUT_MS) {
                            failSafely(
                                errorMessage = "No se localizó el campo de entrada de texto en Claude.",
                                instructionSent = "Campo de texto no encontrado",
                            )
                        }
                    }

                    PromptSendAttempt.TextInjectionFailed -> {
                        sendAttemptCount++
                        lastActionAtMillis = now
                        addLog(AutomationStatus.EJECUTANDO, "Reintentando insertar texto en el campo de Claude...")
                    }

                    PromptSendAttempt.SendButtonNotFound, PromptSendAttempt.SendClickFailed -> {
                        sendAttemptCount++
                        lastActionAtMillis = now
                        addLog(AutomationStatus.EJECUTANDO, "Buscando botón de enviar en Claude ($sendAttemptCount/2)...")
                    }
                }
            }

            ExecutionStep.WAITING_FOR_LIMIT -> {
                // La detección real de límite se ejecuta arriba en cada evento y tick del bucle.
            }

            else -> Unit
        }
    }

    private fun beginFinishing(
        finishReason: String,
        terminalStatus: AutomationStatus,
        isError: Boolean,
    ) {
        if (_runtimeState.value.step == ExecutionStep.COMPLETED ||
            _runtimeState.value.step == ExecutionStep.FAILED
        ) {
            return
        }

        watchdogJob?.cancel()
        watchdogJob = null

        scope.launch {
            val config = activeConfig ?: repository.getConfig()
            val bridge = accessibilityBridge

            updateState(
                status = AutomationStatus.FINALIZANDO,
                step = ExecutionStep.FINISHING_HOME,
                detail = "Cerrando navegador y finalizando...",
            )
            addLog(AutomationStatus.FINALIZANDO, "Iniciando cierre seguro: $finishReason")

            // 1. Salir del navegador mediante HOME
            if (bridge != null && bridge.isConnected()) {
                bridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                delay(900)
            }

            // 2. Apagar Wi-Fi desde Ajustes rápidos si está habilitado
            var finalWifiDesc = if (ConnectivityHelper.isWifiEnabled(context)) "Encendido" else "Apagado"
            if (config.turnOffWifiOnFinish && ConnectivityHelper.isWifiEnabled(context)) {
                if (bridge != null && bridge.isConnected()) {
                    addLog(AutomationStatus.FINALIZANDO, "Abriendo Ajustes rápidos para apagar Wi-Fi...")
                    bridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    delay(1300)

                    var offConfirmed = false
                    var attempts = 0
                    val deadline = System.currentTimeMillis() + 14_000L
                    while (System.currentTimeMillis() < deadline && attempts < 4) {
                        if (!ConnectivityHelper.isWifiEnabled(context)) {
                            offConfirmed = true
                            break
                        }
                        val roots = bridge.getWindowRoots()
                        when (val res = QuickSettingsWifiController.trySetWifiState(roots, desiredEnabled = false)) {
                            is WifiToggleAttemptResult.AlreadyInDesiredState -> {
                                offConfirmed = true
                                break
                            }
                            is WifiToggleAttemptResult.ClickedTileOrSwitch -> {
                                attempts++
                                addLog(AutomationStatus.FINALIZANDO, res.detail)
                                delay(1800)
                                if (!ConnectivityHelper.isWifiEnabled(context)) {
                                    offConfirmed = true
                                    break
                                }
                            }
                            is WifiToggleAttemptResult.OpenedInternetPanel -> {
                                attempts++
                                delay(1400)
                            }
                            is WifiToggleAttemptResult.NotFound -> {
                                attempts++
                                delay(1200)
                            }
                        }
                    }

                    bridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    delay(500)
                    finalWifiDesc = if (offConfirmed || !ConnectivityHelper.isWifiEnabled(context)) {
                        "Apagado"
                    } else {
                        "No verificado (sigue encendido)"
                    }
                    addLog(AutomationStatus.FINALIZANDO, "Estado final de Wi-Fi: $finalWifiDesc")
                } else {
                    finalWifiDesc = "Encendido (sin Accesibilidad para apagar)"
                }
            } else if (!config.turnOffWifiOnFinish) {
                finalWifiDesc = "$finalWifiDesc (conservado por configuración)"
            }

            updateHistory {
                it.copy(
                    endTimeMillis = System.currentTimeMillis(),
                    finishReason = finishReason,
                    finalWifiState = finalWifiDesc,
                )
            }

            _runtimeState.value = AutomationRuntimeState(
                status = terminalStatus,
                step = if (isError) ExecutionStep.FAILED else ExecutionStep.COMPLETED,
                statusDetail = finishReason,
            )

            NotificationHelper.showResultNotification(
                context = context,
                title = "Claude AutoRun · ${terminalStatus.label}",
                message = "$finishReason · Wi-Fi: $finalWifiDesc",
                isError = isError,
            )

            addLog(terminalStatus, "Proceso terminado: $finishReason")
            runJob?.cancel()
            runJob = null
            onStopServiceCallback?.invoke()
        }
    }

    private suspend fun failSafely(
        errorMessage: String,
        connectionResult: String? = null,
        browserOpened: String? = null,
        conversationFound: String? = null,
        instructionSent: String? = null,
        finishReason: String = "Error: $errorMessage",
    ) {
        updateHistory { current ->
            current.copy(
                connectionResult = connectionResult ?: current.connectionResult,
                browserOpened = browserOpened ?: current.browserOpened,
                conversationFound = conversationFound ?: current.conversationFound,
                instructionSent = instructionSent ?: current.instructionSent,
                errorsFound = current.errorsFound + errorMessage,
            )
        }
        addLog(AutomationStatus.ERROR, errorMessage, isError = true)
        beginFinishing(
            finishReason = finishReason,
            terminalStatus = AutomationStatus.ERROR,
            isError = true,
        )
    }

    private suspend fun addLog(
        status: AutomationStatus,
        message: String,
        isError: Boolean = false,
    ) {
        repository.appendLog(
            ActivityLogEntry(
                status = status,
                message = message,
                isError = isError,
            ),
        )
    }

    private suspend fun updateHistory(transform: (ExecutionHistoryItem) -> ExecutionHistoryItem) {
        val current = currentHistory ?: return
        val updated = transform(current)
        currentHistory = updated
        repository.upsertHistoryItem(updated)
    }

    private fun updateState(
        status: AutomationStatus,
        step: ExecutionStep,
        detail: String,
        startedAt: Long? = _runtimeState.value.runStartedAtMillis,
        maxEnd: Long? = _runtimeState.value.maxDurationEndMillis,
        historyId: String? = _runtimeState.value.activeHistoryId,
    ) {
        _runtimeState.value = AutomationRuntimeState(
            status = status,
            step = step,
            statusDetail = detail,
            runStartedAtMillis = startedAt,
            maxDurationEndMillis = maxEnd,
            activeHistoryId = historyId,
        )
    }

    private fun resetRunFlags() {
        lastActionAtMillis = 0L
        wifiToggleAttempts = 0
        sidebarOpenAttempts = 0
        searchTyped = false
        sendAttemptCount = 0
        promptSentThisRun = false
        awaitingSendVerification = false
        currentPhaseEnteredAtMillis = System.currentTimeMillis()
    }

    companion object {
        private const val WIFI_ENABLE_TIMEOUT_MS = 22_000L
        private const val INTERNET_WAIT_TIMEOUT_MS = 30_000L
        private const val BROWSER_LOAD_TIMEOUT_MS = 30_000L
        private const val CONVERSATION_SEARCH_TIMEOUT_MS = 25_000L
        private const val ACTION_COOLDOWN_MS = 1_800L
    }
}
