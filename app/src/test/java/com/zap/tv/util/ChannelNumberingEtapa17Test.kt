package com.zap.tv.util

import com.zap.tv.data.local.entity.ChannelNumberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 17: regressões de grade/fontes.
 *  - §03: famílias de emissoras permanecem consecutivas no bloco destacado.
 *  - §04: TV Cultura (posição 7) nunca usa a Cultura Fast como sinal.
 *  - §05: Gazeta casa determinística pela cabeça real do iptv-org (TVGazeta.br).
 */
class ChannelNumberingEtapa17Test {

    private fun ent(
        id: String,
        name: String,
        streams: Int = 3,
        number: Int = 0
    ) = ChannelNumberEntity(
        number = number,
        channelId = id,
        channelName = name,
        country = "BR",
        categories = "general",
        logoUrl = null,
        isNsfw = false,
        streamCount = streams
    )

    @Test
    fun gazeta_casa_pela_cabeca_real_do_iptv_org() {
        val catalogo = listOf(
            ent("RedeGlobo.br", "Globo"),
            ent("TVGazeta.br", "TV Gazeta", streams = 6),
            ent("tv_centro_sul_br", "TV Centro Sul", streams = 1),
            ent("TVLitoralRN.br", "TV Litoral RN", streams = 1)
        )
        val byNum = ChannelNumbering.resolveFixedEntities(catalogo, "NACIONAL").first
            .associateBy { it.number }
        assertEquals("TVGazeta.br", byNum[5]?.channelId)
    }

    @Test
    fun tv_cultura_nunca_usa_a_cultura_fast() {
        // Cabeça principal ausente; apenas uma variante que casaria por palavra-chave
        // ("TV Cultura Fast" contém "tv cultura") — deve ser bloqueada pela posição 7.
        val catalogo = listOf(
            ent("RedeGlobo.br", "Globo"),
            ent("culturafast.br", "TV Cultura Fast"),
            ent("tv_centro_sul_br", "TV Centro Sul", streams = 1),
            ent("TVLitoralRN.br", "TV Litoral RN", streams = 1)
        )
        val byNum = ChannelNumbering.resolveFixedEntities(catalogo, "NACIONAL").first
            .associateBy { it.number }
        assertNull("posição 7 deve ficar ausente em vez de usar a Cultura Fast", byNum[7])
    }

    @Test
    fun tv_cultura_principal_tem_precedencia_sobre_a_cultura_fast() {
        val catalogo = listOf(
            ent("RedeGlobo.br", "Globo"),
            ent("TVCultura.br", "TV Cultura", streams = 10),
            ent("culturafast.br", "TV Cultura Fast"),
            ent("tv_centro_sul_br", "TV Centro Sul", streams = 1),
            ent("TVLitoralRN.br", "TV Litoral RN", streams = 1)
        )
        val byNum = ChannelNumbering.resolveFixedEntities(catalogo, "NACIONAL").first
            .associateBy { it.number }
        assertEquals("TVCultura.br", byNum[7]?.channelId)
    }

    private fun grade(vararg items: ChannelNumberEntity) = items.toList()

    @Test
    fun familia_caze_tv_permanece_consecutiva() {
        val ordered = ChannelNumbering.orderGrade(
            grade(
                ent("RedeGlobo.br", "Globo", number = 1),
                ent("rdc:cazetv3", "Cazé TV 3", number = 30),
                ent("rdc:cazetv", "Cazé TV", number = 31),
                ent("rdc:cazetv2", "Cazé TV 2", number = 32),
                ent("rdc:axn", "AXN", number = 33)
            ),
            setOf("rdc:cazetv", "rdc:cazetv2", "rdc:cazetv3", "rdc:axn")
        )
        val caze = ordered.filter { it.channelName.startsWith("Cazé TV") }
        assertEquals(3, caze.size)
        // Consecutivas e na ordem natural (base -> 2 -> 3).
        assertEquals(listOf("Cazé TV", "Cazé TV 2", "Cazé TV 3"), caze.map { it.channelName })
        val nums = caze.map { it.number }
        assertTrue("famílias devem ser numeradas em sequência", nums == (nums.first()..nums.first() + 2).toList())
    }

    @Test
    fun familia_premiere_permanece_consecutiva() {
        val ordered = ChannelNumbering.orderGrade(
            grade(
                ent("RedeGlobo.br", "Globo", number = 1),
                ent("rdc:premiere8", "Premiere 8", number = 40),
                ent("rdc:premiereclubes", "Premiere", number = 41),
                ent("rdc:premiere2", "Premiere 2", number = 42),
                ent("rdc:woohoo", "WooHoo", number = 43)
            ),
            setOf("rdc:premiereclubes", "rdc:premiere2", "rdc:premiere8", "rdc:woohoo")
        )
        val premiere = ordered.filter { it.channelName.startsWith("Premiere") }
        assertEquals(listOf("Premiere", "Premiere 2", "Premiere 8"), premiere.map { it.channelName })
        val nums = premiere.map { it.number }
        assertTrue(nums == (nums.first()..nums.first() + 2).toList())
    }
}
