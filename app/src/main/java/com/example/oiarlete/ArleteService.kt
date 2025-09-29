package com.example.oiarlete

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.oiarlete.mcp.McpClient
import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import com.example.oiarlete.engine.EngineAssetsProvider
import com.example.oiarlete.engine.EngineAvailability
import com.example.oiarlete.engine.AssetExtractor
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import com.example.oiarlete.diagnostics.DiagnosticsBus

class ArleteService : Service() {
    companion object {
        private const val CHANNEL_ID = "arlete_fg"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.example.oiarlete.ACTION_START"
        const val ACTION_PAUSE_CAPTURE = "com.example.oiarlete.ACTION_PAUSE_CAPTURE"
        const val ACTION_RESUME_CAPTURE = "com.example.oiarlete.ACTION_RESUME_CAPTURE"
    }

    private lateinit var wake: WakeWordDetector
    private lateinit var asr: SpeechRecognizer
    private lateinit var conv: ConversationManager
    private lateinit var assistant: Assistant
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAsrRunnable: Runnable? = null
    private var audioManager: android.media.AudioManager? = null
    private var focusRequested: Boolean = false
    private var previousAudioMode: Int = android.media.AudioManager.MODE_NORMAL

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arlete")
            .setContentText("Assistente de voz ativo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(NOTIF_ID, notification)
        // Inicialização básica (opt-in para engines nativas via flavor) + detecção/extração de assets
        if (BuildConfig.USE_NATIVE_WAKE_ASR) {
            ensureEnginesAreReady()
        }
    wake = WakeWordDetector(this)
        asr = SpeechRecognizer()
        conv = ConversationManager()
        // Para a 1ª versão: foco em wake + comandos de voz + LLM local
        assistant = Assistant(this, conv)

        // callbacks
        wake.setOnWakeListener {
            DiagnosticsBus.emit(DiagnosticsBus.Event.WakeDetected(source = if (wake.isNativeEnabled()) "porcupine" else "stub"))
            conv.onWakeWord()
            // Deixe o ConversationManager acionar ASR via state machine
        }
        asr.setOnResultListener { text ->
            // Ao reconhecer, invocamos Assistant e mudamos estado
            conv.onRecognizedIntent()
            assistant.handleUtterance(text)
        }

        // Pausar/retomar ASR conforme estado da conversa
        conv.setOnStateChangeListener { s ->
            // Debounce leve (80ms) para evitar thrashing start/stop
            pendingAsrRunnable?.let { handler.removeCallbacks(it) }
            val r = Runnable {
                when (s) {
                    ConversationManager.State.Speaking -> {
                        // Durante fala, pare ASR e Wake para liberar o microfone
                        asr.stop(); wake.stop()
                        abandonVoiceFocusSafely()
                    }
                    ConversationManager.State.ActiveListening -> {
                        // Ouvindo ativamente: pare o Wake para não competir com ASR
                        wake.stop()
                        requestVoiceFocusSafely()
                        asr.start()
                    }
                    ConversationManager.State.Idle -> {
                        // Volte a escutar wake; ASR parado
                        asr.stop()
                        abandonVoiceFocusSafely()
                        wake.start()
                    }
                }
            }
            pendingAsrRunnable = r
            handler.postDelayed(r, 80)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> {
                val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasMic) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Permissão de microfone ausente — conceda e reinicie o serviço"))
                } else {
                    // Garante que os assets dos engines existam sempre que o serviço iniciar (após reextração, por exemplo)
                    if (BuildConfig.USE_NATIVE_WAKE_ASR) {
                        ensureEnginesAreReady()
                    }
                    // Apenas logar estado atual; foco/mode serão gerenciados pela state machine
                    try {
                        val am = (audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)).also { audioManager = it }
                        val mode = am.mode
                        val micMute = am.isMicrophoneMute
                        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AudioManager: mode=${mode} micMute=${micMute}"))
                    } catch (_: Exception) {}
                    wake.start()
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Serviço: captura iniciada"))
                }
            }
            ACTION_PAUSE_CAPTURE -> {
                try { wake.stop() } catch (_: Exception) {}
                try { asr.stop() } catch (_: Exception) {}
                // Liberar foco ao pausar
                abandonVoiceFocusSafely()
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Serviço: captura pausada"))
            }
            ACTION_RESUME_CAPTURE -> {
                // Re-solicitar foco ao retomar
                try { wake.start() } catch (_: Exception) {}
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Serviço: captura retomada"))
            }
        }
        return START_STICKY
    }

    private fun ensureEnginesAreReady() {
        val assetsProvider = EngineAssetsProvider(this)
        val porcupineKeyword = try { assetsProvider.findPorcupineKeywordAsset() } catch (_: Exception) { null }
        // Prioridade: SharedPreferences (UI) > secrets.properties
        val uiPref = getSharedPreferences("arlete_prefs", Context.MODE_PRIVATE).getString("porcupine_model_lang", null)
        val preferredModelLang = uiPref ?: try { com.example.oiarlete.SecretsManager.load(this).get("PORCUPINE_MODEL_LANG") } catch (_: Exception) { null }
        val porcupineModel = try { assetsProvider.findPorcupineModelAsset(porcupineKeyword, preferredModelLang) } catch (_: Exception) { null }
        val voskModelDir = try { assetsProvider.findVoskModelDir() } catch (_: Exception) { null }

        EngineAvailability.porcupineAssets = porcupineKeyword != null && porcupineModel != null
        EngineAvailability.voskAssets = voskModelDir != null
        EngineAvailability.porcupineKeywordAssetName = porcupineKeyword
        EngineAvailability.porcupineModelAssetName = porcupineModel
        EngineAvailability.voskModelDirName = voskModelDir

        try {
            val extractor = AssetExtractor(this)
            if (porcupineKeyword != null) {
                EngineAvailability.porcupineKeywordFilePath = extractor.extractFile(porcupineKeyword, targetSubdir = "engines/porcupine")
            }
            if (porcupineModel != null) {
                EngineAvailability.porcupineModelFilePath = extractor.extractFile(porcupineModel, targetSubdir = "engines/porcupine")
            }
            if (voskModelDir != null) {
                EngineAvailability.voskModelDirPath = extractor.extractDirectory(voskModelDir, targetSubdir = "engines/vosk-model")
                // Verificação de integridade básica pós-cópia
                try {
                    val base = java.io.File(EngineAvailability.voskModelDirPath!!)
                    val okDir = base.exists() && base.isDirectory
                    val okFinalMdl = java.io.File(base, "final.mdl").exists()
                    val okHCLr = java.io.File(base, "HCLr.fst").exists()
                    val okGr = java.io.File(base, "Gr.fst").exists()
                    val okIvector = java.io.File(java.io.File(base, "ivector"), "final.dubm").exists()
                    DiagnosticsBus.emit(
                        DiagnosticsBus.Event.Info(
                            "Vosk extraído: path=${EngineAvailability.voskModelDirPath} okDir=${okDir} final.mdl=${okFinalMdl} HCLr.fst=${okHCLr} Gr.fst=${okGr} ivector/final.dubm=${okIvector}"
                        )
                    )
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Extração de engines falhou: ${e.message}"))
        }

        Log.i(
            "Arlete",
            "Engines: porcupineAssets=${EngineAvailability.porcupineAssets} keywordAsset=${EngineAvailability.porcupineKeywordAssetName} keywordPath=${EngineAvailability.porcupineKeywordFilePath}; " +
                    "voskAssets=${EngineAvailability.voskAssets} modelDir=${EngineAvailability.voskModelDirName} modelPath=${EngineAvailability.voskModelDirPath}"
        )
        Log.i(
            "Arlete",
            "Porcupine files: keyword=${EngineAvailability.porcupineKeywordFilePath} model=${EngineAvailability.porcupineModelFilePath}"
        )
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Porcupine keyword: ${EngineAvailability.porcupineKeywordFilePath ?: "(não encontrado)"}"))
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Porcupine model: ${EngineAvailability.porcupineModelFilePath ?: "(não encontrado)"}"))
        DiagnosticsBus.emit(
            DiagnosticsBus.Event.EngineStatus(
                EngineAvailability.porcupineAssets,
                EngineAvailability.voskAssets
            )
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Hooks internos apenas para testes
    internal fun testGetWakeDetector(): WakeWordDetector = wake
    internal fun testGetSpeechRecognizer(): SpeechRecognizer = asr
    internal fun testGetAssistant(): Assistant = assistant
    internal fun stateFlow() = conv.stateFlow

    override fun onDestroy() {
        try { wake.stop() } catch (_: Exception) {}
        try { asr.stop() } catch (_: Exception) {}
        // Limpar recursos do assistant e actions
        try { assistant.cleanup() } catch (_: Exception) {}
        // Restaurar modo e liberar foco
        abandonVoiceFocusSafely()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Arlete Foreground",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun requestVoiceFocusSafely() {
        try {
            val am = (audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)).also { audioManager = it }
            if (!focusRequested) {
                previousAudioMode = am.mode
                am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val afr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener { }
                        .build()
                } else null
                val res = if (afr != null) am.requestAudioFocus(afr) else am.requestAudioFocus(null, android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                focusRequested = (res == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("AudioFocus: requested=${focusRequested}"))
            }
        } catch (_: Exception) {}
    }

    private fun abandonVoiceFocusSafely() {
        try {
            audioManager?.let { am ->
                if (focusRequested) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        am.abandonAudioFocusRequest(
                            android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).build()
                        )
                    } else {
                        am.abandonAudioFocus(null)
                    }
                    focusRequested = false
                }
                am.mode = previousAudioMode
            }
        } catch (_: Exception) {}
    }
}
