package com.example.oiarlete

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class YouTubeMusicIntentTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `usa ytmusic scheme quando disponível`() {
        // Simula que existe uma activity capaz de lidar com ytmusic://
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

        val executor = ActionExecutor(app)
        executor.playYouTubeMusic("legião urbana tempo perdido")

        val started = shadowOf(app).nextStartedActivity
        assertEquals("ytmusic", started.data?.scheme)
    }

    @Test
    fun `fallback para web search quando app não disponível`() {
        val executor = ActionExecutor(app)
        val query = "legião urbana tempo perdido"
        executor.playYouTubeMusic(query)

        val started = shadowOf(app).nextStartedActivity
        assertEquals("https", started.data?.scheme)
        assertEquals("music.youtube.com", started.data?.host)
        // query string deve conter q=
        val qs = started.data?.getQueryParameter("q")
        // Uri.encode no executor assegura codificação; aqui basta verificar conteúdo
        assertEquals(query, qs)
    }
}
