package com.example.oiarlete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationIntegrationTest {

    @Test
    fun `wake to listen deve ocorrer em menos de 500ms`() {
        val cm = ConversationManager()
        val start = System.nanoTime()
        cm.onWakeWord()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(ConversationManager.State.ActiveListening, cm.state)
        assertTrue("Transição demorou ${elapsedMs}ms", elapsedMs < 500)
    }

    @Test
    fun `timeout retorna para Idle`() {
        val cm = ConversationManager()
        cm.onWakeWord()
        cm.onRecognizedIntent() // falando
        assertEquals(ConversationManager.State.Speaking, cm.state)
        cm.onTimeout()
        assertEquals(ConversationManager.State.Idle, cm.state)
    }
}
