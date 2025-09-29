package com.example.oiarlete

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.oiarlete.engine.EngineAvailability
import com.example.oiarlete.diagnostics.DiagnosticsBus
import kotlinx.coroutines.flow.collectLatest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.pm.PackageManager
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auto-iniciar o serviço em segundo plano para extrair modelos e começar a captura (se permissão já concedida)
        try {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                val itn = android.content.Intent(this, ArleteService::class.java).setAction(ArleteService.ACTION_START)
                androidx.core.content.ContextCompat.startForegroundService(this, itn)
            }
        } catch (_: Exception) { }
        setContent {
            OiArleteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!BuildConfig.USE_NATIVE_WAKE_ASR) {
                            SettingsScreen()
                            Spacer(Modifier.height(16.dp))
                        }
                        DiagnosticsPanel()
                    }
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
        modifier = Modifier.fillMaxWidth().padding(24.dp),
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

@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun DiagnosticsPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var micState by remember { mutableStateOf("Parado") }
    var engineState by remember { mutableStateOf("Porcupine=${EngineAvailability.porcupineAssets} Vosk=${EngineAvailability.voskAssets}") }
    var lastSpeechResult by remember { mutableStateOf("") }
    val prefs = context.getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE)
    var porcupineLang by remember { mutableStateOf(prefs.getString("porcupine_model_lang", "AUTO") ?: "AUTO") }
    var selectedAudioId by remember { mutableStateOf(prefs.getInt("audio_input_id", 0)) }
    var activeAudioDesc by remember { mutableStateOf(prefs.getString("audio_input_active_desc", "(desconhecido) ") ?: "(desconhecido)") }
    var logs by remember { mutableStateOf(listOf<String>()) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { micTest(context) }
        } else {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Permissão de microfone negada"))
        }
    }
    // Launcher separado para iniciar serviço após permissão
    val servicePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val itn = Intent(context, ArleteService::class.java).setAction(ArleteService.ACTION_START)
            ContextCompat.startForegroundService(context, itn)
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Serviço iniciado (após permissão)"))
        } else {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Permissão de microfone é necessária para iniciar o serviço"))
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            DiagnosticsBus.events.collectLatest { e ->
                when (e) {
                    is DiagnosticsBus.Event.MicLevel -> {
                        logs = (listOf("Mic RMS: ${e.rms}") + logs).take(30)
                    }
                    is DiagnosticsBus.Event.WakeDetected -> {
                        logs = (listOf("Wake: ${e.source}") + logs).take(30)
                    }
                    is DiagnosticsBus.Event.EngineStatus -> {
                        engineState = "Porcupine=${e.porcupineLoaded} Vosk=${e.voskLoaded}"
                    }
                    is DiagnosticsBus.Event.SpeechResult -> {
                        lastSpeechResult = e.text
                        logs = (listOf("🎤 RECONHECIDO: \"${e.text}\"") + logs).take(30)
                    }
                    is DiagnosticsBus.Event.Error -> {
                        logs = (listOf("Erro: ${e.message}") + logs).take(30)
                    }
                    is DiagnosticsBus.Event.Info -> {
                        logs = (listOf(e.message) + logs).take(30)
                    }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxWidth().padding(24.dp), shape = RectangleShape) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnóstico")
            
            // Informação de build atual
            val buildTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            Text(
                "Build: $buildTime", 
                color = androidx.compose.ui.graphics.Color.Gray,
                fontSize = 12.sp
            )
            
            Text(engineState)
            
            // Mostrar último resultado de fala de forma destacada
            if (lastSpeechResult.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .background(
                            androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.3f),
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Text("🎤 ÚLTIMO RECONHECIMENTO:")
                        Text(
                            text = "\"$lastSpeechResult\"",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
            // Seletor de tipo de ASR
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo ASR:")
                val currentAsrType = prefs.getString("asr_type", "android") ?: "android"
                listOf(
                    "android" to "Android Nativo",
                    "streaming_api" to "API Streaming",
                    "vosk" to "Vosk Local"
                ).forEach { (key, label) ->
                    Button(onClick = {
                        prefs.edit { putString("asr_type", key) }
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR: $label selecionado (reinicie o serviço)"))
                    }) { 
                        Text(if (currentAsrType == key) "[$label]" else label)
                    }
                }
            }
            
            // Configuração da API (se streaming_api estiver selecionado)
            if (prefs.getString("asr_type", "android") == "streaming_api") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Configuração da API:")
                    var apiKey by remember { mutableStateOf(prefs.getString("speech_api_key", "") ?: "") }
                    var apiEndpoint by remember { mutableStateOf(prefs.getString("speech_api_endpoint", "https://speech.googleapis.com/v1/speech:recognize") ?: "") }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("API Key: ")
                        BasicTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.padding(4.dp)
                        )
                        Button(onClick = {
                            prefs.edit { putString("speech_api_key", apiKey) }
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("API Key salva"))
                        }) { Text("Salvar") }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Endpoint: ")
                        BasicTextField(
                            value = apiEndpoint,
                            onValueChange = { apiEndpoint = it },
                            modifier = Modifier.padding(4.dp)
                        )
                        Button(onClick = {
                            prefs.edit { putString("speech_api_endpoint", apiEndpoint) }
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Endpoint salvo"))
                        }) { Text("Salvar") }
                    }
                }
            }
            
            // Seletor simples de idioma do modelo Porcupine
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modelo Porcupine:")
                listOf("AUTO", "PT-BR", "PT", "EN").forEach { opt ->
                    Button(onClick = {
                        porcupineLang = opt
                        prefs.edit { putString("porcupine_model_lang", opt) }
                        // Também persistir em secrets em runtime não é possível; usamos SharedPreferences e ArleteService lê SecretsManager
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Modelo Porcupine: $opt (reinicie o serviço)"))
                    }) { Text(opt) }
                }
            }
            // Seletor do dispositivo de áudio
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Entrada USB:")
                val inputs = remember { com.example.oiarlete.audio.AudioInputSelector.listInputs(context) }
                Button(onClick = {
                    // Recarregar lista e logar
                    val list = com.example.oiarlete.audio.AudioInputSelector.listInputs(context)
                    if (list.isEmpty()) {
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Nenhum dispositivo USB encontrado! Conecte um microfone USB."))
                    } else {
                        list.forEach {
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Input USB ${it.id}: ${com.example.oiarlete.audio.AudioInputSelector.describe(it)}"))
                        }
                    }
                    activeAudioDesc = prefs.getString("audio_input_active_desc", activeAudioDesc) ?: activeAudioDesc
                }) { Text("Listar USB") }
                Text("Ativo: $activeAudioDesc")
                // Botões por ID (até 6) + campo rápido via texto seria o ideal; mantemos botões por ora.
                if (inputs.isEmpty()) {
                    Text("⚠️ Nenhum USB", color = Color.Red)
                } else {
                    inputs.take(6).forEach { dev ->
                        Button(onClick = {
                            selectedAudioId = dev.id
                            prefs.edit { putInt("audio_input_id", selectedAudioId) }
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Selecionado input USB id=$selectedAudioId (${com.example.oiarlete.audio.AudioInputSelector.describe(dev)})"))
                        }) { Text("USB ${dev.id}") }
                    }
                }
                Button(onClick = {
                    selectedAudioId = 0
                    prefs.edit { putInt("audio_input_id", 0) }
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Entrada de áudio: padrão (pode causar crashes!)"))
                }) { Text("Padrão") }
            }
            RowButtons(micState, onMicTest = {
                val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    // Pausar captura do serviço antes do teste
                    ContextCompat.startForegroundService(context, Intent(context, ArleteService::class.java).setAction(ArleteService.ACTION_PAUSE_CAPTURE))
                    scope.launch {
                        micTest(context)
                        // Retomar captura após o teste
                        ContextCompat.startForegroundService(context, Intent(context, ArleteService::class.java).setAction(ArleteService.ACTION_RESUME_CAPTURE))
                    }
                } else {
                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }, onCheckModels = {
                DiagnosticsBus.emit(DiagnosticsBus.Event.EngineStatus(
                    EngineAvailability.porcupineAssets, EngineAvailability.voskAssets
                ))
            })
            // Controles do serviço
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    // Start Foreground Service com checagem de permissão
                    val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val itn = Intent(context, ArleteService::class.java).setAction(ArleteService.ACTION_START)
                        ContextCompat.startForegroundService(context, itn)
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Serviço iniciado"))
                    } else {
                        // Solicitar permissão; start será feito no callback servicePermissionLauncher
                        servicePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }) { Text("Iniciar serviço") }
                Button(onClick = {
                    val itn = Intent(context, ArleteService::class.java)
                    val stopped = context.stopService(itn)
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Serviço parado=$stopped"))
                }) { Text("Parar serviço") }
                Button(onClick = {
                    // Força reextração limpa dos modelos
                    try {
                        val enginesDir = java.io.File(context.filesDir, "engines")
                        val ok = enginesDir.deleteRecursively()
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Pasta de modelos apagada=$ok"))
                    } catch (e: Exception) {
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Falha ao apagar modelos: ${e.message}"))
                    }
                    // Reiniciar serviço para extrair de novo
                    val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val itn = Intent(context, ArleteService::class.java).setAction(ArleteService.ACTION_START)
                        ContextCompat.startForegroundService(context, itn)
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Reextração: serviço reiniciado"))
                    } else {
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Reextração: conceda permissão de microfone e reinicie"))
                    }
                }) { Text("Reextrair modelos") }
            }
            logs.forEach { Text(it) }
        }
    }
}

