package com.example.oiarlete

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import android.os.Looper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AsrActiveFlowTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `activeFlow reflete start e stop do ASR`() {
        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()
        val asr = service.testGetSpeechRecognizer()

        // processar callbacks pendentes
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(asr.activeFlow.value)
        asr.start()
        assertTrue(asr.activeFlow.value)
        asr.stop()
        assertFalse(asr.activeFlow.value)

        controller.destroy()
    }
}
