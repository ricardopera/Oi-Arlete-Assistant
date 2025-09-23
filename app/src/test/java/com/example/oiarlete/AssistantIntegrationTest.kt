package com.example.oiarlete

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After
import org.junit.Before
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AssistantIntegrationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    @Before
    fun setUp() { /* no-op */ }

    @After
    fun tearDown() { /* no-op */ }

    @Test
    fun `T202 frase longa com youtube music dispara intent (sem MCP)`() {
        // Permite ytmusic://
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

        val assistant = Assistant(app)
        val longUtterance = "por favor toque legião urbana tempo perdido no youtube music agora mesmo que eu estou animado"
        assistant.handleUtterance(longUtterance)

        val started = shadowOf(app).nextStartedActivity
        assertEquals("ytmusic", started.data?.scheme)
    }
    @Test
    fun `T202 frase com youtube music dispara intent`() {
        // Permite ytmusic:// para preferencia do executor
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

        val assistant = Assistant(app)
        assistant.handleUtterance("toque legião urbana no YouTube Music")

        val started = shadowOf(app).nextStartedActivity
        assertEquals("ytmusic", started.data?.scheme)
    }

    @Test
    fun `T205 stop retorna cm para Idle`() {
        val cm = ConversationManager()
        cm.onWakeWord()
        cm.onRecognizedIntent()
        cm.onStop()
        assertEquals(ConversationManager.State.Idle, cm.state)
    }
}
