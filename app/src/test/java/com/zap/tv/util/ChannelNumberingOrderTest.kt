package com.zap.tv.util

import com.zap.tv.data.local.entity.ChannelNumberEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Etapa 10 §05: ordem definitiva da grade — fixos (1-9) -> Fonte A (RDC) -> iptv-org.
 */
class ChannelNumberingOrderTest {

    private fun ent(number: Int, id: String, name: String, country: String = "BR", categories: String = "general") =
        ChannelNumberEntity(
            number = number,
            channelId = id,
            channelName = name,
            country = country,
            categories = categories,
            logoUrl = null,
            isNsfw = false,
            streamCount = 1
        )

    @Test
    fun fixos_depois_rdc_depois_demais() {
        val entities = listOf(
            ent(30, "xyz.br", "Zeta"),
            ent(50, "rdc:a", "Alpha Rdc"),
            ent(20, "rdc:b", "Beta Rdc"),
            ent(1, "RedeGlobo.br", "Globo")
        )
        val ordered = ChannelNumbering.orderGrade(entities, setOf("rdc:a", "rdc:b"))

        // Fixo preservado na posição 1.
        assertEquals("RedeGlobo.br", ordered.first { it.number == 1 }.channelId)
        // Bloco RDC começa em 10, ordenado por nome.
        assertEquals("rdc:a", ordered.first { it.number == 10 }.channelId)
        assertEquals("rdc:b", ordered.first { it.number == 11 }.channelId)
        // Demais canais vêm depois do bloco RDC.
        assertEquals("xyz.br", ordered.first { it.number == 12 }.channelId)
    }

    @Test
    fun posicoes_fixas_nao_sao_renumeradas() {
        val entities = listOf(
            ent(1, "RedeGlobo.br", "Globo"),
            ent(8, "tv_centro_sul_br", "TV Centro Sul"),
            ent(9, "TVLitoralRN.br", "TV Litoral RN"),
            ent(40, "rdc:x", "Canal Rdc")
        )
        val ordered = ChannelNumbering.orderGrade(entities, setOf("rdc:x"))
        assertEquals(setOf(1, 8, 9), ordered.filter { it.number in 1..9 }.map { it.number }.toSet())
        assertEquals("tv_centro_sul_br", ordered.first { it.number == 8 }.channelId)
    }

    @Test
    fun renumera_a_partir_de_10_sem_lacunas() {
        val entities = (1..9).map { ent(it, "fix$it", "Fixo $it") } +
            listOf(ent(100, "rdc:a", "A", categories = "news"), ent(101, "other", "B"))
        val ordered = ChannelNumbering.orderGrade(entities, setOf("rdc:a"))
        assertEquals((1..11).toList(), ordered.map { it.number })
    }
}
