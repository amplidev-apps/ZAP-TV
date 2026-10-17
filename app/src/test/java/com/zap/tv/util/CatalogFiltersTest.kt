package com.zap.tv.util

import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiLogoDto
import com.zap.tv.data.api.ApiStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da composição do catálogo ativo (Etapa 07 §08).
 * Cobre: exclusão de temáticos 24h, não exclusão de notícia/TV ao vivo,
 * política de URLs reproduzíveis e o flag reversível das listas antigas.
 */
class CatalogFiltersTest {

    // ------------------------------------------------------------------ 24h

    @Test
    fun exclui_canal_com_prefixo_24h() {
        assertTrue(CatalogFilters.isTematic24h("24h_101_dalmatas.br", "[24h] 101 Dalmatas", listOf("general")))
        assertTrue(CatalogFilters.isTematic24h("24h_3_palavrinhas.br", "[24H] 3 Palavrinhas", listOf("general")))
    }

    @Test
    fun exclui_canal_por_categoria_maratonar() {
        assertTrue(CatalogFilters.isTematic24h("algum_canal.br", "Canal Qualquer", listOf("maratonar")))
    }

    @Test
    fun exclui_canal_por_identificador_24h() {
        assertTrue(CatalogFilters.isTematic24h("24h_musicas_top_100_gospel_2024.br", "Top 100 Gospel", listOf("general")))
    }

    @Test
    fun exclui_marcador_24_horas_tematico() {
        assertTrue(CatalogFilters.isTematic24h("maratona_x.br", "24 horas de desenhos", listOf("general")))
    }

    @Test
    fun nao_exclui_noticia_24_horas() {
        assertFalse(CatalogFilters.isTematic24h("cnn_brasil.br", "CNN Brasil", listOf("news")))
        assertFalse(CatalogFilters.isTematic24h("24h_globonews.br", "[24h] GloboNews", listOf("news")))
        assertFalse(CatalogFilters.isTematic24h("bandnews.br", "BandNews 24 horas", listOf("general")))
    }

    @Test
    fun nao_exclui_canal_ao_vivo_normal() {
        assertFalse(CatalogFilters.isTematic24h("globo_sp.br", "Globo SP", listOf("general")))
        assertFalse(CatalogFilters.isTematic24h("band.br", "[HD] Band", listOf("general")))
        assertFalse(CatalogFilters.isTematic24h("", "", emptyList()))
    }

    // ------------------------------------------------------------ URLs

    @Test
    fun aceita_urls_hls_reproduziveis() {
        assertTrue(CatalogFilters.isPlayableStreamUrl("https://cdn.exemplo.com/live/playlist.m3u8"))
        assertTrue(CatalogFilters.isPlayableStreamUrl("http://host/servidor/stream.ts"))
    }

    @Test
    fun rejeita_embeds_e_esquemas_invalidos() {
        assertFalse(CatalogFilters.isPlayableStreamUrl("https://rdcanais.org/canal/globo"))
        assertFalse(CatalogFilters.isPlayableStreamUrl("https://api.reidoscanais.si/channels"))
        assertFalse(CatalogFilters.isPlayableStreamUrl("https://site/embed/abc"))
        assertFalse(CatalogFilters.isPlayableStreamUrl("rtsp://host/stream"))
        assertFalse(CatalogFilters.isPlayableStreamUrl(null))
        assertFalse(CatalogFilters.isPlayableStreamUrl(""))
    }

    // -------------------------------------------------- flag reversível

    @Test
    fun listas_antigas_desativadas_por_padrao() {
        assertFalse(CatalogFilters.LEGACY_BR_CATALOG_ENABLED)
    }

    // ------------------------------- composição efetiva do catálogo

    @Test
    fun buildNumberedChannels_exclui_tematicos_24h_e_preserva_fixos() {
        val channels = listOf(
            ch("RedeGlobo.br", "Globo", "general"),
            ch("24h_desenhos.br", "[24h] Desenhos", "general"),
            ch("maratona_filmes.br", "Maratona Filmes", "maratonar"),
            ch("TVLitoralRN.br", "TV Litoral RN", "general")
        )
        val streams = channels.map {
            ApiStreamDto(it.id, null, "HD", "https://cdn/${it.id}.m3u8", null, null, "HD", "HD")
        }
        val logos = emptyList<ApiLogoDto>()

        val numbered = ChannelNumbering.buildNumberedChannels(channels, streams, logos, "NACIONAL")
        val ids = numbered.map { it.channelId }

        assertFalse("temático [24h] não deve constar", ids.contains("24h_desenhos.br"))
        assertFalse("maratonar não deve constar", ids.contains("maratona_filmes.br"))
        assertTrue("TV Litoral RN preservada", ids.contains("TVLitoralRN.br"))
        // TV Centro-Sul continua na posição fixa 8 (exceção obrigatória).
        assertEquals("tv_centro_sul_br", numbered.firstOrNull { it.number == 8 }?.channelId)
    }

    @Test
    fun buildNumberedChannels_sem_streams_nao_gera_canal_metadados() {
        val channels = listOf(ch("RedeGlobo.br", "Globo", "general"), ch("SBTNacional.br", "SBT", "general"))
        val numbered = ChannelNumbering.buildNumberedChannels(channels, emptyList(), emptyList(), "NACIONAL")
        // Sem nenhum stream, não há canal de grade além das posições fixas resolvíveis.
        assertTrue(numbered.none { it.number > 9 })
    }

    private fun ch(id: String, name: String, category: String) = ApiChannelDto(
        id = id,
        name = name,
        country = "BR",
        categories = listOf(category),
        is_nsfw = false,
        owners = emptyList()
    )
}
