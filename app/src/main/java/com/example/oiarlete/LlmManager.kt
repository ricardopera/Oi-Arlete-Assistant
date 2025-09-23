package com.example.oiarlete

class LlmManager {
    enum class Path { LOCAL, REMOTE }

    fun route(utterance: String): Path {
        val tokens = utterance.trim().split(" ").filter { it.isNotBlank() }
        return if (tokens.size <= 8) Path.LOCAL else Path.REMOTE
    }
}
