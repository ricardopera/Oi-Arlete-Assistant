package com.example.oiarlete

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Button
import android.widget.Toast
import android.content.Context
import androidx.core.content.edit
import com.example.oiarlete.mcp.McpClient
import com.example.oiarlete.mcp.McpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.UUID
import com.example.oiarlete.ui.theme.OiArleteTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OiArleteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE)
    var url by remember { mutableStateOf(TextFieldValue(prefs.getString("mcp_base_url", BuildConfig.MCP_BASE_URL) ?: "")) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configurações do MCP")
        BasicTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.padding(8.dp)
        )
        Button(onClick = {
            prefs.edit { putString("mcp_base_url", url.text.trim()) }
            Toast.makeText(context, "URL salva", Toast.LENGTH_SHORT).show()
        }) { Text("Salvar URL") }

        Button(onClick = {
            val base = url.text.trim()
            testing = true
            // Teste simples: enviar interpret com utterance curta; resultado será ignorado
            try {
                val client = McpClient(base)
                val req = McpRequest(
                    id = UUID.randomUUID().toString(),
                    command = "interpret",
                    params = mapOf("utterance" to "teste de conexão")
                )
                scope.launch(Dispatchers.IO) {
                    val resp = runCatching { client.execute(req).execute() }.getOrNull()
                    // mudar estado de volta na Main
                    launch(Dispatchers.Main) {
                        testing = false
                        if (resp?.isSuccessful == true) {
                            Toast.makeText(context, "Conexão OK", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Falha na conexão", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                testing = false
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }) { Text(if (testing) "Testando..." else "Testar conexão") }
    }
}

@Preview(showBackground = true)
@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun GreetingPreview() {
    OiArleteTheme {
        SettingsScreen()
    }
}