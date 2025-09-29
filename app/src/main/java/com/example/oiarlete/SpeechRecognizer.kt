package com.example.oiarlete

import com.example.oiarlete.asr.VoskRecognizer
import com.example.oiarlete.asr.AndroidSpeechRecognizer
import com.example.oiarlete.asr.StreamingApiRecognizer
import com.example.oiarlete.engine.EngineAvailability

class SpeechRecognizer {
    private var resultListener: ((String) -> Unit)? = null
    private var onActiveListener: (() -> Unit)? = null
    private var onInactiveListener: (() -> Unit)? = null
    private var running: Boolean = false
    private val _activeFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val activeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _activeFlow

    // Escolher tipo de ASR via SharedPreferences
    private val context = AppGlobals.get()
    private val prefs = context.getSharedPreferences("arlete_prefs", android.content.Context.MODE_PRIVATE)
    private val asrType = prefs.getString("asr_type", "android") ?: "android" // "android", "vosk", "streaming_api"
    
    private val useAndroidASR = asrType == "android"
    private val useStreamingAPI = asrType == "streaming_api"
    private val useVosk = asrType == "vosk" && BuildConfig.USE_NATIVE_WAKE_ASR && EngineAvailability.voskAssets
    
    private val androidASR: AndroidSpeechRecognizer? = if (useAndroidASR) AndroidSpeechRecognizer(context) else null
    private val streamingASR: StreamingApiRecognizer? = if (useStreamingAPI) StreamingApiRecognizer(context) else null
    private val native: VoskRecognizer? = if (useVosk) VoskRecognizer(EngineAvailability.voskModelDirPath) else null

    fun setOnResultListener(l: ((String) -> Unit)?) {
        resultListener = l
        androidASR?.setOnResultListener { txt -> resultListener?.invoke(txt) }
        streamingASR?.setOnResultListener { txt -> resultListener?.invoke(txt) }
        native?.setOnResultListener { txt -> resultListener?.invoke(txt) }
    }
    fun setOnActiveListener(l: (() -> Unit)?) { onActiveListener = l }
    fun setOnInactiveListener(l: (() -> Unit)?) { onInactiveListener = l }

    fun start() {
        if (running) return
        running = true
        _activeFlow.value = true
        onActiveListener?.invoke()
        androidASR?.start()
        streamingASR?.start()
        native?.start()
    }
    fun stop() {
        if (!running) return
        running = false
        _activeFlow.value = false
        onInactiveListener?.invoke()
        androidASR?.stop()
        streamingASR?.stop()
        native?.stop()
    }

    fun isRunning(): Boolean = running

    // Simulação de resultado
    fun simulateResult(text: String) {
        resultListener?.invoke(text)
    }
}
