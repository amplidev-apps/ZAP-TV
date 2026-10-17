package com.zap.tv.util

import com.zap.tv.data.api.ApiStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 14 §05: preferência de estabilidade da fonte. Corrige Band/TV Gazeta, que
 * abriam por IP/HTTP instável antes de tentar a fonte por domínio/HTTPS.
 */
class StreamPreferenceTest {

    private fun s(url: String, quality: String? = "HD") = ApiStreamDto(
        channel = "x.br", feed = null, title = quality ?: "", url = url,
        referrer = null, user_agent = null, quality = quality, label = quality
    )

    @Test
    fun https_dominio_vem_antes_de_http_ip() {
        val ordered = StreamPreference.order(
            listOf(
                s("http://170.84.165.204/BAND_HD/index.m3u8", "720p"),
                s("https://media.cdntvms.com.br/band_sat/index.m3u8", "576p")
            )
        )
        assertEquals("https://media.cdntvms.com.br/band_sat/index.m3u8", ordered.first().url)
    }

    @Test
    fun ordem_de_estabilidade() {
        assertEquals(0, StreamPreference.stabilityRank("https://host.tld/a.m3u8"))
        assertEquals(1, StreamPreference.stabilityRank("https://10.0.0.1/a.m3u8"))
        assertEquals(2, StreamPreference.stabilityRank("http://host.tld/a.m3u8"))
        assertEquals(3, StreamPreference.stabilityRank("http://10.0.0.1/a.m3u8"))
        assertEquals(4, StreamPreference.stabilityRank("ftp://host/a"))
        assertEquals(4, StreamPreference.stabilityRank(null))
    }

    @Test
    fun qualidade_numerica_reconhecida_com_inicio_conservador() {
        assertEquals(0, StreamPreference.qualityRank("480p"))
        assertEquals(1, StreamPreference.qualityRank("576p"))
        assertEquals(2, StreamPreference.qualityRank("720p"))
        assertEquals(3, StreamPreference.qualityRank("1080p"))
        assertEquals(5, StreamPreference.qualityRank("4K"))
        // Mesma estabilidade -> resolução menor primeiro (política preservada).
        val ordered = StreamPreference.order(
            listOf(s("https://h/a.m3u8", "1080p"), s("https://h/b.m3u8", "480p"))
        )
        assertEquals("https://h/b.m3u8", ordered.first().url)
    }

    @Test
    fun determinismo_total() {
        val a = StreamPreference.order(
            listOf(s("http://b/x.m3u8"), s("http://a/x.m3u8"), s("https://a/x.m3u8"))
        ).map { it.url }
        val b = StreamPreference.order(
            listOf(s("https://a/x.m3u8"), s("http://a/x.m3u8"), s("http://b/x.m3u8"))
        ).map { it.url }
        assertEquals(a, b)
        assertTrue(a.first().startsWith("https://"))
    }
}
