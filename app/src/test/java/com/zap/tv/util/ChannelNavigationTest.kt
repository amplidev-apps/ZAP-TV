package com.zap.tv.util

import com.zap.tv.data.local.entity.ChannelNumberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Etapa 17 §02: a navegação (canal +/-) percorre a GRADE ATIVA COMPLETA, por número,
 * sem pular canais presentes mas indisponíveis. Corrige o salto 9 -> 14 causado por
 * ocultar registros de catálogo da Fonte A (sem stream) durante a navegação.
 */
class ChannelNavigationTest {

    private fun ent(number: Int, id: String, available: Boolean = true) =
        ChannelNumberEntity(
            number = number,
            channelId = id,
            channelName = "Canal $number",
            country = "BR",
            categories = "general",
            logoUrl = null,
            isNsfw = false,
            streamCount = if (available) 2 else 0,
            isAvailable = available
        )

    /** Grade com os fixos 1-9 e o bloco destacado 10+ (10-13 sem fonte reproduzível). */
    private fun grade() = (1..9).map { ent(it, "fix$it") } +
        (10..13).map { ent(it, "rdc:catalog$it", available = false) } +
        listOf(ent(14, "AXN.br"))

    @Test
    fun proximo_nao_pula_canais_presentes_mas_indisponiveis() {
        val next = ChannelNumbering.nextChannel(grade(), 9)
        assertEquals("o canal 10 existe na grade e não pode ser pulado", 10, next?.number)
        assertEquals("rdc:catalog10", next?.channelId)
    }

    @Test
    fun anterior_nao_pula_canais_presentes_mas_indisponiveis() {
        val prev = ChannelNumbering.prevChannel(grade(), 14)
        assertEquals(13, prev?.number)
        assertEquals("rdc:catalog13", prev?.channelId)
    }

    @Test
    fun sequencia_e_estritamente_crescente_por_numero() {
        var current = 9
        val visited = mutableListOf(9)
        repeat(5) {
            current = ChannelNumbering.nextChannel(grade(), current)!!.number
            visited += current
        }
        assertEquals(listOf(9, 10, 11, 12, 13, 14), visited)
    }

    @Test
    fun envolve_nas_pontas() {
        assertEquals(1, ChannelNumbering.nextChannel(grade(), 14)?.number)
        assertEquals(14, ChannelNumbering.prevChannel(grade(), 1)?.number)
    }

    @Test
    fun canal_desconhecido_retorna_extremos() {
        assertEquals(1, ChannelNumbering.nextChannel(grade(), 999)?.number)
        assertEquals(14, ChannelNumbering.prevChannel(grade(), 999)?.number)
    }

    @Test
    fun grade_vazia_retorna_null() {
        assertNull(ChannelNumbering.nextChannel(emptyList(), 1))
        assertNull(ChannelNumbering.prevChannel(emptyList(), 1))
    }
}
