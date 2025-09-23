package com.example.oiarlete

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Ignore("Ignorar MCP por enquanto — focando em wake word, comandos por voz e LLM local")
class ArleteServiceMcpConfigTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `usa MCP quando URL estiver em SharedPreferences`() {
        // Mock da resposta interpret com play_youtube_music
        val body = "{" +
                "\"id\":\"abc\",\n" +
                "\"statusCode\":200,\n" +
                "\"body\":{\"action\":\"play_youtube_music\",\"query\":\"legião urbana tempo perdido\"}}"
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        // Registrar resolução ytmusic://
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

        // Configurar URL no SharedPreferences
        val prefs = app.getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("mcp_base_url", server.url("/").toString()).commit()

        // Start do serviço
        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()

        // Simula wake+asr
        service.testGetWakeDetector().simulateDetection()
        service.testGetSpeechRecognizer().simulateResult(
            "esta é uma frase bem longa com muitas palavras para forçar o caminho remoto de interpretação"
        )

        val started = shadowOf(app).nextStartedActivity
        assertEquals("ytmusic", started.data?.scheme)

        controller.destroy()
    }

    @Test
    fun `fallback local quando MCP não configurado`() {
        // Garantir prefs limpas
        val prefs = app.getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Registrar resolução ytmusic://
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

        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()

        service.testGetWakeDetector().simulateDetection()
        service.testGetSpeechRecognizer().simulateResult("toque legião urbana no YouTube Music")

        val started = shadowOf(app).nextStartedActivity
        assertEquals("ytmusic", started.data?.scheme)

        controller.destroy()
    }
}
