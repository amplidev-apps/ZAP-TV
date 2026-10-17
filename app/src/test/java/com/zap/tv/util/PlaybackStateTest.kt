package com.zap.tv.util

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 10 §07: mapeamento do estado do ExoPlayer para o estado de reprodução do app.
 */
class PlaybackStateTest {

    @Test
    fun idle_e_ended() {
        assertEquals(PlaybackState.IDLE,
            PlaybackStates.fromExo(Player.STATE_IDLE, false, false, false))
        assertEquals(PlaybackState.UNAVAILABLE,
            PlaybackStates.fromExo(Player.STATE_ENDED, false, false, false))
    }

    @Test
    fun buffering_sem_frame_e_conectando() {
        assertEquals(PlaybackState.CONNECTING,
            PlaybackStates.fromExo(Player.STATE_BUFFERING, true, false, false))
        assertEquals(PlaybackState.BUFFERING,
            PlaybackStates.fromExo(Player.STATE_BUFFERING, true, true, true))
    }

    @Test
    fun ready_com_video_e_playing() {
        assertEquals(PlaybackState.PLAYING,
            PlaybackStates.fromExo(Player.STATE_READY, true, true, true))
    }

    @Test
    fun ready_sem_video_e_audio_somente() {
        assertEquals(PlaybackState.AUDIO_ONLY,
            PlaybackStates.fromExo(Player.STATE_READY, true, false, false))
    }

    @Test
    fun ready_video_declarado_sem_frame_dentro_da_tolerancia_ainda_e_playing() {
        // Etapa 14 §01: janela de tolerância evita piscar AUDIO_ONLY na inicialização.
        assertEquals(PlaybackState.PLAYING,
            PlaybackStates.fromExo(Player.STATE_READY, true, true, false, videoFrameTimedOut = false))
    }

    @Test
    fun ready_video_declarado_sem_frame_apos_timeout_e_audio_somente() {
        // Trilha de vídeo existe, mas nunca renderizou -> não declarar PLAYING.
        assertEquals(PlaybackState.AUDIO_ONLY,
            PlaybackStates.fromExo(Player.STATE_READY, true, true, false, videoFrameTimedOut = true))
    }

    @Test
    fun estados_transitorios() {
        assertTrue(PlaybackStates.isTransient(PlaybackState.CONNECTING))
        assertTrue(PlaybackStates.isTransient(PlaybackState.BUFFERING))
        assertTrue(PlaybackStates.isTransient(PlaybackState.RETRYING))
        assertFalse(PlaybackStates.isTransient(PlaybackState.PLAYING))
        assertFalse(PlaybackStates.isTransient(PlaybackState.UNAVAILABLE))
    }
}
