package com.example.oiarlete.wake

import android.util.Log
import com.example.oiarlete.diagnostics.DiagnosticsBus
import android.content.Context
import com.example.oiarlete.SecretsManager
import ai.picovoice.porcupine.Porcupine

/**
 * Futuro wrapper para Porcupine.
 * Mantém a mesma API do stub atual: start/stop e setOnWakeListener.
 */
class PorcupineWakeWord(private val context: Context, private val keywordFilePath: String?) {
    private var listener: (() -> Unit)? = null
    private var porcupine: Porcupine? = null
    private var recorder: android.media.AudioRecord? = null
    private var thread: Thread? = null
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    fun setOnWakeListener(l: (() -> Unit)?) { listener = l }
    fun start() {
        try {
            if (running.get()) return
            // Checar permissão antes de tentar inicializar o gravador
            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Porcupine: permissão de microfone ausente"))
                return
            }
            val accessKey = SecretsManager.load(context).getPicovoiceAccessKey()
            if (accessKey.isNullOrBlank()) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("PICOVOICE_ACCESS_KEY ausente"))
                Log.w("Arlete", "Porcupine sem AccessKey — fallback para stub (não iniciando)")
                return
            }
            val modelPath = com.example.oiarlete.engine.EngineAvailability.porcupineModelFilePath
            if (keywordFilePath.isNullOrBlank() || modelPath.isNullOrBlank()) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Porcupine: keyword (.ppn) ou model (.pv) ausente"))
                Log.w("Arlete", "Porcupine: faltando arquivos. keyword=${'$'}keywordFilePath model=${'$'}modelPath")
                return
            }
            // Inicializa Porcupine
            porcupine = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setModelPath(modelPath)
                .setKeywordPaths(arrayOf(keywordFilePath))
                .build(context)

            // Inicializa AudioRecord, preferindo dispositivo USB
            val sampleRates = intArrayOf(16000, 48000, 32000, 44100)
            val channelMono = android.media.AudioFormat.CHANNEL_IN_MONO
            val channelStereo = android.media.AudioFormat.CHANNEL_IN_STEREO
            val format = android.media.AudioFormat.ENCODING_PCM_16BIT
            val prefs = context.getSharedPreferences("arlete_prefs", android.content.Context.MODE_PRIVATE)
            val selId = prefs.getInt("audio_input_id", 0)
            val selected = com.example.oiarlete.audio.AudioInputSelector.findById(context, selId)
            val choice = if (selected != null) com.example.oiarlete.audio.AudioInputSelector.Choice(selected, "UI") else com.example.oiarlete.audio.AudioInputSelector.findPreferredInput(context)
            if (selected == null) {
                com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                    com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Info(
                        "Auto input escolhido (Porcupine): ${'$'}{com.example.oiarlete.audio.AudioInputSelector.describe(choice.device)}"
                    )
                )
            }
            var srChosen = 0
            var chosenChannels = channelMono
            var sourceChosen = android.media.MediaRecorder.AudioSource.DEFAULT
            val sources = buildList<Int> {
                add(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION)
                add(android.media.MediaRecorder.AudioSource.MIC)
                add(android.media.MediaRecorder.AudioSource.CAMCORDER)
                add(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                if (android.os.Build.VERSION.SDK_INT >= 24) add(android.media.MediaRecorder.AudioSource.UNPROCESSED)
                add(android.media.MediaRecorder.AudioSource.DEFAULT)
            }.toIntArray()
            outer@ for (src in sources) {
                for (sr in sampleRates) {
                    for (ch in intArrayOf(channelMono, channelStereo)) {
                        val mb = android.media.AudioRecord.getMinBufferSize(sr, ch, format)
                        if (mb <= 0) continue
                        // garantir buffer suficiente para ao menos um frame completo em 16k
                        val factorTmp = if (sr % 16000 == 0) sr / 16000 else 1
                        val channelsCount = if (ch == channelStereo) 2 else 1
                        val frameLen = porcupine!!.frameLength
                        val needBytes = frameLen * factorTmp * channelsCount * 2 // short->bytes
                        val bufferBytes = kotlin.math.max(mb, needBytes * 2) // headroom

                        val rec = if (android.os.Build.VERSION.SDK_INT >= 23) {
                            try {
                                val fmt = android.media.AudioFormat.Builder()
                                    .setChannelMask(ch)
                                    .setEncoding(format)
                                    .setSampleRate(sr)
                                    .build()
                                android.media.AudioRecord.Builder()
                                    .setAudioSource(src)
                                    .setAudioFormat(fmt)
                                    .setBufferSizeInBytes(bufferBytes)
                                    .build()
                            } catch (_: Exception) {
                                // fallback para ctor clássico
                                android.media.AudioRecord(src, sr, ch, format, bufferBytes)
                            }
                        } else {
                            android.media.AudioRecord(src, sr, ch, format, bufferBytes)
                        }

                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            // Se builder não aceitou device, ainda tentamos preferredDevice
                            try { if (rec.preferredDevice == null) choice.device?.let { rec.preferredDevice = it } } catch (_: Exception) {}
                        }
                        if (rec.state == android.media.AudioRecord.STATE_INITIALIZED) {
                            recorder = rec
                            srChosen = sr
                            chosenChannels = ch
                            sourceChosen = src
                            DiagnosticsBus.emit(
                                DiagnosticsBus.Event.Info(
                                    "Porcupine input: ${com.example.oiarlete.audio.AudioInputSelector.describe(choice.device)} sr=${srChosen} ch=${if (ch==channelStereo) 2 else 1} src=${when(src){
                                        android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
                                        android.media.MediaRecorder.AudioSource.MIC -> "MIC"
                                        android.media.MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
                                        android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                                        android.media.MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
                                        android.media.MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
                                        else -> src.toString()
                                    }}"
                                )
                            )
                            break@outer
                        } else {
                            try { rec.release() } catch (_: Exception) {}
                        }
                    }
                }
            }
            if (recorder == null) throw IllegalStateException("Não foi possível inicializar AudioRecord para Porcupine")
            // Persistir dispositivo ativo selecionado
            try {
                val activeDesc = com.example.oiarlete.audio.AudioInputSelector.describe(choice.device)
                prefs.edit().putInt("audio_input_active_id", choice.device?.id ?: 0).putString("audio_input_active_desc", activeDesc).apply()
            } catch (_: Exception) {}

            // Loop de captura
            running.set(true)
            val frameLen = porcupine!!.frameLength
            thread = Thread {
                try {
                    recorder!!.startRecording()
                    val factorInt = if (srChosen % 16000 == 0) srChosen / 16000 else 0
                    val step = if (factorInt > 0) factorInt.toDouble() else (srChosen.toDouble() / 16000.0)
                    val inBuf = ShortArray((frameLen * kotlin.math.max(1, (if (factorInt>0) factorInt else 3)) * (if (chosenChannels==channelStereo) 2 else 1)))
                    val outBuf = ShortArray(frameLen)
                    // Buffer para reamostragem (caso SR não seja múltiplo de 16k, ex.: 44100)
                    var resampleSrc = ShortArray(16000)
                    var resampleLen = 0
                    var resamplePos = 0.0 // posição (double) no buffer de entrada
                    while (running.get()) {
                        val n = recorder!!.read(inBuf, 0, inBuf.size)
                        if (n > 0) {
                            // Preparar amostras mono
                            val monoTmp: ShortArray
                            val monoCount: Int
                            if (chosenChannels == channelStereo) {
                                val stereoLen = n
                                monoTmp = ShortArray(stereoLen/2)
                                var j = 0; var i = 0
                                while (i + 1 < stereoLen) {
                                    val l = inBuf[i]
                                    val r = inBuf[i+1]
                                    monoTmp[j++] = (((l.toInt() + r.toInt())/2).toShort())
                                    i += 2
                                }
                                monoCount = j
                            } else {
                                monoTmp = inBuf
                                monoCount = n
                            }

                            var produced = 0
                            if (factorInt == 1) {
                                if (monoCount < frameLen) continue
                                System.arraycopy(monoTmp, 0, outBuf, 0, frameLen)
                                produced = frameLen
                            } else if (factorInt == 2 || factorInt == 3) {
                                var j = 0
                                var i = 0
                                val stepInt = factorInt
                                while (i < monoCount && j < frameLen) { outBuf[j++] = monoTmp[i]; i += stepInt }
                                if (j < frameLen) continue
                                produced = j
                            } else {
                                // Reamostragem linear (ex.: 44100 -> 16000)
                                // 1) Append monoTmp ao buffer de entrada acumulado
                                if (resampleSrc.size < resampleLen + monoCount) {
                                    val newSize = kotlin.math.max(resampleSrc.size * 2, resampleLen + monoCount)
                                    val newBuf = ShortArray(newSize)
                                    java.lang.System.arraycopy(resampleSrc, 0, newBuf, 0, resampleLen)
                                    resampleSrc = newBuf
                                }
                                java.lang.System.arraycopy(monoTmp, 0, resampleSrc, resampleLen, monoCount)
                                resampleLen += monoCount
                                // 2) Verificar se há amostras suficientes para gerar um frame completo
                                val needSpan = resamplePos + step * (frameLen - 1)
                                if (needSpan >= resampleLen) continue
                                // 3) Gerar frame com interpolação linear simples
                                var j = 0
                                var pos = resamplePos
                                while (j < frameLen) {
                                    val i0 = pos.toInt()
                                    val i1 = if (i0 + 1 < resampleLen) i0 + 1 else i0
                                    val frac = pos - i0
                                    val s0 = resampleSrc[i0].toInt()
                                    val s1 = resampleSrc[i1].toInt()
                                    val interp = (s0 + ((s1 - s0) * frac)).toInt()
                                    outBuf[j++] = interp.toShort()
                                    pos += step
                                }
                                produced = frameLen
                                // 4) Descartar amostras já consumidas do buffer acumulado
                                val drop = pos.toInt()
                                val remain = resampleLen - drop
                                if (remain > 0) java.lang.System.arraycopy(resampleSrc, drop, resampleSrc, 0, remain)
                                resampleLen = remain
                                resamplePos = pos - drop
                            }
                            if (produced < frameLen) continue
                            val res = porcupine!!.process(outBuf)
                            if (res >= 0) {
                                DiagnosticsBus.emit(DiagnosticsBus.Event.WakeDetected("porcupine"))
                                listener?.invoke()
                            }
                        }
                    }
                } catch (e: Exception) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Porcupine erro: ${'$'}{e.message}"))
                    Log.e("Arlete", "Porcupine loop failed", e)
                } finally {
                    try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
                }
            }
            thread?.start()
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Porcupine iniciado"))
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("Porcupine erro: ${'$'}{e.message}"))
            Log.e("Arlete", "Porcupine init/start failed", e)
        }
    }
    fun stop() {
        try {
            running.set(false)
            try { thread?.join(300) } catch (_: Exception) {}
            try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
            try { porcupine?.delete() } catch (_: Exception) {}
        } catch (_: Exception) { }
        finally {
            porcupine = null
            recorder = null
            thread = null
        }
    }
}
