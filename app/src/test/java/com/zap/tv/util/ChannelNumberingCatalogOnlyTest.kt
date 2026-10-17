package com.zap.tv.util

import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 08: canais registrados apenas no catálogo (sem fonte reproduzível) devem
 * ser numerados normalmente, marcados `isAvailable = false`, e NUNCA ocupar as
 * posições fixas 1-9 (reservadas a emissoras reproduzíveis).
 */
class ChannelNumberingCatalogOnlyTest {

    private fun channel(id: String, name: String, categories: List<String> = listOf("general")) =
        ApiChannelDto(id = id, name = name, country = "BR", categories = categories, is_nsfw = false)

    private fun stream(channelId: String) = ApiStreamDto(
        channel = channelId,
        feed = null,
        title = "HD",
        url = "https://exemplo.test/$channelId.m3u8",
        referrer = null,
        user_agent = null,
        quality = "HD",
        label = "HD"
    )

    @Test
    fun canal_somente_catalogo_entra_numerado_e_indisponivel() {
        val channels = listOf(
            channel("premiere.br", "Premiere", listOf("sports")),
            channel("rdc:premiere2", "Premiere 2", listOf("sports")),
            channel("RedeGlobo.br", "Globo")
        )
        val streams = listOf(stream("premiere.br"), stream("RedeGlobo.br"))
        val numbered = ChannelNumbering.buildNumberedChannels(
            channels, streams, emptyList(), "NACIONAL", setOf("rdc:premiere2")
        )
        val co = numbered.firstOrNull { it.channelId == "rdc:premiere2" }
        assertTrue("canal catalog-only deve estar presente", co != null)
        assertFalse("canal catalog-only não é reproduzível", co!!.isAvailable)
        assertEquals(0, co.streamCount)
    }

    @Test
    fun canal_somente_catalogo_nao_ocupa_posicao_fixa() {
        // Único candidato a "Globo" é um registro de catálogo (sem stream).
        val channels = listOf(channel("rdc:globo", "Globo"))
        val numbered = ChannelNumbering.buildNumberedChannels(
            channels, emptyList(), emptyList(), "NACIONAL", setOf("rdc:globo")
        )
        assertNull("posição 1 não pode ser ocupada por canal sem reprodução",
            numbered.firstOrNull { it.number == 1 })
        assertTrue(numbered.any { it.channelId == "rdc:globo" })
    }

    @Test
    fun canal_reproduzivel_permanece_disponivel() {
        val channels = listOf(channel("RedeGlobo.br", "Globo"))
        val numbered = ChannelNumbering.buildNumberedChannels(
            channels, listOf(stream("RedeGlobo.br")), emptyList(), "NACIONAL"
        )
        val globo = numbered.firstOrNull { it.channelId == "RedeGlobo.br" }
        assertTrue(globo != null && globo.isAvailable)
    }
}
