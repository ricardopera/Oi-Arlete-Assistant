package com.example.oiarlete.asr

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.oiarlete.diagnostics.DiagnosticsBus
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura áudio após wake word e envia para API de reconhecimento via streaming.
 */
class StreamingApiRecognizer(private val context: Context) {
    private var listener: ((String) -> Unit)? = null
    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private val running = AtomicBoolean(false)
    
    // Configurações de áudio
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // API configuration - pode ser configurada via SharedPreferences
    private var apiEndpoint = "https://speech.googleapis.com/v1/speech:recognize" // Exemplo
    private var apiKey = "" // Deve ser configurado
    
    fun setOnResultListener(l: ((String) -> Unit)?) { 
        listener = l 
    }

    fun start() {
        if (running.get()) return
        
        try {
            // Configurar API key das preferências
            val prefs = context.getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE)
            apiKey = prefs.getString("speech_api_key", "") ?: ""
            apiEndpoint = prefs.getString("speech_api_endpoint", apiEndpoint) ?: apiEndpoint
            
            if (apiKey.isEmpty()) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("API key não configurada"))
                return
            }

            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Streaming ASR: permissão de microfone não concedida"))
                return
            }

            // Inicializar AudioRecord similar ao Porcupine
            val sources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )
            
            var minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBuf <= 0) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Streaming ASR: AudioRecord não suportado"))
                return
            }
            
            val bufferSize = (minBuf * 2).coerceAtLeast(4096)
            
            // Tentar diferentes fontes de áudio
            for (source in sources) {
                try {
                    recorder = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                    if (recorder?.state == AudioRecord.STATE_INITIALIZED) {
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: AudioRecord inicializado (source=$source)"))
                        break
                    } else {
                        recorder?.release()
                        recorder = null
                    }
                } catch (se: SecurityException) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Streaming ASR: permissão de microfone negada durante inicialização"))
                    recorder?.release()
                    recorder = null
                    return
                } catch (e: Exception) {
                    recorder?.release()
                    recorder = null
                }
            }
            
            if (recorder == null) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Streaming ASR: Falha ao inicializar AudioRecord"))
                return
            }

            running.set(true)
            startRecordingAndStreaming()
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: iniciado"))

        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Streaming ASR init erro: ${e.message}"))
            running.set(false)
        }
    }

    private fun startRecordingAndStreaming() {
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                recorder?.startRecording()
                
                val audioBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                val maxDurationMs = 5000 // 5 segundos máximo
                val startTime = System.currentTimeMillis()
                
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: gravando..."))
                
                while (running.get() && 
                       (System.currentTimeMillis() - startTime) < maxDurationMs &&
                       isActive) {
                    
                    val bytesRead = recorder?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        audioBuffer.write(buffer, 0, bytesRead)
                    }
                    
                    delay(50) // Small delay to prevent busy waiting
                }
                
                recorder?.stop()
                
                // Enviar áudio capturado para API
                if (audioBuffer.size() > 0) {
                    sendAudioToApi(audioBuffer.toByteArray())
                } else {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: nenhum áudio capturado"))
                }
                
            } catch (e: Exception) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Streaming ASR recording erro: ${e.message}"))
            } finally {
                running.set(false)
                cleanup()
            }
        }
    }
    
    private suspend fun sendAudioToApi(audioData: ByteArray) {
        try {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: enviando ${audioData.size} bytes para API"))
            
            // Exemplo para Google Speech-to-Text API
            val requestBody = createGoogleSpeechRequest(audioData)
            
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url("$apiEndpoint?key=$apiKey")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    parseApiResponse(responseBody)
                } else {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("API erro: ${response.code} - ${responseBody ?: "sem resposta"}"))
                }
            }
            
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Streaming ASR API erro: ${e.message}"))
        }
    }
    
    private fun createGoogleSpeechRequest(audioData: ByteArray): RequestBody {
        // Converter PCM para base64
        val audioBase64 = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
        
        // Criar JSON request para Google Speech API
        val json = """
        {
            "config": {
                "encoding": "LINEAR16",
                "sampleRateHertz": $sampleRate,
                "languageCode": "pt-BR",
                "enableAutomaticPunctuation": true
            },
            "audio": {
                "content": "$audioBase64"
            }
        }
        """.trimIndent()
        
        return json.toRequestBody("application/json".toMediaType())
    }
    
    private fun parseApiResponse(responseBody: String) {
        try {
            // Parse simples do JSON response do Google Speech API
            // Em produção, usar biblioteca JSON como Gson ou kotlinx.serialization
            
            if (responseBody.contains("\"transcript\"")) {
                // Extrair transcript usando regex simples
                val transcriptRegex = """"transcript":\s*"([^"]+)"""".toRegex()
                val match = transcriptRegex.find(responseBody)
                
                if (match != null) {
                    val transcript = match.groupValues[1]
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR resultado: \"$transcript\""))
                    DiagnosticsBus.emit(DiagnosticsBus.Event.SpeechResult(transcript))
                    listener?.invoke(transcript)
                } else {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: resposta sem transcript"))
                }
            } else {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: sem reconhecimento na resposta"))
            }
            
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Parse response erro: ${e.message}"))
        }
    }

    fun stop() {
        if (!running.get()) return
        
        running.set(false)
        recordingJob?.cancel()
        cleanup()
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Streaming ASR: parado"))
    }
    
    private fun cleanup() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    fun isRunning(): Boolean = running.get()
}