@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
private fun RowButtons(micState: String, onMicTest: () -> Unit, onCheckModels: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onMicTest) { Text("Testar microfone") }
        Button(onClick = onCheckModels) { Text("Checar modelos") }
        Text("Estado Mic: $micState")
    }
}

private suspend fun micTest(context: Context) {
    // Mic test resiliente: tenta diferentes fontes e tamanhos, evita concorrência com outras capturas
    try {
        // Avisar para pausar outras capturas (ASR/wake) durante o teste
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Pausando ASR/Wake para teste de microfone"))
        // Envia um broadcast interno simples (opcional futuramente) — por ora, só informativo

        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Mic teste: permissão de microfone não concedida"))
            return
        }

        val sampleRates = intArrayOf(16000, 44100, 11025, 8000)
        val sources = intArrayOf(
            android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
            android.media.MediaRecorder.AudioSource.MIC,
            android.media.MediaRecorder.AudioSource.CAMCORDER,
            android.media.MediaRecorder.AudioSource.DEFAULT
        )
        val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
        val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT

        var ok = false
        outer@ for (sr in sampleRates) {
            for (src in sources) {
                val minBuf = android.media.AudioRecord.getMinBufferSize(sr, channelConfig, audioFormat)
                if (minBuf <= 0) continue
                val bufSize = (minBuf * 2).coerceAtLeast(2048)
                val buffer = ShortArray(bufSize / 2)
                var rec: android.media.AudioRecord? = null
                try {
                    rec = android.media.AudioRecord(src, sr, channelConfig, audioFormat, bufSize)
                    if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) {
                        continue
                    }
                    rec.startRecording()
                    var collected = 0
                    repeat(10) {
                        val n = rec.read(buffer, 0, buffer.size)
                        if (n > 0) {
                            collected += n
                            var sum = 0.0
                            for (i in 0 until n) { val v = buffer[i].toDouble(); sum += v*v }
                            val rms = Math.sqrt(sum / n)
                            DiagnosticsBus.emit(DiagnosticsBus.Event.MicLevel(rms.toFloat()))
                        }
                    }
                    rec.stop()
                    if (collected > 0) {
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Mic OK (src=$src sr=$sr)"))
                        ok = true
                        break@outer
                    }
                } catch (se: SecurityException) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Mic erro: permissão de microfone negada durante inicialização"))
                    return
                } catch (_: Exception) {
                    // ignora e tenta próxima combinação
                } finally {
                    try { rec?.release() } catch (_: Exception) {}
                }
            }
        }
        if (!ok) DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Mic erro: não foi possível inicializar captura (verifique permissões e uso por outro app)"))
    } catch (e: Exception) {
        DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Mic erro: ${e.message}"))
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