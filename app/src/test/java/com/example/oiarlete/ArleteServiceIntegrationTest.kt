package com.example.oiarlete

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ArleteServiceIntegrationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `wake→ASR→Assistant dispara YouTube Music`() {
        // Preparar resolução para ytmusic://
        val pkg = "com.google.android.apps.youtube.music"
        val component = ComponentName(pkg, "com.google.android.apps.youtube.music.activities.MusicActivity")
        val intentFilterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ytmusic://"))
        shadowOf(app.packageManager).addResolveInfoForIntent(
            intentFilterIntent,
            android.content.pm.ResolveInfo().apply {
                this.activityInfo = android.content.pm.ActivityInfo().apply {
                    this.packageName = pkg
                    this.name = component.className
                }
            }
        )

        // Startar o serviço
        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()

        // Simular ciclo: wake → ASR "toque legião urbana no YouTube Music"
        service.testGetWakeDetector().simulateDetection()
        service.testGetSpeechRecognizer().simulateResult("toque legião urbana no YouTube Music")

        // Verificar intent lançado
        val started = shadowOf(app).nextStartedActivity
        assertEquals("ytmusic", started.data?.scheme)

        controller.destroy()
    }

    @Test
    fun `stateFlow reflete transições básicas`() {
        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()

        // Idle inicial
        assertEquals(ConversationManager.State.Idle, service.stateFlow().value)

        // Wake leva a ActiveListening
        service.testGetWakeDetector().simulateDetection()
        assertEquals(ConversationManager.State.ActiveListening, service.stateFlow().value)

        // Reconhecimento leva a Speaking (via Assistant)
        service.testGetSpeechRecognizer().simulateResult("qual a hora?")
        assertEquals(ConversationManager.State.Speaking, service.stateFlow().value)

        // TTS completo devolve a ActiveListening
        service.testGetAssistant().testActions().simulateTtsComplete()
        assertEquals(ConversationManager.State.ActiveListening, service.stateFlow().value)

        controller.destroy()
    }
}
