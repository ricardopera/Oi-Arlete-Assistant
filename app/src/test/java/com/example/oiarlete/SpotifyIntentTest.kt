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
class SpotifyIntentTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `usa spotify scheme quando disponível`() {
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

        val executor = ActionExecutor(app)
        executor.playSpotifyMusic("legião urbana tempo perdido")

        val started = shadowOf(app).nextStartedActivity
        assertEquals("spotify", started.data?.scheme)
    }

    @Test
    fun `fallback para web search quando app não disponível`() {
        val executor = ActionExecutor(app)
        val query = "legião urbana tempo perdido"
        executor.playSpotifyMusic(query)

        val started = shadowOf(app).nextStartedActivity
        assertEquals("https", started.data?.scheme)
        assertEquals("open.spotify.com", started.data?.host)
    val qs = started.data?.lastPathSegment
    assertEquals(query, qs?.let { Uri.decode(it) })
    }
}
