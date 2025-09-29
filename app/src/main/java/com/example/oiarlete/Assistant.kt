package com.example.oiarlete

import android.content.Context

class Assistant(
    private val context: Context,
    private val conv: ConversationManager? = null,
    private val providedActions: ActionExecutor? = null,
) {
    private val llm = LocalLlm()
    private val actions = providedActions ?: ActionExecutor(context)

    init {
        actions.initTts()
        actions.setOnTtsComplete { conv?.onTtsComplete() }
    }

    fun handleUtterance(utterance: String) {
        val normalized = utterance.lowercase()
        // Comandos de controle
        if (normalized == "stop" || normalized.startsWith("stop") || normalized == "pause") {
            conv?.onStop()
            return
        }
        if (normalized.contains("youtube music")) {
            var q = normalized
                .replace("play", "")
                .replace("on youtube music", "")
                .replace("youtube music", "")
                .replace("ytmusic", "")
                .trim()
            if (q.isBlank()) q = utterance.trim()
            actions.playYouTubeMusic(q)
            return
        }

        // Resposta local via LLM local simulado
        val reply = llm.generate(utterance)
        conv?.onRecognizedIntent()
        actions.speak(reply)
    }

    // Somente para testes
    internal fun testActions(): ActionExecutor = actions
}
