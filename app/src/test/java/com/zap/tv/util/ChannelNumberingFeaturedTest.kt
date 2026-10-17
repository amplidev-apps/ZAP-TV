package com.zap.tv.util

import com.zap.tv.data.local.entity.ChannelNumberEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Etapa 13 §09: bloco alfabético dos canais citados em `novas regras.md`, logo após
 * os fixos 1–9, antes dos demais canais da Fonte A. Reversível por
 * [CatalogFilters.FEATURED_ALPHA_BLOCK].
 */
class ChannelNumberingFeaturedTest {

    private fun ent(number: Int, id: String, name: String, country: String = "BR") =
        ChannelNumberEntity(
            number = number,
            channelId = id,
            channelName = name,
            country = country,
            categories = "general",
            logoUrl = null,
            isNsfw = false,
            streamCount = 1
        )

    @Test
    fun citados_formam_bloco_alfabetico_apos_os_fixos() {
        val entities = listOf(
            ent(1, "RedeGlobo.br", "Globo"),
            ent(40, "rdc:zeta", "Zeta"),
            ent(41, "rdc:tnt", "TNT"),
            ent(42, "rdc:axn", "AXN"),
            ent(43, "rdc:hbo", "HBO")
        )
        val ordered = ChannelNumbering.orderGrade(entities, setOf("rdc:zeta", "rdc:tnt", "rdc:axn", "rdc:hbo"))

        assertEquals("RedeGlobo.br", ordered.first { it.number == 1 }.channelId)
        assertEquals("AXN", ordered.first { it.number == 10 }.channelName)
        assertEquals("HBO", ordered.first { it.number == 11 }.channelName)
        assertEquals("TNT", ordered.first { it.number == 12 }.channelName)
        assertEquals("Zeta", ordered.first { it.number == 13 }.channelName)
    }

    @Test
    fun citado_fora_da_fonte_a_tambem_entra_no_bloco() {
        val entities = listOf(
            ent(1, "RedeGlobo.br", "Globo"),
            ent(50, "rdc:zeta", "Zeta"),
            ent(51, "TNT.br", "TNT", country = "BR")
        )
        val ordered = ChannelNumbering.orderGrade(entities, setOf("rdc:zeta"))
        assertEquals("TNT", ordered.first { it.number == 10 }.channelName)
        assertEquals("Zeta", ordered.first { it.number == 11 }.channelName)
    }

    @Test
    fun renumera_sem_lacunas_e_sem_duplicar() {
        val entities = (1..9).map { ent(it, "fix$it", "Fixo $it") } +
            listOf(ent(40, "rdc:tnt", "TNT"), ent(41, "rdc:zeta", "Zeta"), ent(42, "other", "Outro"))
        val ordered = ChannelNumbering.orderGrade(entities, setOf("rdc:tnt", "rdc:zeta"))
        assertEquals((1..12).toList(), ordered.map { it.number })
        assertEquals(ordered.map { it.channelId }.toSet().size, ordered.size)
    }

    @Test
    fun bloco_destacado_segue_a_chave_canonica() {
        // A ordem alfabética usa a chave canônica (sem acento/caixa), garantindo
        // estabilidade independente do nome de exibição (Etapa 14 §06).
        val entities = listOf(
            ent(1, "RedeGlobo.br", "Globo"),
            ent(40, "rdc:woohoo", "WooHoo"),
            ent(41, "rdc:cartoonnetwork", "Cartoon Network"),
            ent(42, "rdc:ae", "A&E"),
            ent(43, "rdc:cazetv", "Cazé TV")
        )
        val rdc = setOf("rdc:woohoo", "rdc:cartoonnetwork", "rdc:ae", "rdc:cazetv")
        val ordered = ChannelNumbering.orderGrade(entities, rdc)
        val keys = ordered.filter { it.number in 10..13 }
            .map { SourcePreferenceRegistry.nameOrderKey(it.channelName) }
        assertEquals(keys.sorted(), keys)
        assertEquals("A&E", ordered.first { it.number == 10 }.channelName)
    }

    @Test
    fun ordem_gerada_igual_ordem_persistida_por_numero() {
        // A == B: a lista já sai ordenada por número (é isso que vai ao Room e à UI).
        val entities = listOf(
            ent(1, "RedeGlobo.br", "Globo"),
            ent(50, "rdc:zeta", "Zeta"),
            ent(51, "rdc:tnt", "TNT"),
            ent(52, "rdc:axn", "AXN")
        )
        val ordered = ChannelNumbering.orderGrade(entities, setOf("rdc:zeta", "rdc:tnt", "rdc:axn"))
        assertEquals(ordered.sortedBy { it.number }.map { it.channelId }, ordered.map { it.channelId })
    }
}
