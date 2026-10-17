package com.zap.tv.util

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 10 §10: perfis de qualidade apenas limitam o teto do ABR nativo, sem
 * transcodificar nem forçar variante fixa.
 */
class QualityPolicyTest {

    @Test
    fun auto_nao_imp_oe_caps() {
        val caps = QualityPolicy.capsFor(PlaybackQualityProfile.AUTO)
        assertNull(caps.maxWidth)
        assertNull(caps.maxHeight)
        assertNull(caps.maxBitrate)
    }

    @Test
    fun data_saver_limita_sd() {
        val caps = QualityPolicy.capsFor(PlaybackQualityProfile.DATA_SAVER)
        assertEquals(480, caps.maxHeight)
        assertTrue(caps.maxBitrate!! in 1..1_000_000)
    }

    @Test
    fun best_limita_full_hd() {
        val caps = QualityPolicy.capsFor(PlaybackQualityProfile.BEST)
        assertEquals(1080, caps.maxHeight)
        assertTrue(caps.maxBitrate!! > 0)
    }
}
