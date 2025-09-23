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

class ArleteService : Service() {
    companion object {
        private const val CHANNEL_ID = "arlete_fg"
        private const val NOTIF_ID = 1001
    }

    private lateinit var wake: WakeWordDetector
    private lateinit var asr: SpeechRecognizer
    private lateinit var conv: ConversationManager
    private lateinit var assistant: Assistant
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAsrRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Arlete")
            .setContentText("Assistente de voz ativo")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(NOTIF_ID, notification)
        // Inicialização básica
        wake = WakeWordDetector()
        asr = SpeechRecognizer()
        conv = ConversationManager()
        // Para a 1ª versão: foco em wake + comandos de voz + LLM local
        assistant = Assistant(this, conv)

        // callbacks
        wake.setOnWakeListener {
            conv.onWakeWord()
            // Quando acordar, iniciamos ASR (simulado)
            asr.start()
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
                    ConversationManager.State.Speaking -> asr.stop()
                    ConversationManager.State.ActiveListening -> asr.start()
                    ConversationManager.State.Idle -> asr.stop()
                }
            }
            pendingAsrRunnable = r
            handler.postDelayed(r, 80)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Iniciar detecção de wake word
        wake.start()
        return START_STICKY
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
}
