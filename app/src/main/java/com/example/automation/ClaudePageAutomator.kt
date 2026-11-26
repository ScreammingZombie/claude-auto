package com.example.automation

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

object ClaudePageAutomator {

    private val MESSAGE_INPUT_HINTS = listOf(
        "how can i help",
        "message claude",
        "reply to claude",
        "write a message",
        "type a message",
        "ask claude",
        "enviar un mensaje",
        "escribe un mensaje",
        "como puedo ayudarte",
        "mensaje a claude",
        "chat",
        "prompt",
    )

    private val SEND_BUTTON_HINTS = listOf(
        "send message",
        "send",
        "enviar mensaje",
        "enviar",
        "submit",
    )

    private val SIDEBAR_BUTTON_HINTS = listOf(
        "open sidebar",
        "sidebar",
        "toggle sidebar",
        "menu",
        "navigation",
        "chats",
        "recents",
        "abrir barra lateral",
        "barra lateral",
        "historial",
    )

    private val SEARCH_BUTTON_HINTS = listOf(
        "search chats",
        "search conversations",
        "search",
        "buscar chats",
        "buscar conversaciones",
        "buscar",
    )

    fun extractVisibleTexts(roots: List<AccessibilityNodeInfo>): List<String> {
        val nodes = QuickSettingsWifiController.collectNodes(roots)
        return nodes.mapNotNull { node ->
            val t = node.text?.toString()?.trim()
            val d = node.contentDescription?.toString()?.trim()
            when {
                !t.isNullOrEmpty() -> t
                !d.isNullOrEmpty() -> d
                else -> null
            }
        }.distinct()
    }

    fun isLikelyBrowserOrClaudeWindow(roots: List<AccessibilityNodeInfo>): Boolean {
        val nodes = QuickSettingsWifiController.collectNodes(roots)
        if (nodes.isEmpty()) return false
        val hasClaudeText = nodes.any { node ->
            val combined = LimitDetector.normalize(
                listOfNotNull(
                    node.text?.toString(),
                    node.contentDescription?.toString(),
                ).joinToString(" "),
            )
            combined.contains("claude")
        }
        val packageName = roots.firstOrNull()?.packageName?.toString().orEmpty()
        return hasClaudeText || (packageName.isNotBlank() && packageName != "com.android.systemui")
    }

    fun findMessageComposer(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val nodes = QuickSettingsWifiController.collectNodes(roots)
        val editables = nodes.filter { node ->
            val cls = node.className?.toString().orEmpty()
            (node.isEditable || cls.contains("EditText", ignoreCase = true)) && node.isEnabled
        }
        if (editables.isEmpty()) return null

        // Excluir barra de direcciones URL del navegador y campos de búsqueda
        val nonAddressBar = editables.filterNot { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val hint = LimitDetector.normalize(
                listOfNotNull(node.hintText?.toString(), node.contentDescription?.toString()).joinToString(" "),
            )
            id.contains("url_bar") ||
                id.contains("omnibox") ||
                id.contains("location_bar") ||
                id.contains("search_box") ||
                hint.contains("search or enter") ||
                hint.contains("busca o escribe") ||
                hint.contains("buscar chats")
        }

        val prioritized = nonAddressBar.firstOrNull { node ->
            val hint = LimitDetector.normalize(
                listOfNotNull(
                    node.hintText?.toString(),
                    node.contentDescription?.toString(),
                    node.text?.toString(),
                ).joinToString(" "),
            )
            MESSAGE_INPUT_HINTS.any { hint.contains(it) }
        }
        return prioritized ?: nonAddressBar.lastOrNull()
    }

    fun isCurrentConversationMatching(
        roots: List<AccessibilityNodeInfo>,
        targetTitle: String,
    ): Boolean {
        if (targetTitle.isBlank()) return true
        val texts = extractVisibleTexts(roots).take(25)
        val normTarget = LimitDetector.normalize(targetTitle)
        return texts.any { text ->
            val normText = LimitDetector.normalize(text)
            normText.length in 3..120 && (normText == normTarget || normText.contains(normTarget))
        }
    }

