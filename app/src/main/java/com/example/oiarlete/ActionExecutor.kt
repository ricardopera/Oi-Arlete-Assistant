package com.example.oiarlete

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class ActionExecutor(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var onTtsComplete: (() -> Unit)? = null
    private var lastSpoken: String? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus: Boolean = false

    fun initTts(onReady: (() -> Unit)? = null) {
        if (tts != null) { onReady?.invoke(); return }
        audioManager = try { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager } catch (_: Exception) { null }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val ok = tts?.setLanguage(Locale.forLanguageTag("en-US"))
                    if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale("en", "US")
                    }
                } catch (_: Exception) {}
                try {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onError(utteranceId: String?) { abandonAudioFocus() }
                        override fun onDone(utteranceId: String?) { abandonAudioFocus(); onTtsComplete?.invoke() }
                    })
                } catch (_: Exception) {}
                onReady?.let { it() }
            }
        }
    }

    fun speak(text: String) {
        lastSpoken = text
        requestAudioFocus()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "arlete-tts")
    }

    fun playYouTubeMusic(query: String) {
        val preferred = Intent(Intent.ACTION_VIEW, Uri.parse("ytmusic://"))
        preferred.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (preferred.resolveActivity(context.packageManager) != null) {
            context.startActivity(preferred)
            return
        }
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query)))
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallback)
    }

    fun setOnTtsComplete(listener: (() -> Unit)?) {
        onTtsComplete = listener
    }

    // Somente para testes
    internal fun testGetLastSpoken(): String? = lastSpoken
    internal fun simulateTtsComplete() { abandonAudioFocus(); onTtsComplete?.invoke() }
    internal fun isAudioFocused(): Boolean = hasFocus

    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            val res = am.requestAudioFocus(req)
            focusRequest = req
            hasFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } catch (_: Exception) { hasFocus = false }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            val req = focusRequest ?: return
            am.abandonAudioFocusRequest(req)
        } catch (_: Exception) { }
        hasFocus = false
        focusRequest = null
    }
}
