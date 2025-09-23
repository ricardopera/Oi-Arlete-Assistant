package com.example.oiarlete

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BootReceiverIntegrationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `T206 boot completed inicia servico`() {
        val receiver = BootCompletedReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(app, intent)

        val started = shadowOf(app).nextStartedService
        // Em Android 8+, startForegroundService é equivalente no Shadow a nextStartedService
        assertEquals(ArleteService::class.java.name, started.component?.className)
    }
}
