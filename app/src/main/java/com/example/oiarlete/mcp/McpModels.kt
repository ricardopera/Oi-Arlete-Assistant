package com.example.oiarlete.mcp

data class McpRequest(
    val id: String,
    val command: String,
    val params: Map<String, Any?>,
    val timestamp: String? = null,
)

data class McpResponse(
    val id: String,
    val statusCode: Int,
    val body: Map<String, Any?>? = null,
    val error: String? = null,
)
