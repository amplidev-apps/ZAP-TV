package com.zap.tv.util

import androidx.media3.common.Player

/**
 * Estado de reprodução observável (Etapa 10 §06). Distingue explicitamente:
 * - [IDLE]/[CONNECTING]/[BUFFERING]: tentativa legítima em andamento (não é "sem sinal").
 * - [PLAYING]: vídeo efetivamente renderizado.
 * - [AUDIO_ONLY]: há áudio, mas NENHUM vídeo — não é sucesso audiovisual.
 * - [RETRYING]: recuperação automática em andamento.
 * - [UNAVAILABLE]/[ERROR]: terminais.
 * - [UNKNOWN_PLAYBACK_STATE]: não é possível observar o estado interno (ex.: WebView).
 */
enum class PlaybackState {
    IDLE,
    CONNECTING,
    BUFFERING,
    PLAYING,
    AUDIO_ONLY,
    RETRYING,
    UNAVAILABLE,
    ERROR,
    UNKNOWN_PLAYBACK_STATE
}

/**
 * Regras puras de transição/derivação de estado, testáveis sem Android.
 */
object PlaybackStates {

    /** `true` quando ainda existe tentativa legítima (não exibir "sem sinal"). */
    fun isTransient(state: PlaybackState): Boolean = when (state) {
        PlaybackState.IDLE,
        PlaybackState.CONNECTING,
        PlaybackState.BUFFERING,
        PlaybackState.RETRYING,
        PlaybackState.UNKNOWN_PLAYBACK_STATE -> true
        else -> false
    }

    /**
     * Deriva o estado a partir de observações cruas do player direto.
     *
     * [hasRenderedFirstFrame] é **obrigatório** para declarar PLAYING (Etapa 14 §01):
     * uma trilha de vídeo *declarada* ([hasVideoTrack]) pode existir mesmo quando o
     * vídeo nunca é renderizado (decoder/codec/superfície) — nesse caso o áudio toca
     * e a tela fica preta. Para não piscar durante a inicialização, existe uma janela
     * de tolerância: enquanto [videoFrameTimedOut] for `false` o estado permanece
     * PLAYING (otimista); após o tempo limite sem primeiro quadro vira AUDIO_ONLY.
     */
    fun fromExo(
        playerState: Int,
        isPlaying: Boolean,
        hasVideoTrack: Boolean,
        hasRenderedFirstFrame: Boolean,
        videoFrameTimedOut: Boolean = false
    ): PlaybackState = when (playerState) {
        Player.STATE_IDLE -> PlaybackState.IDLE
        Player.STATE_BUFFERING ->
            if (hasRenderedFirstFrame) PlaybackState.BUFFERING else PlaybackState.CONNECTING
        Player.STATE_READY -> when {
            !isPlaying -> PlaybackState.BUFFERING
            !hasVideoTrack -> PlaybackState.AUDIO_ONLY
            hasRenderedFirstFrame -> PlaybackState.PLAYING
            videoFrameTimedOut -> PlaybackState.AUDIO_ONLY
            else -> PlaybackState.PLAYING
        }
        Player.STATE_ENDED -> PlaybackState.UNAVAILABLE
        else -> PlaybackState.UNKNOWN_PLAYBACK_STATE
    }
}
