package com.example.oiarlete

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.oiarlete.diagnostics.DiagnosticsBus
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class AmazonMusicAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceHandler.post {
            serviceHandler.removeCallbacks(timeoutRunnable)
        }
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AmazonAutomation: serviço habilitado"))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val query = pendingQuery.get() ?: return
        val packageName = event.packageName?.toString() ?: return
        if (!PACKAGE_PREFIXES.any { packageName.startsWith(it) }) return
        val root = rootInActiveWindow ?: return
        when (state.get()) {
            Step.REQUESTED -> {
                if (clickSearch(root)) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AmazonAutomation: botão de busca acionado"))
                    state.set(Step.SEARCH_CLICKED)
                }
            }
            Step.SEARCH_CLICKED -> {
                if (enterQuery(root, query)) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AmazonAutomation: consulta '$query' inserida"))
                    state.set(Step.QUERY_SUBMITTED)
                }
            }
            Step.QUERY_SUBMITTED -> {
                if (triggerPlayback(root)) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AmazonAutomation: tentativa de reprodução iniciada"))
                    reset()
                }
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        reset()
    }

    private fun clickSearch(root: AccessibilityNodeInfo): Boolean {
        val node = findNode(root) { candidate ->
            candidate.isClickable && candidate.isEnabled && matchesSearchDescriptor(candidate)
        }
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    private fun enterQuery(root: AccessibilityNodeInfo, query: String): Boolean {
        val input = findNode(root) { candidate ->
            val className = candidate.className?.toString() ?: ""
            val viewId = candidate.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""
            candidate.isFocusable &&
                (className.contains("EditText") || viewId.contains("search") || viewId.contains("query"))
        } ?: return false

        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        }
        val set = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!set) {
            return false
        }
        return true
    }

    private fun triggerPlayback(root: AccessibilityNodeInfo): Boolean {
        val candidate = findNode(root) { node ->
            node.isClickable && node.isEnabled && matchesPlayDescriptor(node)
        }
        if (candidate != null) {
            return candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    private fun matchesSearchDescriptor(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""
        return desc.contains("buscar") || desc.contains("search") ||
            text.contains("buscar") || text.contains("search") ||
            viewId.contains("search") || viewId.contains("buscar")
    }

    private fun matchesPlayDescriptor(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.getDefault()) ?: ""
        return desc.contains("reproduzir") || desc.contains("play") ||
            text.contains("reproduzir") || text.contains("play") ||
            viewId.contains("play") || viewId.contains("result")
    }

    private fun findNode(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val match = findNode(child, predicate)
            if (match != null) return match
        }
        return null
    }

    private fun reset() {
        pendingQuery.set(null)
        state.set(Step.IDLE)
        serviceHandler.removeCallbacks(timeoutRunnable)
    }

    companion object {
        private enum class Step { IDLE, REQUESTED, SEARCH_CLICKED, QUERY_SUBMITTED }

        private val pendingQuery = AtomicReference<String?>(null)
        private val state = AtomicReference(Step.IDLE)
        private val serviceHandler = Handler(Looper.getMainLooper())
        private val timeoutRunnable = Runnable {
            if (state.get() != Step.IDLE) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AmazonAutomation: tempo limite atingido, limpando estado"))
            }
            pendingQuery.set(null)
            state.set(Step.IDLE)
        }

        private val PACKAGE_PREFIXES = listOf(
            "com.amazon.music",
            "com.amazon.mp3"
        )

        fun requestPlayback(query: String) {
            pendingQuery.set(query)
            state.set(Step.REQUESTED)
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AmazonAutomation: armado para '$query'"))
            serviceHandler.removeCallbacks(timeoutRunnable)
            serviceHandler.postDelayed(timeoutRunnable, 8000)
        }
    }
}
