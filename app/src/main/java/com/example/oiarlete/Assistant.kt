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
        if (normalized == "parar" || normalized.startsWith("pare") || normalized == "stop") {
            actions.stopMusic()
            conv?.onStop()
            return
        }
        if (normalized.contains("parar música") || normalized.contains("parar musica")) {
            actions.stopMusic()
            conv?.onStop()
            return
        }
        if (normalized.contains("pausar música") || normalized.contains("pausar musica") || normalized.startsWith("pausar")) {
            actions.pauseMusic()
            return
        }
        if (normalized.contains("youtube music")) {
            var q = normalized
                .replace("toque", "")
                .replace("no youtube music", "")
                .replace("no youtube música", "")
                .replace("no ytmusic", "")
                .replace("youtube music", "")
                .trim()
            if (q.isBlank()) q = utterance.trim()
            actions.playYouTubeMusic(q)
            return
        }
        if (normalized.contains("spotify") || normalized.contains("spotfy")) {
            val tokens = listOf(
                "toque",
                "tocar",
                "reproduzir",
                "reproduza",
                "no spotify",
                "na spotify",
                "no spotfy",
                "na spotfy",
                "spotify",
                "spotfy"
            )
            val q = sanitizeQuery(utterance, tokens)
            actions.playSpotifyMusic(q)
            return
        }
        if (normalized.contains("amazon music") || normalized.contains("prime music")) {
            var q = normalized
                .replace("toque", "")
                .replace("na amazon music", "")
                .replace("no amazon music", "")
                .replace("amazon music", "")
                .replace("na prime music", "")
                .replace("no prime music", "")
                .replace("prime music", "")
                .trim()
            if (q.isBlank()) q = utterance.trim()
            actions.playAmazonMusic(q)
            return
        }
        if (normalized.startsWith("tocar") || normalized.startsWith("toque") ||
            normalized.startsWith("reproduzir") || normalized.startsWith("reproduza")) {
            val q = sanitizeQuery(utterance, listOf("tocar", "toque", "reproduzir", "reproduza"))
            actions.playSpotifyMusic(q)
            return
        }

        // Resposta local via LLM local simulado
        val reply = llm.generate(utterance)
        conv?.onRecognizedIntent()
        actions.speak(reply)
    }

    fun cleanup() {
        actions.cleanup()
    }

    // Somente para testes
    internal fun testActions(): ActionExecutor = actions

    private fun sanitizeQuery(utterance: String, tokens: List<String>): String {
        var result = utterance
        tokens.forEach { token ->
            val pattern = Regex("(?i)${Regex.escape(token)}")
            result = result.replace(pattern, " ")
        }
        return result.replace("\\s+".toRegex(), " ").trim()
    }
}
