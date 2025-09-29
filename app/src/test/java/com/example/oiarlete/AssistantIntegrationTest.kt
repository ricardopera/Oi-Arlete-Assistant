package com.example.oiarlete

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private class SpyActionExecutor(context: Context) : ActionExecutor(context) {
        var pauseCount = 0
        var stopCount = 0

        override fun initTts(onReady: (() -> Unit)?) {
            onReady?.invoke()
        }

        override fun pauseMusic(): Boolean {
            pauseCount++
            return true
        }

        override fun stopMusic(): Boolean {
            stopCount++
            return true
        }
    }

    @Test
    fun `T202 frase longa com youtube music dispara intent (sem MCP)`() {
        // Permite ytmusic://
        val pkg = "com.google.android.apps.youtube.music"
        val component = ComponentName(pkg, "com.google.android.apps.youtube.music.activities.MusicActivity")
        val intentFilterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ytmusic://music/search"))
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
        val intentFilterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ytmusic://music/search"))
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
    fun `T203 frase com spotify dispara intent`() {
        val pkg = "com.spotify.music"
        val component = ComponentName(pkg, "com.spotify.music.MainActivity")
    val encodedQuery = Uri.encode("legião urbana")
    val intentFilterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery"))
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
        assistant.handleUtterance("toque legião urbana no Spotify")

        val started = shadowOf(app).nextStartedActivity
        assertEquals("spotify", started.data?.scheme)
    }

    @Test
    fun `T204 tocar sem app usa spotify e remove verbo`() {
        val pkg = "com.spotify.music"
        val component = ComponentName(pkg, "com.spotify.music.MainActivity")
        val encodedQuery = Uri.encode("legião urbana tempo perdido")
        val intentFilterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery"))
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
        assistant.handleUtterance("tocar legião urbana tempo perdido")

        val started = shadowOf(app).nextStartedActivity
        assertEquals("spotify", started.data?.scheme)
        val decoded = Uri.decode(started.data.toString())
        assertFalse(decoded.contains("tocar"))
        assertFalse(decoded.contains("reproduzir"))
    }

    @Test
    fun `T204 reproduzir sem app usa spotify`() {
        val pkg = "com.spotify.music"
        val component = ComponentName(pkg, "com.spotify.music.MainActivity")
    val encodedQuery = Uri.encode("Legião Urbana")
        val intentFilterIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery"))
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
        assistant.handleUtterance("Reproduzir Legião Urbana")

        val started = shadowOf(app).nextStartedActivity
        assertEquals("spotify", started.data?.scheme)
        val decoded = Uri.decode(started.data.toString())
        assertFalse(decoded.contains("reproduzir"))
    }

    @Test
    fun `T206 parar musica aciona stop`() {
        val spy = SpyActionExecutor(app)
        val assistant = Assistant(app, providedActions = spy)
        assistant.handleUtterance("Parar música")
        assertEquals(1, spy.stopCount)
        assertEquals(0, spy.pauseCount)
    }

    @Test
    fun `T207 pausar musica aciona pause`() {
        val spy = SpyActionExecutor(app)
        val assistant = Assistant(app, providedActions = spy)
        assistant.handleUtterance("Pausar música")
        assertEquals(1, spy.pauseCount)
        assertEquals(0, spy.stopCount)
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
