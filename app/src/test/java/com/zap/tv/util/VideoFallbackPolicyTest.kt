package com.zap.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 15 §03/§04: fallback após falha de VÍDEO (áudio sem imagem), sem loops.
 */
class VideoFallbackPolicyTest {

    private fun attempt(mech: PlaybackMechanism) = PlaybackAttempt(
        mechanism = mech,
        source = PlaybackSource(
            network = null,
            identityLabel = "Canal",
            region = null,
            regionKind = SourceRegionKind.UNREGIONALIZED,
            label = "Fonte"
        )
    )

    @Test
    fun avanca_para_a_proxima_fonte_quando_existe() {
        val attempts = listOf(
            attempt(PlaybackMechanism.DIRECT_STREAM),
            attempt(PlaybackMechanism.WEB_EMBED)
        )
        val d = VideoFallbackPolicy.decide(0, attempts)
        assertTrue(d is VideoFallbackDecision.Next)
        assertEquals(1, (d as VideoFallbackDecision.Next).index)
    }

    @Test
    fun mantem_audio_quando_plano_esgotado() {
        val attempts = listOf(attempt(PlaybackMechanism.DIRECT_STREAM))
        assertEquals(VideoFallbackDecision.KeepAudioOnly, VideoFallbackPolicy.decide(0, attempts))
    }

    @Test
    fun nao_aplica_com_plano_vazio_ou_indice_invalido() {
        assertEquals(VideoFallbackDecision.None, VideoFallbackPolicy.decide(0, emptyList()))
        val attempts = listOf(attempt(PlaybackMechanism.DIRECT_STREAM))
        assertEquals(VideoFallbackDecision.None, VideoFallbackPolicy.decide(5, attempts))
    }

    @Test
    fun nao_gera_loop_indices_estritamente_crescentes() {
        val attempts = List(6) { attempt(PlaybackMechanism.DIRECT_STREAM) }
        var idx = 0
        var steps = 0
        while (true) {
            when (val d = VideoFallbackPolicy.decide(idx, attempts)) {
                is VideoFallbackDecision.Next -> {
                    assertTrue("índice deve crescer", d.index > idx)
                    idx = d.index
                    steps++
                }
                VideoFallbackDecision.KeepAudioOnly -> {
                    assertEquals(attempts.lastIndex, idx)
                    break
                }
                VideoFallbackDecision.None -> break
            }
            if (steps > attempts.size + 1) break
        }
        assertTrue("passos limitados pelo plano", steps <= attempts.size)
    }

    @Test
    fun troca_de_exoplayer_para_webview_apos_falha_de_video() {
        val attempts = listOf(
            attempt(PlaybackMechanism.DIRECT_STREAM),
            attempt(PlaybackMechanism.WEB_EMBED)
        )
        val d = VideoFallbackPolicy.decide(0, attempts) as VideoFallbackDecision.Next
        assertEquals(PlaybackMechanism.WEB_EMBED, attempts[d.index].mechanism)
    }
}
