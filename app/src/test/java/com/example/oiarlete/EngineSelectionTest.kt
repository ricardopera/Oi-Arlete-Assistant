package com.example.oiarlete

import com.example.oiarlete.engine.EngineAvailability
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class EngineSelectionTest {
    @Test
    fun `wake word detector respeita flag e disponibilidade de assets`() {
        // Sem assets → sempre falso
        EngineAvailability.porcupineAssets = false
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val wNoAssets = WakeWordDetector(ctx)
        assertEquals(false, wNoAssets.isNativeEnabled())

        // Com assets → segue o flag de flavor
        EngineAvailability.porcupineAssets = true
        val wWithAssets = WakeWordDetector(ctx)
        assertEquals(BuildConfig.USE_NATIVE_WAKE_ASR, wWithAssets.isNativeEnabled())
    }
}
