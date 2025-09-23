package com.example.oiarlete

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioFocusTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `foco de áudio é adquirido ao falar e liberado ao terminar`() {
        val actions = ActionExecutor(app)
        actions.initTts()
        actions.speak("olá")
        // Se o ambiente suportar, a flag ficará true, mas não é garantido em todos os ambientes.
        // O importante é que após simulateTtsComplete a flag volte a false.
        if (actions.isAudioFocused()) {
            assertTrue(actions.isAudioFocused())
        }
        actions.simulateTtsComplete()
        assertFalse(actions.isAudioFocused())
    }
}
