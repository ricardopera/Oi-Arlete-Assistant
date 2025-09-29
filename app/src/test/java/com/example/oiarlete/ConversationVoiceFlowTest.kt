package com.example.oiarlete

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationVoiceFlowTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `wake→listen→speak→tts done volta a ActiveListening`() {
        val cm = ConversationManager()
        val assistant = Assistant(app, cm)

        // wake
        cm.onWakeWord()
        assertEquals(ConversationManager.State.ActiveListening, cm.state)

        // fala
        assistant.handleUtterance("qual o clima hoje?")
        assertEquals(ConversationManager.State.Speaking, cm.state)

        // simula fim do TTS
        val actions = assistant.testActions()
        // durante fala deve requisitar foco de audio (não assertivo, pois depende do ambiente Robolectric)
        actions.simulateTtsComplete()
        assertEquals(ConversationManager.State.ActiveListening, cm.state)
    }

    @Test
    fun `comando stop envia estado para Idle`() {
        val cm = ConversationManager()
        val assistant = Assistant(app, cm)
        cm.onWakeWord()
        assistant.handleUtterance("stop")
        assertEquals(ConversationManager.State.Idle, cm.state)
    }
}
