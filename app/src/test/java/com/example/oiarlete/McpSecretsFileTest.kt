package com.example.oiarlete

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
class McpSecretsFileTest {
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
    fun `lê API key e baseUrl do arquivo secrets properties`() {
        // Limpar prefs para garantir que secrets.properties seja usado
        val prefs = app.getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        // Gravar secrets.properties em filesDir para ter prioridade, com URL do MockWebServer
        val file = File(app.filesDir, SecretsManager.FILE_NAME)
        file.writeText(
            "${SecretsManager.KEY_MCP_BASE_URL}=${server.url("/")}\n" +
            "${SecretsManager.KEY_MCP_API_KEY}=SECRETS_FILE_KEY\n"
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"1\",\"statusCode\":200,\"body\":{}}"))

        val controller = Robolectric.buildService(ArleteService::class.java).create().startCommand(0, 0)
        val service = controller.get()

        service.testGetWakeDetector().simulateDetection()
        service.testGetSpeechRecognizer().simulateResult(
            "esta é uma frase bem longa para forçar o caminho remoto via MCP secrets agora"
        )

    val recorded = server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)
    assertTrue("Nenhuma requisição recebida pelo servidor de teste", recorded != null)
    assertEquals("/mcp", recorded!!.path)
    val auth = recorded.getHeader("Authorization")
        assertTrue(auth?.startsWith("Bearer ") == true)
        assertTrue(auth?.endsWith("SECRETS_FILE_KEY") == true)

        controller.destroy()
    }
}
