package com.example.oiarlete.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class McpClientIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var client: McpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = McpClient(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `200 ok retorna corpo parseado`() {
        val id = UUID.randomUUID().toString()
        val body = "{" +
                "\"id\":\"$id\",\n" +
                "\"statusCode\":200,\n" +
                "\"body\":{\"ok\":true}}"
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val resp = client.execute(McpRequest(id, "ping", emptyMap())).execute()
        assertTrue(resp.isSuccessful)
        val parsed = resp.body()!!
        assertEquals(id, parsed.id)
        assertEquals(200, parsed.statusCode)
        assertEquals(true, parsed.body?.get("ok"))
    }

    @Test
    fun `erro http propaga codigo`() {
        val id = UUID.randomUUID().toString()
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val resp = client.execute(McpRequest(id, "ping", emptyMap())).execute()
        assertEquals(500, resp.code())
    }
}
