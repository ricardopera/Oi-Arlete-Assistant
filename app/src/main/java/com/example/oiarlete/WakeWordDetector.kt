package com.example.oiarlete

import com.example.oiarlete.wake.PorcupineWakeWord
import com.example.oiarlete.engine.EngineAvailability
import android.content.Context

class WakeWordDetector(private val context: Context) {
    private var listener: (() -> Unit)? = null
    private val useNative = BuildConfig.USE_NATIVE_WAKE_ASR && EngineAvailability.porcupineAssets
    private val native: PorcupineWakeWord? = if (useNative) PorcupineWakeWord(context, EngineAvailability.porcupineKeywordFilePath) else null

    fun setOnWakeListener(l: (() -> Unit)?) {
        listener = l
        native?.setOnWakeListener { listener?.invoke() }
    }

    fun start() {
        if (useNative) native?.start() else { /* stub start */ }
    }
    fun stop() {
        if (useNative) native?.stop() else { /* stub stop */ }
    }

    // Somente para testes/simulações por enquanto
    fun simulateDetection() { listener?.invoke() }

    // Test hook
    internal fun isNativeEnabled(): Boolean = useNative

    // Construtor secundário para manter compatibilidade com testes existentes
    constructor(): this(
        try {
            val appClass = Class.forName("android.app.AppGlobals")
            val method = appClass.getDeclaredMethod("getInitialApplication")
            method.isAccessible = true
            (method.invoke(null) as Context)
        } catch (_: Exception) {
            throw IllegalStateException("WakeWordDetector requires Context; use primary constructor in production code")
        }
    )
}
