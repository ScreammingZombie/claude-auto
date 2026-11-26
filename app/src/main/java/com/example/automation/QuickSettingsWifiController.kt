package com.example.automation

import android.view.accessibility.AccessibilityNodeInfo

sealed class WifiToggleAttemptResult {
    data class AlreadyInDesiredState(val detail: String) : WifiToggleAttemptResult()
    data class ClickedTileOrSwitch(val detail: String) : WifiToggleAttemptResult()
    data class OpenedInternetPanel(val detail: String) : WifiToggleAttemptResult()
    data class NotFound(val detail: String) : WifiToggleAttemptResult()
}

object QuickSettingsWifiController {

    private val WIFI_LABELS = listOf("wi-fi", "wifi", "wlan")
    private val INTERNET_LABELS = listOf("internet", "red e internet", "conexiones")

    private val ON_STATES = listOf("activado", "activada", "conectado", "on", "encendido", "connected")
    private val OFF_STATES = listOf("desactivado", "desactivada", "apagado", "off", "disconnected", "sin internet")

    fun trySetWifiState(
        roots: List<AccessibilityNodeInfo>,
        desiredEnabled: Boolean,
    ): WifiToggleAttemptResult {
        val allNodes = collectNodes(roots)
        if (allNodes.isEmpty()) {
            return WifiToggleAttemptResult.NotFound("No hay nodos visibles en Ajustes rápidos.")
        }

        // 1. Priorizar interruptores (Switch / ToggleButton / CheckBox) etiquetados con Wi-Fi
        val wifiSwitches = allNodes.filter { node ->
            val label = nodeLabel(node)
            val isSwitchLike = node.isCheckable ||
                node.className?.toString()?.contains("Switch", ignoreCase = true) == true ||
                node.className?.toString()?.contains("Toggle", ignoreCase = true) == true
            isSwitchLike && WIFI_LABELS.any { label.contains(it) }
        }

        for (sw in wifiSwitches) {
            val currentState = inferNodeCheckedState(sw)
            if (currentState == desiredEnabled) {
                return WifiToggleAttemptResult.AlreadyInDesiredState(
                    "Interruptor Wi-Fi ya está ${if (desiredEnabled) "activado" else "desactivado"}.",
                )
            }
            if (performClickWithParentFallback(sw)) {
                return WifiToggleAttemptResult.ClickedTileOrSwitch(
                    "Interruptor Wi-Fi pulsado para ${if (desiredEnabled) "activar" else "desactivar"}.",
                )
            }
        }

        // 2. Buscar mosaico / fila de Wi-Fi directo en Ajustes rápidos (Samsung, Xiaomi, Motorola, AOSP antiguo)
        val wifiTiles = allNodes.filter { node ->
            val label = nodeLabel(node)
            WIFI_LABELS.any { label.contains(it) } && (node.isClickable || findClickableAncestor(node) != null)
        }

        for (tile in wifiTiles) {
            val currentState = inferNodeCheckedState(tile)
            if (currentState != null && currentState == desiredEnabled) {
                return WifiToggleAttemptResult.AlreadyInDesiredState(
                    "Mosaico Wi-Fi ya indica estado ${if (desiredEnabled) "activo" else "inactivo"}.",
                )
            }
            if (performClickWithParentFallback(tile)) {
                return WifiToggleAttemptResult.ClickedTileOrSwitch(
                    "Mosaico Wi-Fi pulsado en Ajustes rápidos.",
                )
            }
        }

        // 3. Buscar un Switch Wi-Fi junto a un nodo hermano que diga "Wi-Fi" (ej. panel de Internet en Android 12+)
        val rowSwitch = findSwitchNearWifiLabel(allNodes)
        if (rowSwitch != null) {
            val currentState = inferNodeCheckedState(rowSwitch)
            if (currentState == desiredEnabled) {
                return WifiToggleAttemptResult.AlreadyInDesiredState(
                    "Switch del panel Internet ya está ${if (desiredEnabled) "activado" else "desactivado"}.",
                )
            }
            if (performClickWithParentFallback(rowSwitch)) {
                return WifiToggleAttemptResult.ClickedTileOrSwitch(
                    "Switch Wi-Fi del panel Internet pulsado.",
                )
            }
        }

        // 4. En Android 12+ (Pixel/AOSP), el mosaico principal se llama "Internet" y abre el panel con el switch Wi-Fi
        val internetTiles = allNodes.filter { node ->
            val label = nodeLabel(node)
            INTERNET_LABELS.any { label.contains(it) } && (node.isClickable || findClickableAncestor(node) != null)
        }
        for (internetTile in internetTiles) {
            if (performClickWithParentFallback(internetTile)) {
                return WifiToggleAttemptResult.OpenedInternetPanel(
                    "Mosaico 'Internet' pulsado para abrir el subpanel de Wi-Fi.",
                )
            }
        }

        return WifiToggleAttemptResult.NotFound(
            "No se localizó el control de Wi-Fi ni el mosaico Internet en Ajustes rápidos.",
        )
    }

    private fun findSwitchNearWifiLabel(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val wifiLabelNodes = nodes.filter { node ->
            val text = LimitDetector.normalize(node.text?.toString().orEmpty())
            val desc = LimitDetector.normalize(node.contentDescription?.toString().orEmpty())
            WIFI_LABELS.any { text == it || desc == it || text.startsWith("$it ") }
        }
        for (labelNode in wifiLabelNodes) {
            val parent = labelNode.parent ?: continue
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling.isCheckable || sibling.className?.toString()?.contains("Switch", true) == true) {
                    return sibling
                }
            }
            val grandParent = parent.parent ?: continue
            val candidate = collectNodes(listOf(grandParent)).firstOrNull {
                it.isCheckable || it.className?.toString()?.contains("Switch", true) == true
            }
            if (candidate != null) return candidate
        }
        return null
    }

    private fun inferNodeCheckedState(node: AccessibilityNodeInfo): Boolean? {
        if (node.isCheckable) return node.isChecked
        val combined = LimitDetector.normalize(
            listOfNotNull(
                node.stateDescription?.toString(),
                node.contentDescription?.toString(),
                node.text?.toString(),
            ).joinToString(" "),
        )
        if (OFF_STATES.any { combined.contains(it) }) return false
        if (ON_STATES.any { combined.contains(it) }) return true
        return null
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        return LimitDetector.normalize(
            listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.viewIdResourceName,
            ).joinToString(" "),
        )
    }

    private fun performClickWithParentFallback(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val ancestor = findClickableAncestor(node)
        return ancestor?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            depth++
        }
        return null
    }

    fun collectNodes(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.forEach { queue.addLast(it) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 450) {
            val node = queue.removeFirst()
            visited++
            result.add(node)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return result
    }
}
