package com.example.oiarlete.asr

import android.util.Log
import com.example.oiarlete.diagnostics.DiagnosticsBus
// Vosk via AAR local

/**
 * Futuro wrapper para Vosk/Whisper.
 * Deve alinhar-se com a API de SpeechRecognizer: start/stop, setOnResultListener.
 */
class VoskRecognizer(private val modelDirPath: String?) {
    private var listener: ((String) -> Unit)? = null
    private var model: org.vosk.Model? = null
    private var recognizer: org.vosk.Recognizer? = null
    private var thread: Thread? = null
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    fun setOnResultListener(l: ((String) -> Unit)?) { listener = l }
    fun start() {
        if (running.get()) return
        try {
            // Tentar carregar biblioteca nativa explicitamente para obter erros claros se faltar ABI
            try { java.lang.System.loadLibrary("vosk") } catch (_: UnsatisfiedLinkError) { }
            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                com.example.oiarlete.AppGlobals.get(), android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Vosk: permissão de microfone ausente"))
                return
            }
            if (model == null) {
                if (modelDirPath.isNullOrBlank()) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Vosk: modelDirPath ausente"))
                    return
                }
                // Validar estrutura mínima do modelo no filesystem
                val f = java.io.File(modelDirPath)
                val okDir = f.exists() && f.isDirectory
                val okFiles = java.io.File(f, "final.mdl").exists() && java.io.File(f, "HCLr.fst").exists() && java.io.File(f, "Gr.fst").exists()
                val okIvector = java.io.File(java.io.File(f, "ivector"), "final.dubm").exists() || java.io.File(java.io.File(f, "ivector"), "final.ie").exists()
                if (!okDir || !okFiles || !okIvector) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Vosk: modelo incompleto em ${modelDirPath} (okDir=${okDir} okFiles=${okFiles} okIvector=${okIvector})"))
                    try {
                        val list = f.listFiles()?.map { (if (it.isDirectory) "[D]" else "[F]") + it.name }?.sorted()?.take(50).orEmpty()
                        if (list.isEmpty()) {
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Vosk: dir vazio ou inacessível: ${modelDirPath}"))
                        } else {
                            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Vosk: conteúdo do modelo: ${list.joinToString(", ")}"))
                        }
                    } catch (_: Exception) { }
                    return
                }
                try {
                    model = org.vosk.Model(modelDirPath)
                } catch (e: Exception) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Vosk: falha ao criar Model em ${modelDirPath}: ${e.message}"))
                    throw e
                }
            }
            val sampleRates = intArrayOf(16000, 48000, 44100, 32000)
            var chosenSr = 16000
            var chosenChannels = android.media.AudioFormat.CHANNEL_IN_MONO
            recognizer = org.vosk.Recognizer(model, chosenSr.toFloat())
            running.set(true)
            thread = Thread {
                try {
                    val channelMono = android.media.AudioFormat.CHANNEL_IN_MONO
                    val channelStereo = android.media.AudioFormat.CHANNEL_IN_STEREO
                    val format = android.media.AudioFormat.ENCODING_PCM_16BIT
                    var recorder: android.media.AudioRecord? = null
                    var minBuf = 0
                    var selectedDeviceDesc = ""
                    var chosenSource = android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
                    // Escolhe input USB se disponível
                    val ctx = com.example.oiarlete.AppGlobals.get()
                    val prefs = ctx.getSharedPreferences("arlete_prefs", android.content.Context.MODE_PRIVATE)
                    val selId = prefs.getInt("audio_input_id", 0)
                    val selected = com.example.oiarlete.audio.AudioInputSelector.findById(ctx, selId)
                    val choice = if (selected != null) com.example.oiarlete.audio.AudioInputSelector.Choice(selected, "UI") else com.example.oiarlete.audio.AudioInputSelector.findPreferredInput(ctx)
                    if (selected == null) {
                        com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                            com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Info(
                                "Auto input escolhido (Vosk): ${'$'}{com.example.oiarlete.audio.AudioInputSelector.describe(choice.device)}"
                            )
                        )
                    }
                    val sources = buildList {
                        add(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
                        add(android.media.MediaRecorder.AudioSource.MIC)
                        if (android.os.Build.VERSION.SDK_INT >= 24) add(android.media.MediaRecorder.AudioSource.UNPROCESSED)
                        add(android.media.MediaRecorder.AudioSource.DEFAULT)
                    }.toIntArray()
                    outer@ for (src in sources) {
                        for (sr in sampleRates) {
                            for (ch in intArrayOf(channelMono, channelStereo)) {
                                val mb = android.media.AudioRecord.getMinBufferSize(sr, ch, format)
                                if (mb <= 0) continue
                                val bufSize = (mb * 3).coerceAtLeast(mb + 1024)
                                val rec = android.media.AudioRecord(
                                    src,
                                    sr,
                                    ch,
                                    format,
                                    bufSize
                                )
                                try {
                                    if (android.os.Build.VERSION.SDK_INT >= 23) {
                                        choice.device?.let { rec.preferredDevice = it }
                                    }
                                    if (rec.state == android.media.AudioRecord.STATE_INITIALIZED) {
                                        recorder = rec
                                        chosenSr = sr
                                        chosenChannels = ch
                                        minBuf = bufSize
                                        chosenSource = src
                                        selectedDeviceDesc = com.example.oiarlete.audio.AudioInputSelector.describe(choice.device)
                                        break@outer
                                    } else {
                                        rec.release()
                                    }
                                } catch (_: Exception) {
                                    try { rec.release() } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                    if (recorder == null) throw IllegalStateException("Não foi possível inicializar AudioRecord em nenhum SR")
                    recognizer = org.vosk.Recognizer(model, chosenSr.toFloat())
                    recorder!!.startRecording()
                    // Persistir dispositivo ativo selecionado
                    prefs.edit().putInt("audio_input_active_id", choice.device?.id ?: 0).putString("audio_input_active_desc", selectedDeviceDesc).apply()
                    com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                        com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Info(
                            "Vosk gravando (src=${'$'}{sourceName(chosenSource)} sr=${'$'}chosenSr ch=${if (chosenChannels==channelStereo) 2 else 1} dev=${'$'}selectedDeviceDesc)"
                        )
                    )
                    val buf = ByteArray(minBuf)
                    val monoBuf = ByteArray(minBuf) // suficiente; usaremos metade se estéreo
                    while (running.get()) {
                        val n = recorder!!.read(buf, 0, buf.size)
                        if (n > 0) {
                            val data: ByteArray
                            val len: Int
                            if (chosenChannels == channelStereo) {
                                // downmix estéreo -> mono
                                val samples = n / 2 // 2 bytes per sample x 2 channels interleaved
                                var outIdx = 0
                                var i = 0
                                while (i + 3 < n) {
                                    val l = (buf[i].toInt() and 0xFF) or (buf[i+1].toInt() shl 8)
                                    val r = (buf[i+2].toInt() and 0xFF) or (buf[i+3].toInt() shl 8)
                                    val avg = ((l + r) / 2).toShort()
                                    monoBuf[outIdx] = (avg.toInt() and 0xFF).toByte()
                                    monoBuf[outIdx + 1] = ((avg.toInt() ushr 8) and 0xFF).toByte()
                                    outIdx += 2
                                    i += 4
                                }
                                data = monoBuf
                                len = outIdx
                            } else {
                                data = buf
                                len = n
                            }
                            val rec = recognizer
                            if (rec != null) {
                                val hasRes = rec.acceptWaveForm(data, len)
                                if (hasRes) {
                                    val res = rec.result
                                    listener?.invoke(res)
                                }
                            }
                        }
                    }
                    recorder!!.stop(); recorder!!.release()
                } catch (e: Exception) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Vosk erro: ${'$'}{e.message}"))
                    android.util.Log.e("Arlete", "Vosk thread failed", e)
                    running.set(false)
                }
            }
            thread?.start()
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Vosk init erro: ${'$'}{e.message}"))
            android.util.Log.e("Arlete", "Vosk init/start failed", e)
        }
    }
    fun stop() {
        running.set(false)
        try { thread?.join(500) } catch (_: Exception) {}
        try { recognizer?.close() } catch (_: Exception) {}
        recognizer = null
    }

    private fun sourceName(src: Int): String = when (src) {
        android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        android.media.MediaRecorder.AudioSource.MIC -> "MIC"
        android.media.MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        android.media.MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        android.media.MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
        else -> src.toString()
    }
}
