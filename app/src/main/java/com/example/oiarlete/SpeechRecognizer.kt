package com.example.oiarlete

class SpeechRecognizer {
    private var resultListener: ((String) -> Unit)? = null
    private var onActiveListener: (() -> Unit)? = null
    private var onInactiveListener: (() -> Unit)? = null
    private var running: Boolean = false
    private val _activeFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val activeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _activeFlow

    fun setOnResultListener(l: ((String) -> Unit)?) { resultListener = l }
    fun setOnActiveListener(l: (() -> Unit)?) { onActiveListener = l }
    fun setOnInactiveListener(l: (() -> Unit)?) { onInactiveListener = l }

    fun start() {
        if (running) return
        running = true
        _activeFlow.value = true
        onActiveListener?.invoke()
        // TODO: iniciar streaming (VOSK) no futuro
    }
    fun stop() {
        if (!running) return
        running = false
        _activeFlow.value = false
        onInactiveListener?.invoke()
        // TODO: encerrar streaming
    }

    fun isRunning(): Boolean = running

    // Simulação de resultado
    fun simulateResult(text: String) {
        resultListener?.invoke(text)
    }
}
