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
import com.zap.tv.data.api.ApiStreamDto
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
        player = ExoPlayer.Builder(ctx, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)
            }
        return player!!
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
        if (streams.isEmpty()) { onError(); return }
        this.streams = streams
        currentStreamIndex = 0
        onErrorCallback = onError
        playCurrentStream()
    }

    private fun playCurrentStream() {
        val stream = streams.getOrNull(currentStreamIndex) ?: run { onErrorCallback?.invoke(); return }
        val p = build()
        try { p.stop(); p.clearMediaItems() } catch (_: Exception) {}
        removePreviousListener(p)
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        bufferingStartMs = 0L; lastObservedPosition = -1L; positionStallStartMs = 0L
        startWatchdog()
        val dsFactory = createDataSourceFactory(stream.user_agent, stream.referrer)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dsFactory, extractorsFactory)
        val mediaItem = MediaItem.Builder().setUri(Uri.parse(stream.url)).setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).setMinOffsetMs(5_000).setMaxOffsetMs(20_000).build()
        ).build()
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        Log.d("ZapPlayer", "play ${stream.url.take(60)} isHls=true")
        val listener = makePlayerListener(p)
        playerListener = listener
        p.addListener(listener)
        try { p.setMediaSource(mediaSource); p.prepare(); p.play() } catch (_: Exception) { tryNextStream() }
    }

    private fun makePlayerListener(p: ExoPlayer): Player.Listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.d("ZapPlayer", "onPlayerError ${error.errorCode} ${error.cause}")
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
                onReconnecting?.invoke(false)
            }
        }
    }

    private fun tryNextStream(): Boolean {
        val next = currentStreamIndex + 1
        return if (next < streams.size) {
            currentStreamIndex = next
            onReconnecting?.invoke(true)
            playCurrentStream(); true
        } else {
            onErrorCallback?.invoke()
            val retry = Runnable { if (streams.isNotEmpty() && player != null) { currentStreamIndex = 0; onReconnecting?.invoke(true); playCurrentStream() } }
            autoRetryRunnable = retry
            handler.postDelayed(retry, 2500L); false
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
                    Log.d("ZapPlayer", "watchdog BUFFERING>5s -> reconnect")
                    bufferingStartMs = 0L
                    onReconnecting?.invoke(true)
                    // 1.5s back seek já está em playCurrentStream via seekTo lastPos -1500
                    playCurrentStream(); return
                }
                if (state == Player.STATE_READY && p.playWhenReady) {
                    val pos = p.currentPosition
                    if (pos == lastObservedPosition && pos != -1L) {
                        if (positionStallStartMs == 0L) positionStallStartMs = now
                        else if (now - positionStallStartMs > 3000L) {
                            Log.d("ZapPlayer", "watchdog READY stall>3s -> reconnect 1.5s back")
                            positionStallStartMs = 0L
                            val lastPos = lastObservedPosition
                            try { p.seekTo((lastPos - 1500).coerceAtLeast(0)); p.prepare(); p.play() } catch (_: Exception) {}
                            onReconnecting?.invoke(true)
                            handler.postDelayed({ playCurrentStream() }, 500L); return
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
        bufferingStartMs = 0L; lastObservedPosition = -1L; positionStallStartMs = 0L
        onReconnecting?.invoke(false)
        try { playerListener?.let { player?.removeListener(it) } } catch (_: Exception) {}
        playerListener = null
        try { player?.release() } catch (_: Exception) {}
        player = null; onErrorCallback = null; streams = emptyList(); currentStreamIndex = 0
    }
}
