package com.zap.tv.util

import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 18: o pertencimento à grade é decidido pela identidade (ledger aprovado),
 * nunca pela existência de stream.
 *
 *  - Canal aprovado permanece presente após perder o stream.
 *  - Canal NÃO aprovado não entra automaticamente (mesmo com stream).
 *  - Registro RDC catalog-only (sem stream) permanece na grade.
 *  - Posições fixas 1-9 existem independentemente da transmissão.
 *  - Sem ledger (`approvedIds` vazio) o comportamento legado é preservado.
 */
class ChannelNumberingApprovedLedgerTest {

    private fun ch(id: String, name: String, category: String = "general") = ApiChannelDto(
        id = id, name = name, country = "BR", categories = listOf(category),
        is_nsfw = false, owners = emptyList()
    )

    private fun stream(id: String) = ApiStreamDto(
        channel = id, feed = null, title = "HD",
        url = "https://cdn.exemplo.test/$id.m3u8",
        referrer = null, user_agent = null, quality = "HD", label = "HD"
    )

    private fun numbered(
        channels: List<ApiChannelDto>,
        streams: List<ApiStreamDto>,
        approved: Set<String>,
        catalogOnly: Set<String> = emptySet()
    ) = ChannelNumbering.buildNumberedChannels(
        channels, streams, emptyList(), "NACIONAL", catalogOnly, approved
    )

    @Test
    fun canal_aprovado_permanece_apos_perder_o_stream() {
        val result = numbered(listOf(ch("CanalX.br", "Canal X")), emptyList(), setOf("CanalX.br"))
        val c = result.firstOrNull { it.channelId == "CanalX.br" }
        assertTrue("canal aprovado deve continuar presente", c != null)
        assertFalse("sem stream, não é reproduzível", c!!.isAvailable)
        assertEquals(0, c.streamCount)
        assertTrue("deve estar numerado fora das posições fixas", c.number > 9)
    }

    @Test
    fun canal_nao_aprovado_com_stream_nao_entra_automaticamente() {
        // Ledger informado (não vazio) => a presença de stream NÃO admite sozinho.
        val result = numbered(
            listOf(ch("NaoAprovado.br", "Não Aprovado")),
            listOf(stream("NaoAprovado.br")),
            setOf("OutroAprovado.br")
        )
        assertNull(result.firstOrNull { it.channelId == "NaoAprovado.br" })
    }

    @Test
    fun registro_rdc_catalog_only_sem_stream_permanece_na_grade() {
        val result = numbered(
            listOf(ch("rdc:exemplo", "Exemplo")),
            emptyList(),
            setOf("OutroAprovado.br"),
            catalogOnly = setOf("rdc:exemplo")
        )
        val c = result.firstOrNull { it.channelId == "rdc:exemplo" }
        assertTrue("registro catalog-only deve permanecer", c != null)
        assertFalse(c!!.isAvailable)
    }

    @Test
    fun posicao_fixa_sobrevive_sem_stream_quando_identidade_aprovada() {
        // Globo perdeu o stream e virou catalog-only, mas a identidade é aprovada
        // (fixedApprovedIds / ledger). A posição 1 NÃO pode desaparecer.
        val result = numbered(
            listOf(ch("RedeGlobo.br", "Globo")),
            emptyList(),
            setOf("RedeGlobo.br", "tv_centro_sul_br"),
            catalogOnly = setOf("RedeGlobo.br")
        )
        val one = result.firstOrNull { it.number == 1 }
        assertEquals("RedeGlobo.br", one?.channelId)
        assertFalse("posição fixa sem stream não é reproduzível até haver fonte", one!!.isAvailable)
        // Posição 8 (TV Centro Sul) preservada independentemente.
        assertEquals("tv_centro_sul_br", result.firstOrNull { it.number == 8 }?.channelId)
    }

    @Test
    fun catalog_only_nao_aprovado_nao_ocupa_posicao_fixa() {
        // `rdc:globo` NÃO está no ledger: permanece na grade, mas não assume a posição 1.
        val result = numbered(
            listOf(ch("rdc:globo", "Globo")),
            emptyList(),
            setOf("OutroAprovado.br"),
            catalogOnly = setOf("rdc:globo")
        )
        assertNull(result.firstOrNull { it.number == 1 })
        assertTrue(result.any { it.channelId == "rdc:globo" })
    }

    @Test
    fun sem_ledger_preserva_comportamento_legado() {
        // approvedIds vazio => regra anterior (stream ou catalog-only).
        val comStream = numbered(
            listOf(ch("ComStream.br", "Com Stream")),
            listOf(stream("ComStream.br")),
            emptySet()
        )
        assertTrue(comStream.any { it.channelId == "ComStream.br" })

        val semStream = numbered(
            listOf(ch("SemStream.br", "Sem Stream")),
            emptyList(),
            emptySet()
        )
        assertFalse(semStream.any { it.channelId == "SemStream.br" })
    }

    @Test
    fun segunda_sincronizacao_nao_remove_canais_aprovados() {
        val channels = listOf(ch("A.br", "A"), ch("B.br", "B"), ch("C.br", "C"))
        val approved = setOf("A.br", "B.br", "C.br")
        val primeira = numbered(channels, emptyList(), approved).map { it.channelId }.toSet()
        val segunda = numbered(channels, emptyList(), approved).map { it.channelId }.toSet()
        assertTrue(primeira.containsAll(setOf("A.br", "B.br", "C.br")))
        assertEquals(primeira, segunda)
    }

    @Test
    fun aprovacao_futura_nao_duplica_identidade() {
        // Identidade simultaneamente aprovada e catalog-only gera UMA única entrada.
        val result = numbered(
            listOf(ch("A.br", "A")),
            emptyList(),
            setOf("A.br"),
            catalogOnly = setOf("A.br")
        )
        assertEquals(1, result.count { it.channelId == "A.br" })
    }
}
