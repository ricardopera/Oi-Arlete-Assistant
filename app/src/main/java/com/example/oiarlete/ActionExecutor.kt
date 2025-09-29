package com.example.oiarlete

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import com.example.oiarlete.diagnostics.DiagnosticsBus
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.buildList

private fun buildSearchExtras(query: String): Bundle = Bundle().apply {
    putString(SearchManager.QUERY, query)
    putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
    putString(MediaStore.EXTRA_MEDIA_TITLE, query)
    putString(MediaStore.EXTRA_MEDIA_ARTIST, query)
    putString(MediaStore.EXTRA_MEDIA_ALBUM, query)
}

open class ActionExecutor(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var onTtsComplete: (() -> Unit)? = null
    private var lastSpoken: String? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val youtubeMusicPackages = listOf(
        "com.google.android.youtube.tvmusic",
        "com.google.android.apps.youtube.music"
    )

    private val spotifyPackages = listOf(
        "com.spotify.tv.android",
        "com.spotify.music"
    )

    private val amazonPackages = listOf(
        "com.amazon.music",
        "com.amazon.mp3",
        "com.amazon.music.tv",
        "com.amazon.music.android",
        "com.amazon.music.google"
    )

    open fun initTts(onReady: (() -> Unit)? = null) {
        if (tts != null) { onReady?.invoke(); return }
        audioManager = try { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager } catch (_: Exception) { null }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val langResult = tts?.setLanguage(Locale.forLanguageTag("pt-BR"))
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale("pt", "BR")
                    }
                } catch (_: Exception) {}
                try {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onError(utteranceId: String?) { abandonAudioFocus() }
                        override fun onDone(utteranceId: String?) { abandonAudioFocus(); onTtsComplete?.invoke() }
                    })
                } catch (_: Exception) {}
                onReady?.invoke()
            }
        }
    }

    fun setOnTtsComplete(listener: (() -> Unit)?) {
        onTtsComplete = listener
    }

    fun cleanup() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        abandonAudioFocus()
    }

    fun speak(text: String) {
        lastSpoken = text
        requestAudioFocus()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "arlete-tts")
    }

    open fun pauseMusic(): Boolean {
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: solicitando pausa"))
        if (MediaControlService.pauseActiveSessions()) {
            return true
        }
        if (controlViaMediaBrowser(spotifyPackages, "Spotify", "pausa") { controls ->
                controls.pause()
            }) {
            return true
        }
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: pausa não executada"))
        return false
    }

    open fun stopMusic(): Boolean {
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: solicitando stop"))
        if (MediaControlService.stopActiveSessions()) {
            return true
        }
        if (controlViaMediaBrowser(spotifyPackages, "Spotify", "stop") { controls ->
                controls.stop()
                controls.pause()
            }) {
            return true
        }
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: stop não executado"))
        return false
    }

    fun playYouTubeMusic(query: String) {
        val encodedQuery = Uri.encode(query)
        val serviceName = "YouTube Music"

        val attempts = buildList<Pair<String, () -> Intent?>> {
            add("Deep link ytmusic base" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("ytmusic://music/search")
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            youtubeMusicPackages.forEach { pkg ->
                add("Deep link ytmusic base ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("ytmusic://music/search")
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Deep link ytmusic search" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("ytmusic://music/search?query=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            youtubeMusicPackages.forEach { pkg ->
                add("Deep link ytmusic search ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("ytmusic://music/search?query=$encodedQuery")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Deep link vnd.youtube.music" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("vnd.youtube.music://now?search=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            youtubeMusicPackages.forEach { pkg ->
                add("Deep link vnd.youtube.music ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("vnd.youtube.music://now?search=$encodedQuery")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Deep link vnd.youtube search" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("vnd.youtube://music/search?query=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            youtubeMusicPackages.forEach { pkg ->
                add("Deep link vnd.youtube search ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("vnd.youtube://music/search?query=$encodedQuery")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("MediaStore padrão" to {
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtras(buildSearchExtras(query))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            youtubeMusicPackages.forEach { pkg ->
                add("MediaStore pacote $pkg" to {
                    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage(pkg)
                        putExtras(buildSearchExtras(query))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            youtubeMusicPackages.forEach { pkg ->
                add("Action SEARCH ($pkg)" to {
                    Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Deep link https music" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://music.youtube.com/search?q=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            youtubeMusicPackages.forEach { pkg ->
                add("Deep link https music ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://music.youtube.com/search?q=$encodedQuery")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Deep link https youtube" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAQ%253D%253D")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }

        try {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: iniciando busca por '$query'"))

            if (MediaControlService.playFromSearch(youtubeMusicPackages, query, serviceName) { buildSearchExtras(query) }) {
                return
            }

            for ((label, builder) in attempts) {
                val intent = try { builder() } catch (e: Exception) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: erro ao criar intent $label - ${e.message}"))
                    null
                }
                if (intent != null && tryStartActivity(intent, label, serviceName)) return
            }

            val launchIntent = youtubeMusicPackages.asSequence()
                .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
                .firstOrNull()
            if (launchIntent != null) {
                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: nenhum intent reproduziu; app aberto para busca manual de '$query'"))
            } else {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("$serviceName: pacote não encontrado"))
            }

        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("$serviceName: erro na busca musical - ${e.message}"))
        }
    }

    fun playAmazonMusic(query: String) {
        val encodedQuery = Uri.encode(query)
        val serviceName = "Amazon Music"

        val attempts = buildList<Pair<String, () -> Intent?>> {
            add("Deep link amazonmusic search" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("amazonmusic://music/search/$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            add("Deep link amazonmusic search (now)" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("amazonmusic://nowPlaying?search=$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            add("Deep link amznmusic search" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("amznmusic://music/search/$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            amazonPackages.forEach { pkg ->
                add("Deep link amazonmusic search ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("amazonmusic://music/search/$encodedQuery")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Deep link https Amazon Music" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://music.amazon.com/search/$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            amazonPackages.forEach { pkg ->
                add("HTTPS Amazon Music ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://music.amazon.com/search/$encodedQuery")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("MediaStore padrão" to {
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtras(buildSearchExtras(query))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            amazonPackages.forEach { pkg ->
                add("MediaStore pacote $pkg" to {
                    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage(pkg)
                        putExtras(buildSearchExtras(query))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            amazonPackages.forEach { pkg ->
                add("Action SEARCH pacote $pkg" to {
                    Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
        }

        try {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: iniciando busca por '$query'"))

            if (MediaControlService.playFromSearch(amazonPackages, query, serviceName) { buildSearchExtras(query) }) {
                return
            }

            if (tryPlayViaMediaBrowser(amazonPackages, query, serviceName)) return

            for ((label, builder) in attempts) {
                val intent = try { builder() } catch (e: Exception) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: erro ao criar intent $label - ${e.message}"))
                    null
                }
                if (intent != null && tryStartActivity(intent, label, serviceName)) return
            }

            val launchIntent = amazonPackages.asSequence()
                .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
                .firstOrNull()

            if (launchIntent != null) {
                AmazonMusicAutomationService.requestPlayback(query)
                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: nenhum intent reproduziu; app aberto para busca manual de '$query'"))
            } else {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("$serviceName: nenhum pacote compatível encontrado"))
            }

        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("$serviceName: erro na busca musical - ${e.message}"))
        }
    }

    fun playSpotifyMusic(query: String) {
        val encodedQuery = Uri.encode(query)
        val serviceName = "Spotify"

        val attempts = buildList<Pair<String, () -> Intent?>> {
            add("Deep link spotify search" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("spotify:search:$encodedQuery")
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            spotifyPackages.forEach { pkg ->
                add("Deep link spotify search ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("spotify:search:$encodedQuery")
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("MediaStore padrão" to {
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtras(buildSearchExtras(query))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            spotifyPackages.forEach { pkg ->
                add("MediaStore pacote $pkg" to {
                    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage(pkg)
                        putExtras(buildSearchExtras(query))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Action SEARCH" to {
                Intent(Intent.ACTION_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            spotifyPackages.forEach { pkg ->
                add("Action SEARCH ($pkg)" to {
                    Intent(Intent.ACTION_SEARCH).apply {
                        setPackage(pkg)
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
            add("Deep link https open" to {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://open.spotify.com/search/$encodedQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
            spotifyPackages.forEach { pkg ->
                add("Deep link https open ($pkg)" to {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://open.spotify.com/search/$encodedQuery")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            }
        }

        try {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: iniciando busca por '$query'"))

            if (MediaControlService.playFromSearch(spotifyPackages, query, serviceName) { buildSearchExtras(query) }) {
                return
            }

            if (tryPlayViaMediaBrowser(spotifyPackages, query, serviceName)) return

            for ((label, builder) in attempts) {
                val intent = try { builder() } catch (e: Exception) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: erro ao criar intent $label - ${e.message}"))
                    null
                }
                if (intent != null && tryStartActivity(intent, label, serviceName)) return
            }

            val launchIntent = spotifyPackages.asSequence()
                .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
                .firstOrNull()

            if (launchIntent != null) {
                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: nenhum intent reproduziu; app aberto para busca manual de '$query'"))
            } else {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Error("$serviceName: nenhum pacote compatível encontrado"))
            }

        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Error("$serviceName: erro na busca musical - ${e.message}"))
        }
    }

    private fun tryStartActivity(intent: Intent, label: String, serviceName: String): Boolean {
        val pm = context.packageManager
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: erro ao resolver intent '$label' - ${e.message}"))
            null
        }

        if (resolved == null) {
            val scheme = intent.data?.scheme?.lowercase(Locale.getDefault())
            if (scheme != "http" && scheme != "https") {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: intent '$label' não resolvido"))
                return false
            }
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: intent '$label' sem handler explícito; tentando mesmo assim"))
        }

        return try {
            context.startActivity(intent)
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: intent '$label' disparado"))
            true
        } catch (e: SecurityException) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: permissão negada ao iniciar '$label' - ${e.message}"))
            false
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: falha ao iniciar '$label' - ${e.message}"))
            false
        }
    }

    private fun tryPlayViaMediaBrowser(
        packageNames: List<String>,
        query: String,
        serviceName: String
    ): Boolean {
        val components = resolveMediaBrowserServices(packageNames)
        if (components.isEmpty()) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: nenhum MediaBrowserService encontrado"))
            return false
        }

        components.forEach { component ->
            val componentId = component.flattenToShortString()
            val executed = runWithMediaBrowser(component) { controls ->
                controls.playFromSearch(query, buildSearchExtras(query))
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: playFromSearch via MediaBrowser ($componentId)"))
            }
            if (executed) {
                return true
            }
        }

        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: MediaBrowser não executou playFromSearch"))
        return false
    }

    private fun controlViaMediaBrowser(
        packageNames: List<String>,
        serviceName: String,
        action: String,
        command: (MediaControllerCompat.TransportControls) -> Unit
    ): Boolean {
        val components = resolveMediaBrowserServices(packageNames)
        if (components.isEmpty()) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: nenhum MediaBrowserService disponível para $action"))
            return false
        }

        components.forEach { component ->
            val componentId = component.flattenToShortString()
            val executed = runWithMediaBrowser(component) { controls ->
                command(controls)
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: $action via MediaBrowser ($componentId)"))
            }
            if (executed) {
                return true
            }
        }

        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: MediaBrowser não aceitou $action"))
        return false
    }

    private fun runWithMediaBrowser(
        component: ComponentName,
        block: (MediaControllerCompat.TransportControls) -> Unit
    ): Boolean {
        val executed = AtomicBoolean(false)
        val completion = CountDownLatch(1)
        lateinit var browser: MediaBrowserCompat
        browser = MediaBrowserCompat(context, component, object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                try {
                    val controller = MediaControllerCompat(context, browser.sessionToken)
                    block(controller.transportControls)
                    executed.set(true)
                } catch (e: Exception) {
                    DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser (${component.flattenToShortString()}): erro ao executar comando - ${e.message}"))
                } finally {
                    completion.countDown()
                }
            }

            override fun onConnectionFailed() {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser (${component.flattenToShortString()}): conexão falhou"))
                completion.countDown()
            }

            override fun onConnectionSuspended() {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser (${component.flattenToShortString()}): conexão suspensa"))
                completion.countDown()
            }
        }, null)

        connectAndAwait(browser, component, completion)
        disconnectSafely(browser)
        return executed.get()
    }

    private fun connectAndAwait(
        browser: MediaBrowserCompat,
        component: ComponentName,
        completion: CountDownLatch
    ): Boolean {
        val connectLatch = CountDownLatch(1)
        val componentId = component.flattenToShortString()
        runOnMainThread {
            try {
                browser.connect()
            } catch (e: Exception) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser: erro ao conectar ($componentId) - ${e.message}"))
                completion.countDown()
            } finally {
                connectLatch.countDown()
            }
        }

        connectLatch.await(500, TimeUnit.MILLISECONDS)
        val finished = completion.await(1500, TimeUnit.MILLISECONDS)
        if (!finished) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser: tempo limite aguardando resposta ($componentId)"))
        }
        return finished
    }

    private fun disconnectSafely(browser: MediaBrowserCompat) {
        runOnMainThread {
            try {
                if (browser.isConnected) {
                    browser.disconnect()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveMediaBrowserServices(packageNames: List<String>): List<ComponentName> {
        val pm = context.packageManager
        val components = mutableListOf<ComponentName>()
        packageNames.forEach { pkg ->
            val appInstalled = try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser: pacote $pkg não instalado"))
                false
            } catch (e: Exception) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser: falha ao verificar pacote $pkg - ${e.message}"))
                false
            }
            if (!appInstalled) {
                return@forEach
            }
            val intent = Intent("android.media.browse.MediaBrowserService").setPackage(pkg)
            val services = try {
                queryMediaBrowserServices(pm, intent)
            } catch (e: Exception) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaBrowser: erro ao consultar serviços para $pkg - ${e.message}"))
                emptyList()
            }
            services.forEach { info ->
                val serviceInfo = info.serviceInfo ?: return@forEach
                components.add(ComponentName(serviceInfo.packageName, serviceInfo.name))
            }
        }
        return components.distinct()
    }

    private fun queryMediaBrowserServices(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, PackageManager.GET_META_DATA) ?: emptyList()
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            val latch = CountDownLatch(1)
            mainHandler.post {
                try {
                    block()
                } finally {
                    latch.countDown()
                }
            }
            latch.await(500, TimeUnit.MILLISECONDS)
        }
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
