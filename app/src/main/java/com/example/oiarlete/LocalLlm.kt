package com.example.oiarlete

class LocalLlm {
    fun generate(utterance: String): String {
        val u = utterance.trim()
        if (u.isBlank()) return "Não entendi. Pode repetir?"
        // Resposta simples de eco por enquanto
        return "Você disse: ${u}"
    }
}
