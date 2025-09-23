package com.example.oiarlete.asr

/**
 * Futuro wrapper para Vosk/Whisper.
 * Deve alinhar-se com a API de SpeechRecognizer: start/stop, setOnResultListener.
 */
class VoskRecognizer {
    private var listener: ((String) -> Unit)? = null
    fun setOnResultListener(l: ((String) -> Unit)?) { listener = l }
    fun start() { /* TODO iniciar sessão de reconhecimento */ }
    fun stop() { /* TODO encerrar sessão */ }
}
