package com.example.oiarlete

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmRoutingTest {
    @Test
    fun `frases curtas vao para LOCAL`() {
        val m = LlmManager()
        assertEquals(LlmManager.Path.LOCAL, m.route("que horas sao"))
        assertEquals(LlmManager.Path.LOCAL, m.route("toque legiao urbana"))
    }

    @Test
    fun `frases longas vao para REMOTO`() {
        val m = LlmManager()
        val long = """
            por favor encontre e reproduza a versao ao vivo daquela musica do legiao urbana chamada tempo perdido gravada no estadio mane garrincha em brasilia
        """.trimIndent()
        assertEquals(LlmManager.Path.REMOTE, m.route(long))
    }
}
