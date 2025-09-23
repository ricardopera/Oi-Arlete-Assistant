package com.example.oiarlete

class WakeWordDetector {
    private var listener: (() -> Unit)? = null

    fun setOnWakeListener(l: (() -> Unit)?) { listener = l }

    fun start() { /* TODO: Porcupine integration */ }
    fun stop() { /* TODO: stop */ }

    // Somente para testes/simulações por enquanto
    fun simulateDetection() {
        listener?.invoke()
    }
}
