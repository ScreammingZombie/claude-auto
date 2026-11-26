package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ClaudeAutoRunApp
import com.example.automation.AccessibilityBridge
import java.util.concurrent.atomic.AtomicBoolean

class ClaudeAccessibilityService : AccessibilityService() {

    private var lastEventDispatchedAt = 0L
    private val connectedFlag = AtomicBoolean(false)

    private val bridge = object : AccessibilityBridge {
        override fun isConnected(): Boolean = connectedFlag.get()

        override fun performGlobalAction(action: Int): Boolean {
            return this@ClaudeAccessibilityService.performGlobalAction(action)
        }

        override fun getWindowRoots(): List<AccessibilityNodeInfo> {
            val roots = mutableListOf<AccessibilityNodeInfo>()
            rootInActiveWindow?.let { roots.add(it) }
            runCatching {
                windows?.forEach { window ->
                    window.root?.let { root ->
                        if (roots.none { it == root }) {
                            roots.add(root)
                        }
                    }
                }
            }
            return roots
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedFlag.set(true)
        ClaudeAutoRunApp.from(this).coordinator.registerAccessibilityBridge(bridge)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val app = ClaudeAutoRunApp.fromOrNull(this) ?: return
        if (!app.coordinator.isActive()) return

        val now = System.currentTimeMillis()
        if (now - lastEventDispatchedAt < EVENT_DEBOUNCE_MS) return
        lastEventDispatchedAt = now

        app.coordinator.onAccessibilityWindowUpdated()
    }

    override fun onInterrupt() {
        connectedFlag.set(false)
        ClaudeAutoRunApp.fromOrNull(this)?.coordinator?.unregisterAccessibilityBridge()
    }

    override fun onDestroy() {
        connectedFlag.set(false)
        ClaudeAutoRunApp.fromOrNull(this)?.coordinator?.unregisterAccessibilityBridge()
        super.onDestroy()
    }

    companion object {
        private const val EVENT_DEBOUNCE_MS = 450L
    }
}
