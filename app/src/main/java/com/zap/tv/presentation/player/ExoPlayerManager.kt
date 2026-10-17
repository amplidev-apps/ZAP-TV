package com.zap.tv.presentation.player

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.zap.tv.BuildConfig
import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.util.PlaybackQualityProfile
import com.zap.tv.util.PlaybackState
import com.zap.tv.util.PlaybackStates
import com.zap.tv.util.QualityPolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import android.util.Log

class ExoPlayerManager(private val ctx: Context) {
    var player: ExoPlayer? = null
        private set

    private var streams: List<ApiStreamDto> = emptyList()
    private var currentStreamIndex: Int = 0
    private var onErrorCallback: (() -> Unit)? = null
    private var playerListener: Player.Listener? = null
    private var autoRetryRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var bufferingStartMs: Long = 0L
    private var lastObservedPosition: Long = -1L
    private var positionStallStartMs: Long = 0L
    var onReconnecting: ((Boolean) -> Unit)? = null

    // Etapa 10: estado de reprodução + qualidade adaptativa (ABR nativo do Media3).
    private var trackSelector: DefaultTrackSelector? = null
    private var qualityProfile: PlaybackQualityProfile = PlaybackQualityProfile.AUTO
    private var hasVideoTrack: Boolean = false
    private var renderedFirstFrame: Boolean = false
    var onStateChanged: ((PlaybackState) -> Unit)? = null

    // Etapa 15 §03/§04: áudio sem imagem não é reprodução validada. Quando a trilha de
    // vídeo é declarada mas o primeiro quadro não renderiza dentro da janela, o
    // ViewModel decide (via VideoFallbackPolicy) se tenta outra fonte autorizada.
    var onVideoUnavailable: (() -> Unit)? = null

    // Etapa 14 §01: se uma trilha de vídeo é declarada (READY) mas nenhum quadro é
    // renderizado dentro desta janela, o canal é tratado como áudio-sem-imagem —
    // nunca como PLAYING (evita tela preta com áudio sem qualquer indicação).
    private var videoFrameTimedOut: Boolean = false
    private var firstFrameTimeoutRunnable: Runnable? = null
    private var videoFallbackRunnable: Runnable? = null

    // Etapa 11 §05: repetições transitórias antes de escalar a falha da fonte.
    // Evita troca prematura de região por instabilidade momentânea.
    private var transientRetries: Int = 0

    // Etapa 20 §11/§12: reconexões do watchdog (buffering/travamento) para a MESMA
    // fonte. Sem este contador o watchdog reconectaria a mesma fonte para sempre e o
    // canal ficaria preso (ex.: stream que conecta e nunca envia dados — Band/iptv-org).
    private var watchdogReconnects: Int = 0

