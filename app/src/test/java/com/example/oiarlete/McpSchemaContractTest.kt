package com.example.oiarlete

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class McpSchemaContractTest {
    private val mapper = ObjectMapper()
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    private fun load(path: String): String {
        var file = File(path)
        if (!file.exists()) {
            file = File(".." + File.separator + path)
        }
        require(file.exists()) { "Arquivo não encontrado: ${file.path}" }
        return file.readText(Charsets.UTF_8)
    }

    private fun validate(schemaPath: String, json: String): Set<ValidationMessage> {
        val schemaNode: JsonNode = mapper.readTree(load(schemaPath))
        val schema = schemaFactory.getSchema(schemaNode)
        val node = mapper.readTree(json)
        return schema.validate(node)
    }

    @Test
    fun `request deve seguir schema draft-07`() {
    val schemaPath = "specs/001-assistente-de-voz/contracts/mcp.http.json"
        val requestJson = """
            {
              "id": "123e4567-e89b-12d3-a456-426614174000",
              "command": "play_music",
              "params": {"q":"legião urbana tempo perdido"},
              "timestamp": "2025-09-22T10:15:30Z"
            }
        """.trimIndent()
        val errors = validate(schemaPath, requestJson)
        assertTrue("Schema violations: $errors", errors.isEmpty())
    }

    @Test
    fun `response deve seguir schema draft-07`() {
    val schemaPath = "specs/001-assistente-de-voz/contracts/mcp.http.response.json"
        val responseJson = """
            {
              "id": "123e4567-e89b-12d3-a456-426614174000",
              "statusCode": 200,
              "body": {"ok": true}
            }
        """.trimIndent()
        val errors = validate(schemaPath, responseJson)
        assertTrue("Schema violations: $errors", errors.isEmpty())
    }
}