    fun findConversationInTree(
        roots: List<AccessibilityNodeInfo>,
        targetTitle: String,
    ): ConversationMatchResult<AccessibilityNodeInfo> {
        val nodes = QuickSettingsWifiController.collectNodes(roots)
        val candidates = nodes.mapNotNull { node ->
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val label = text.ifBlank { desc }
            if (label.length !in 3..100) return@mapNotNull null
            if (node.isEditable) return@mapNotNull null

            val normLabel = LimitDetector.normalize(label)
            if (normLabel.contains("claude.ai") || normLabel == "claude") return@mapNotNull null

            val clickableTarget = if (node.isClickable) node else findClickableParent(node)
            if (clickableTarget == null) return@mapNotNull null

            ConversationCandidate(title = label, payload = clickableTarget)
        }.distinctBy { LimitDetector.normalize(it.title) }

        return ClaudeConversationMatcher.findMatch(targetTitle, candidates)
    }

    fun tryOpenSidebarOrSearch(roots: List<AccessibilityNodeInfo>): Boolean {
        val nodes = QuickSettingsWifiController.collectNodes(roots)
        val searchNode = nodes.firstOrNull { node ->
            val label = LimitDetector.normalize(
                listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" "),
            )
            SEARCH_BUTTON_HINTS.any { label.contains(it) } && (node.isClickable || findClickableParent(node) != null)
        }
        if (searchNode != null) {
            return clickNodeOrParent(searchNode)
        }

        val sidebarNode = nodes.firstOrNull { node ->
            val label = LimitDetector.normalize(
                listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" "),
            )
            SIDEBAR_BUTTON_HINTS.any { label.contains(it) } && (node.isClickable || findClickableParent(node) != null)
        }
        if (sidebarNode != null) {
            return clickNodeOrParent(sidebarNode)
        }
        return false
    }

    fun tryTypeInChatSearch(roots: List<AccessibilityNodeInfo>, query: String): Boolean {
        val nodes = QuickSettingsWifiController.collectNodes(roots)
        val searchInput = nodes.firstOrNull { node ->
            val hint = LimitDetector.normalize(
                listOfNotNull(
                    node.hintText?.toString(),
                    node.contentDescription?.toString(),
                    node.text?.toString(),
                ).joinToString(" "),
            )
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val isAddressBar = id.contains("url_bar") || id.contains("omnibox") || id.contains("location_bar")
            !isAddressBar && node.isEditable && SEARCH_BUTTON_HINTS.any { hint.contains(it) }
        } ?: return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        }
        searchInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun inputAndSendPrompt(
        roots: List<AccessibilityNodeInfo>,
        promptText: String,
    ): PromptSendAttempt {
        val composer = findMessageComposer(roots)
            ?: return PromptSendAttempt.ComposerNotFound

        composer.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, promptText)
        }
        val textSet = composer.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!textSet) {
            return PromptSendAttempt.TextInjectionFailed
        }

        val sendButton = findSendButton(roots)
            ?: return PromptSendAttempt.SendButtonNotFound

        val clicked = clickNodeOrParent(sendButton)
        return if (clicked) {
            PromptSendAttempt.SentClicked
        } else {
            PromptSendAttempt.SendClickFailed
        }
    }

    fun verifyPromptDispatched(
        roots: List<AccessibilityNodeInfo>,
        promptText: String,
    ): Boolean {
        val composer = findMessageComposer(roots)
        val currentComposerText = composer?.text?.toString()?.trim().orEmpty()
        if (composer != null && currentComposerText.isEmpty()) {
            return true
        }
        val visibleTexts = extractVisibleTexts(roots)
        val normPrefix = LimitDetector.normalize(promptText).take(36)
        val appearsInMessages = visibleTexts.any {
            val norm = LimitDetector.normalize(it)
            norm.contains(normPrefix) && it != currentComposerText
        }
        val hasStopStreamingButton = visibleTexts.any {
            val norm = LimitDetector.normalize(it)
            norm == "stop" || norm == "stop response" || norm == "detener" || norm == "detener respuesta"
        }
        return appearsInMessages || hasStopStreamingButton
    }

    private fun findSendButton(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val nodes = QuickSettingsWifiController.collectNodes(roots)
        return nodes.firstOrNull { node ->
            val label = LimitDetector.normalize(
                listOfNotNull(
                    node.contentDescription?.toString(),
                    node.text?.toString(),
                    node.viewIdResourceName,
                ).joinToString(" "),
            )
            node.isEnabled &&
                (node.isClickable || findClickableParent(node) != null) &&
                SEND_BUTTON_HINTS.any { label.contains(it) }
        }
    }

    fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return findClickableParent(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            depth++
        }
        return null
    }
}

enum class PromptSendAttempt {
    ComposerNotFound,
    TextInjectionFailed,
    SendButtonNotFound,
    SendClickFailed,
    SentClicked,
}
