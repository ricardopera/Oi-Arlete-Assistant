package com.example.oiarlete.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.oiarlete.diagnostics.DiagnosticsBus
import java.util.Locale

/**
 * Wrapper para SpeechRecognizer nativo do Android.
 * Mais estável que soluções que usam AudioRecord manual.
 */
class AndroidSpeechRecognizer(private val context: Context) {
    private var listener: ((String) -> Unit)? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var running = false

    fun setOnResultListener(l: ((String) -> Unit)?) { 
        listener = l 
    }

    fun start() {
        if (running) return
        
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("SpeechRecognizer não disponível neste dispositivo"))
                return
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR: pronto para ouvir"))
                }

                override fun onBeginningOfSpeech() {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR: detectou início da fala"))
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Opcional: emitir nível de áudio
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR: fim da fala detectado"))
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de rede"
                        SpeechRecognizer.ERROR_NETWORK -> "Erro de rede"
                        SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio"
                        SpeechRecognizer.ERROR_SERVER -> "Erro do servidor"
                        SpeechRecognizer.ERROR_CLIENT -> "Erro do cliente"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout - sem fala detectada"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma correspondência encontrada"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissões insuficientes"
                        else -> "Erro desconhecido ($error)"
                    }
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("ASR erro: $errorMsg"))
                    running = false
                }

                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        if (matches.isNotEmpty()) {
                            val result = matches[0]
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR resultado: \"$result\""))
                            DiagnosticsBus.emit(DiagnosticsBus.Event.SpeechResult(result))
                            listener?.invoke(result)
                        }
                    }
                    running = false
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        if (matches.isNotEmpty()) {
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR parcial: \"${matches[0]}\""))
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            }

            running = true
            speechRecognizer?.startListening(intent)
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR Android: iniciado"))

        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("ASR init erro: ${e.message}"))
            running = false
        }
    }

    fun stop() {
        if (!running) return
        
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            running = false
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("ASR Android: parado"))
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("ASR stop erro: ${e.message}"))
        }
    }

    fun isRunning(): Boolean = running
}