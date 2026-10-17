package com.zap.tv.util

import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.reidoscanais.RdcEmbedDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 09 §12: seleção entre ExoPlayer e WebView, fallback de fontes e tratamento
 * de URLs inválidas. Não valida reprodução real (isso exige WebView/ExoPlayer reais).
 */
class PlaybackResolverTest {

    private fun stream(url: String) = ApiStreamDto(
        channel = "canal.br", feed = null, title = "HD", url = url,
        referrer = null, user_agent = null, quality = "HD", label = "HD"
    )

    private fun embed(url: String, provider: String = "Provedor", quality: String? = "HD") =
        RdcEmbedDto(provider = provider, quality = quality, embed_url = url)

    @Test
    fun stream_direto_tem_precedencia_sobre_embed() {
        val plan = PlaybackResolver.resolve(
            directStreams = listOf(stream("https://cdn.test/a.m3u8")),
            embeds = listOf(embed("https://rdcanais.org/x"))
        )
        assertEquals(PlaybackMechanism.DIRECT_STREAM, plan.mechanism)
        assertEquals(1, plan.directStreams.size)
        // Embeds preservados para eventual fallback entre mecanismos.
        assertEquals(1, plan.webEmbeds.size)
    }

    @Test
    fun sem_stream_mas_com_embed_usa_webview() {
        val plan = PlaybackResolver.resolve(emptyList(), listOf(embed("https://rdcanais.org/canal")))
        assertEquals(PlaybackMechanism.WEB_EMBED, plan.mechanism)
        assertTrue(plan.directStreams.isEmpty())
        assertEquals(1, plan.webEmbeds.size)
    }

    @Test
    fun sem_fontes_resulta_indisponivel() {
        val plan = PlaybackResolver.resolve(emptyList(), emptyList())
        assertEquals(PlaybackMechanism.UNAVAILABLE, plan.mechanism)
        assertTrue(plan.webEmbeds.isEmpty())
    }

    @Test
    fun apenas_embeds_http_https_sao_aceitos() {
        assertTrue(PlaybackResolver.isEmbeddableUrl("https://rdcanais.org/a"))
        assertTrue(PlaybackResolver.isEmbeddableUrl("http://rdcanais.org/a"))
        assertFalse(PlaybackResolver.isEmbeddableUrl("ftp://rdcanais.org/a"))
        assertFalse(PlaybackResolver.isEmbeddableUrl("javascript:alert(1)"))
        assertFalse(PlaybackResolver.isEmbeddableUrl(""))
        assertFalse(PlaybackResolver.isEmbeddableUrl(null))
    }

    @Test
    fun embeds_invalidos_sao_descartados() {
        val plan = PlaybackResolver.resolve(
            directStreams = emptyList(),
            embeds = listOf(
                embed("javascript:void(0)"),
                embed(""),
                embed("https://rdcanais.org/valido")
            )
        )
        assertEquals(PlaybackMechanism.WEB_EMBED, plan.mechanism)
        assertEquals(1, plan.webEmbeds.size)
        assertEquals("https://rdcanais.org/valido", plan.webEmbeds[0].embed_url)
    }

    @Test
    fun embeds_duplicados_sao_deduplicados_preservando_provedor_e_qualidade() {
        val plan = PlaybackResolver.resolve(
            directStreams = emptyList(),
            embeds = listOf(
                embed("https://rdcanais.org/a", provider = "P1", quality = "HD"),
                embed("https://rdcanais.org/a", provider = "P2", quality = "SD")
            )
        )
        assertEquals(1, plan.webEmbeds.size)
        assertEquals("P1", plan.webEmbeds[0].provider)
        assertEquals("HD", plan.webEmbeds[0].quality)
    }

    @Test
    fun multiplas_fontes_web_preservam_ordem_para_fallback() {
        val plan = PlaybackResolver.resolve(
            directStreams = emptyList(),
            embeds = listOf(
                embed("https://rdcanais.org/a", provider = "A"),
                embed("https://rdcanais.org/b", provider = "B"),
                embed("https://rdcanais.org/c", provider = "C")
            )
        )
        assertEquals(listOf("A", "B", "C"), plan.webEmbeds.map { it.provider })
    }

    // ---- Etapa 13: preferência manual de fonte ----

    @Test
    fun canal_citado_promove_embed_da_fonte_a_e_mantem_stream_como_recuperacao() {
        val plan = PlaybackResolver.resolve(
            channelId = "rdc:tnt",
            displayName = "TNT",
            directStreams = listOf(stream("https://cdn.test/tnt.m3u8")),
            embeds = listOf(embed("https://rdcanais.org/tnt", provider = "Servidor Premium"))
        )
        assertEquals(PlaybackMechanism.WEB_EMBED, plan.mechanism)
        assertEquals("https://rdcanais.org/tnt", plan.webEmbeds.first().embed_url)
        assertEquals(1, plan.directStreams.size)
    }

    @Test
    fun canal_manter_fonte_existente_mantem_stream_em_primeiro() {
        val plan = PlaybackResolver.resolve(
            channelId = "rdc:globonews",
            displayName = "Globo News",
            directStreams = listOf(stream("https://cdn.test/globonews.m3u8")),
            embeds = listOf(embed("https://rdcanais.org/globonews", provider = "Servidor Premium"))
        )
        assertEquals(PlaybackMechanism.DIRECT_STREAM, plan.mechanism)
    }

    @Test
    fun canal_fora_das_regras_mantem_comportamento_da_etapa_09() {
        val plan = PlaybackResolver.resolve(
            channelId = "rdc:canalqualquer",
            displayName = "Canal Qualquer",
            directStreams = listOf(stream("https://cdn.test/q.m3u8")),
            embeds = listOf(embed("https://rdcanais.org/qq"))
        )
        assertEquals(PlaybackMechanism.DIRECT_STREAM, plan.mechanism)
    }
}
