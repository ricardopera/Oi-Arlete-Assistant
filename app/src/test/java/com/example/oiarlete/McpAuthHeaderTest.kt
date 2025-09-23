package com.example.oiarlete

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Ignore("Ignorar MCP por enquanto — focando em wake word, comandos por voz e LLM local")
class McpAuthHeaderTest {
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
    fun `envia Authorization Bearer quando api key configurada`() {
        // Configurar base e api key
        val prefs = app.getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("mcp_base_url", server.url("/").toString()).putString("mcp_api_key", "TEST_KEY").commit()
        // Resposta vazia 200
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"1\",\"statusCode\":200,\"body\":{}}"))

        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()

        // Disparar caminho remoto com frase longa
        service.testGetWakeDetector().simulateDetection()
        service.testGetSpeechRecognizer().simulateResult(
            "esta é uma frase suficientemente longa para ativar o caminho remoto via MCP"
        )

    val recorded = server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)
    assertTrue("Nenhuma requisição recebida pelo servidor de teste", recorded != null)
    assertEquals("/mcp", recorded!!.path)
    val auth = recorded.getHeader("Authorization")
        assertTrue(auth?.startsWith("Bearer ") == true)
        assertTrue(auth?.endsWith("TEST_KEY") == true)

        controller.destroy()
    }
}
