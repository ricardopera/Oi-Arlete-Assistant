package com.example.oiarlete

import android.content.Context
import java.io.File
import java.util.Properties

class SecretsManager private constructor(private val props: Properties) {
    fun get(key: String): String? = props.getProperty(key)?.takeIf { it.isNotBlank() }

    fun getMcpBaseUrl(): String? = get(KEY_MCP_BASE_URL)
    fun getMcpApiKey(): String? = get(KEY_MCP_API_KEY)

    companion object {
        const val FILE_NAME = "secrets.properties"
        const val KEY_MCP_BASE_URL = "MCP_BASE_URL"
        const val KEY_MCP_API_KEY = "MCP_API_KEY"

        fun load(context: Context): SecretsManager {
            val p = Properties()
            // 1) filesDir (facilita testes/DEV)
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                runCatching { file.inputStream().use { p.load(it) } }
            } else {
                // 2) assets/secrets.properties (empacotado no app)
                runCatching { context.assets.open(FILE_NAME).use { p.load(it) } }
            }
            return SecretsManager(p)
        }
    }
}