    private companion object {
        const val MAX_TRANSIENT_RETRIES = 2
        const val RETRY_DELAY_MS = 2_500L
        // Tempo para declarar "áudio sem imagem" na UI (indicador).
        const val FIRST_FRAME_TIMEOUT_MS = 3_500L
        // Tempo (mais tolerante, "respeitando conexões lentas") antes de escalar a
        // falha de vídeo e tentar outra fonte autorizada.
        const val VIDEO_FALLBACK_MS = 8_000L
        // Após N reconexões do watchdog sem progresso, ESCALAR (próxima stream/fonte)
        // em vez de reconectar a mesma fonte indefinidamente (Etapa 20 §12).
        const val MAX_WATCHDOG_RECONNECTS = 2
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun build(): ExoPlayer {
        if (player != null) return player!!
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,  // minBufferMs: reconexão rápida (não eternidade)
                60_000, // maxBufferMs: 1 min
                1_200,   // bufferForPlaybackMs
                2_000    // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()
        val renderersFactory = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)
        val defaultDsFactory = createDataSourceFactory(null, null)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDsFactory, extractorsFactory)
        val selector = DefaultTrackSelector(ctx).apply { setParameters(buildTrackParameters(qualityProfile)) }
        trackSelector = selector
        player = ExoPlayer.Builder(ctx, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)
            }
        return player!!
    }

    /**
     * Parâmetros do seletor ADAPTATIVO nativo. O ABR do Media3 continua responsável
     * pela inicialização conservadora e pelo upswitch progressivo; aqui apenas
     * limitamos o teto conforme o perfil (Etapa 10 §08/§10). Nunca força uma
     * variante fixa nem transcodifica.
     */
    private fun buildTrackParameters(profile: PlaybackQualityProfile): DefaultTrackSelector.Parameters {
        val caps = QualityPolicy.capsFor(profile)
        val builder = DefaultTrackSelector.Parameters.Builder(ctx)
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .setAllowVideoNonSeamlessAdaptiveness(true)
        caps.maxWidth?.let { w -> builder.setMaxVideoSize(w, caps.maxHeight ?: w) }
        caps.maxBitrate?.let { builder.setMaxVideoBitrate(it) }
        return builder.build()
    }

    /** Aplica um novo perfil de qualidade em tempo real (sem reinstanciar o player). */
    fun setQualityProfile(profile: PlaybackQualityProfile) {
        if (qualityProfile == profile) return
        qualityProfile = profile
        try { trackSelector?.setParameters(buildTrackParameters(profile)) } catch (_: Exception) {}
        if (BuildConfig.DEBUG) Log.d("ZapPlayer", "qualityProfile=$profile")
    }

    private fun createDataSourceFactory(userAgent: String?, referrer: String?): DataSource.Factory {
        val clientBuilder = okHttpClient.newBuilder()
        val effectiveUa = if (!userAgent.isNullOrBlank()) userAgent else "VLC/3.0.9 LibVLC/3.0.9"
        clientBuilder.addInterceptor { chain ->
            val original = chain.request()
            val rb = original.newBuilder().header("User-Agent", effectiveUa).header("Accept", "*/*")
            if (!referrer.isNullOrBlank()) rb.header("Referer", referrer)
            chain.proceed(rb.build())
        }
        return OkHttpDataSource.Factory(clientBuilder.build()).setUserAgent(effectiveUa)
    }

    fun play(url: String, referrer: String?, userAgent: String?, onError: () -> Unit) {
        playMulti(listOf(ApiStreamDto(null, null, "", url, referrer, userAgent, null, null)), referrer, userAgent, onError)
    }

    fun playMulti(streams: List<ApiStreamDto>, referrer: String? = null, userAgent: String? = null, onError: () -> Unit) {
        autoRetryRunnable?.let { handler.removeCallbacks(it) }
        autoRetryRunnable = null
        onReconnecting?.invoke(false)
        hasVideoTrack = false; renderedFirstFrame = false; videoFrameTimedOut = false
        transientRetries = 0
        watchdogReconnects = 0
        if (streams.isEmpty()) { clear(); onError(); return }
        this.streams = streams
        currentStreamIndex = 0
        onErrorCallback = onError
        playCurrentStream()
    }

    /**
     * Interrompe a reprodução e limpa a mídia atual. Garante que o frame do canal
     * anterior não permaneça visível ao trocar para um canal sem sinal (Etapa 07 §06).
     */
    fun clear() {
        autoRetryRunnable?.let { handler.removeCallbacks(it) }; autoRetryRunnable = null
        watchdogRunnable?.let { handler.removeCallbacks(it) }; watchdogRunnable = null
        firstFrameTimeoutRunnable?.let { handler.removeCallbacks(it) }; firstFrameTimeoutRunnable = null
        videoFallbackRunnable?.let { handler.removeCallbacks(it) }; videoFallbackRunnable = null
        onReconnecting?.invoke(false)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.clearMediaItems() } catch (_: Exception) {}
        onErrorCallback = null
        streams = emptyList()
        currentStreamIndex = 0
        bufferingStartMs = 0L; lastObservedPosition = -1L; positionStallStartMs = 0L
        hasVideoTrack = false; renderedFirstFrame = false; videoFrameTimedOut = false
        transientRetries = 0
        watchdogReconnects = 0
        onStateChanged?.invoke(PlaybackState.IDLE)
    }

    private fun playCurrentStream(reconnecting: Boolean = false) {
        val stream = streams.getOrNull(currentStreamIndex) ?: run { onErrorCallback?.invoke(); return }
        val p = build()
        try { p.stop(); p.clearMediaItems() } catch (_: Exception) {}
        removePreviousListener(p)
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        firstFrameTimeoutRunnable?.let { handler.removeCallbacks(it) }; firstFrameTimeoutRunnable = null
        videoFallbackRunnable?.let { handler.removeCallbacks(it) }; videoFallbackRunnable = null
        bufferingStartMs = 0L; lastObservedPosition = -1L; positionStallStartMs = 0L
        hasVideoTrack = false; renderedFirstFrame = false; videoFrameTimedOut = false
        onStateChanged?.invoke(if (reconnecting) PlaybackState.RETRYING else PlaybackState.CONNECTING)
        startWatchdog()
        startFirstFrameWatchdog()
        val dsFactory = createDataSourceFactory(stream.user_agent, stream.referrer)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dsFactory, extractorsFactory)
        val mediaItem = MediaItem.Builder().setUri(Uri.parse(stream.url)).setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).setMinOffsetMs(5_000).setMaxOffsetMs(20_000).build()
        ).build()
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        if (BuildConfig.DEBUG) Log.d("AvDiag", "conexao host=${hostOf(stream.url)}")
        val listener = makePlayerListener(p)
        playerListener = listener
        p.addListener(listener)
        try { p.setMediaSource(mediaSource); p.prepare(); p.play() } catch (_: Exception) { tryNextStream() }
    }

    private fun makePlayerListener(p: ExoPlayer): Player.Listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (BuildConfig.DEBUG) {
                Log.d("AvDiag", "falha tipo=${classifyError(error)} erro=${error.errorCode}")
                Log.d("ZapDiag", "erro=${error.errorCode} causa=${error.cause?.javaClass?.simpleName}")
            }
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                try { p.seekToDefaultPosition(); p.prepare(); p.play(); return } catch (_: Exception) {}
            }
            val isFatalHttp = when (val cause = error.cause) {
                is HttpDataSource.InvalidResponseCodeException -> cause.responseCode in 400..499 && cause.responseCode != 408
                else -> error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND || error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
            }
            if (isFatalHttp) tryNextStream() else tryNextStream()
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_BUFFERING && bufferingStartMs == 0L) bufferingStartMs = System.currentTimeMillis()
            if (state == Player.STATE_READY) {
                bufferingStartMs = 0L; positionStallStartMs = 0L
                transientRetries = 0
                watchdogReconnects = 0
                onReconnecting?.invoke(false)
            }
            emitState()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && BuildConfig.DEBUG && !renderedFirstFrame) {
                Log.d("AvDiag", "audio iniciado video=${hasVideoTrack} quadro=$renderedFirstFrame")
            }
            emitState()
        }
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            hasVideoTrack = videoSize.width > 0 && videoSize.height > 0
            emitState()
        }
        override fun onRenderedFirstFrame() {
            renderedFirstFrame = true; videoFrameTimedOut = false
            firstFrameTimeoutRunnable?.let { handler.removeCallbacks(it) }; firstFrameTimeoutRunnable = null
            videoFallbackRunnable?.let { handler.removeCallbacks(it) }; videoFallbackRunnable = null
            if (BuildConfig.DEBUG) Log.d("AvDiag", "primeiro quadro renderizado -> reproducao confirmada")
            emitState()
        }
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            hasVideoTrack = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
            emitState()
        }
    }

    /** Publica o estado atual (Etapa 10 §07) — base para distinguir áudio-só e evitar falso "sem sinal". */
    private fun emitState() {
        val p = player ?: return
        val state = PlaybackStates.fromExo(p.playbackState, p.isPlaying, hasVideoTrack, renderedFirstFrame, videoFrameTimedOut)
        if (BuildConfig.DEBUG) Log.d("ZapPlayer", "state=$state video=$hasVideoTrack frame=$renderedFirstFrame timedOut=$videoFrameTimedOut")
        onStateChanged?.invoke(state)
    }

    /**
     * Etapa 14 §01: se a trilha de vídeo foi declarada mas o primeiro quadro não chegou
     * dentro de [FIRST_FRAME_TIMEOUT_MS], marca o canal como áudio-sem-imagem. É
     * cancelado assim que um quadro é renderizado ou a fonte é trocada.
     */
    private fun startFirstFrameWatchdog() {
        firstFrameTimeoutRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (hasVideoTrack && !renderedFirstFrame) {
                videoFrameTimedOut = true
                if (BuildConfig.DEBUG) Log.d("ZapPlayer", "first-frame timeout -> AUDIO_ONLY")
                emitState()
            }
        }
        firstFrameTimeoutRunnable = r
        handler.postDelayed(r, FIRST_FRAME_TIMEOUT_MS)

        // Etapa 15 §03/§04: janela mais tolerante antes de ESCALAR a falha de vídeo
        // (respeita conexões lentas). Se a trilha de vídeo continua sem renderizar,
        // avisa o ViewModel para tentar outra fonte autorizada.
        videoFallbackRunnable?.let { handler.removeCallbacks(it) }
        val fb = Runnable {
            if (hasVideoTrack && !renderedFirstFrame) {
                videoFrameTimedOut = true
                if (BuildConfig.DEBUG) Log.d("AvDiag", "video interrompido -> fallback por falha de video")
                emitState()
                onVideoUnavailable?.invoke()
            }
        }
        videoFallbackRunnable = fb
        handler.postDelayed(fb, VIDEO_FALLBACK_MS)
    }

    /** Classifica a falha para diagnóstico (Etapa 15 §06), sem expor URLs/dados sensíveis. */
    private fun classifyError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "codec"
        PlaybackException.ERROR_CODE_TIMEOUT -> "timeout"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "conexao"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "http"
        else -> "outro"
    }

    private fun hostOf(url: String): String =
        try { java.net.URI(url).host ?: "-" } catch (_: Exception) { "-" }

    private fun tryNextStream(): Boolean {
        // Ao sair da stream atual, zera as reconexões do watchdog (Etapa 20 §12).
        watchdogReconnects = 0
        val next = currentStreamIndex + 1
        return if (next < streams.size) {
            currentStreamIndex = next
            onReconnecting?.invoke(true)
            playCurrentStream(reconnecting = true); true
        } else if (transientRetries < MAX_TRANSIENT_RETRIES) {
            // Falha transitória: repete a MESMA fonte antes de escalar (Etapa 11 §05).
            transientRetries++
            onReconnecting?.invoke(true)
            onStateChanged?.invoke(PlaybackState.RETRYING)
            val retry = Runnable { if (streams.isNotEmpty() && player != null) { currentStreamIndex = 0; playCurrentStream(reconnecting = true) } }
            autoRetryRunnable = retry
            handler.postDelayed(retry, RETRY_DELAY_MS); false
        } else {
            // Repetições esgotadas: falha CONFIRMADA — o ViewModel avança de fonte.
            onErrorCallback?.invoke()
            false
        }
    }

    private fun removePreviousListener(p: ExoPlayer) {
        try { playerListener?.let { p.removeListener(it) } } catch (_: Exception) {}
        playerListener = null
    }

    private fun startWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                val p = player ?: run { handler.postDelayed(this, 2000L); return }
                val now = System.currentTimeMillis()
                val state = p.playbackState
                if (state == Player.STATE_BUFFERING && bufferingStartMs != 0L && now - bufferingStartMs > 5000L) {
                    if (watchdogReconnects >= MAX_WATCHDOG_RECONNECTS) {
                        Log.d("ZapPlayer", "watchdog BUFFERING sem progresso -> escalar")
                        watchdogReconnects = 0
                        bufferingStartMs = 0L
                        onReconnecting?.invoke(true)
                        tryNextStream(); return
                    }
                    Log.d("ZapPlayer", "watchdog BUFFERING>5s -> reconnect")
                    bufferingStartMs = 0L
                    watchdogReconnects++
                    onReconnecting?.invoke(true)
                    // 1.5s back seek já está em playCurrentStream via seekTo lastPos -1500
                    playCurrentStream(reconnecting = true); return
                }
                if (state == Player.STATE_READY && p.playWhenReady) {
                    val pos = p.currentPosition
                    if (pos == lastObservedPosition && pos != -1L) {
                        if (positionStallStartMs == 0L) positionStallStartMs = now
                        else if (now - positionStallStartMs > 3000L) {
                            positionStallStartMs = 0L
                            if (watchdogReconnects >= MAX_WATCHDOG_RECONNECTS) {
                                Log.d("ZapPlayer", "watchdog stall sem progresso -> escalar")
                                watchdogReconnects = 0
                                onReconnecting?.invoke(true)
                                tryNextStream(); return
                            }
                            Log.d("ZapPlayer", "watchdog READY stall>3s -> reconnect 1.5s back")
                            watchdogReconnects++
                            val lastPos = lastObservedPosition
                            try { p.seekTo((lastPos - 1500).coerceAtLeast(0)); p.prepare(); p.play() } catch (_: Exception) {}
                            onReconnecting?.invoke(true)
                            handler.postDelayed({ playCurrentStream(reconnecting = true) }, 500L); return
                        }
                    } else { lastObservedPosition = pos; positionStallStartMs = 0L }
                } else { positionStallStartMs = 0L }
                handler.postDelayed(this, 2000L)
            }
        }
        watchdogRunnable = r; handler.postDelayed(r, 2000L)
    }

    fun release() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }; watchdogRunnable = null
        autoRetryRunnable?.let { handler.removeCallbacks(it) }; autoRetryRunnable = null
        firstFrameTimeoutRunnable?.let { handler.removeCallbacks(it) }; firstFrameTimeoutRunnable = null
        videoFallbackRunnable?.let { handler.removeCallbacks(it) }; videoFallbackRunnable = null
        bufferingStartMs = 0L; lastObservedPosition = -1L; positionStallStartMs = 0L
        onReconnecting?.invoke(false)
        try { playerListener?.let { player?.removeListener(it) } } catch (_: Exception) {}
        playerListener = null
        try { player?.release() } catch (_: Exception) {}
        player = null; onErrorCallback = null; streams = emptyList(); currentStreamIndex = 0
        trackSelector = null
        hasVideoTrack = false; renderedFirstFrame = false; videoFrameTimedOut = false
        watchdogReconnects = 0
        onVideoUnavailable = null
    }
}
