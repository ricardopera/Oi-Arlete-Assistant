package com.example.oiarlete

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import android.os.Looper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AsrControlAndTimeoutTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `ASR pausa ao falar e retoma ao completar TTS`() {
        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()

        val wake = service.testGetWakeDetector()
        val asr = service.testGetSpeechRecognizer()
        val assistant = service.testGetAssistant()

        // Wake ativa ASR
        wake.simulateDetection()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertTrue(asr.isRunning())

        // Reconhecimento inicia fala → ASR deve pausar
        service.testGetSpeechRecognizer().simulateResult("como está o tempo hoje?")
    // aguarda debounce do serviço
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertFalse(asr.isRunning())

        // Completar TTS deve retomar ASR
        assistant.testActions().simulateTtsComplete()
    // aguarda debounce do serviço
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertTrue(asr.isRunning())

        controller.destroy()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `timeout em ActiveListening leva a Idle`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val cm = ConversationManager(listeningTimeoutSeconds = 1, scope = scope)
        // Ativa escuta
        cm.onWakeWord()
        assertEquals(ConversationManager.State.ActiveListening, cm.state)

        // Avança o tempo virtual em 1,2s
        advanceTimeBy(1200)

        assertEquals(ConversationManager.State.Idle, cm.state)
    }
}
