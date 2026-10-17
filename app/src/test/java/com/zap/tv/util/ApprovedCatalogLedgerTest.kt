package com.zap.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 18: ledger persistente do catálogo complementar aprovado.
 * Cobre parse/serialize determinístico e união monotônica (nunca remove identidade).
 */
class ApprovedCatalogLedgerTest {

    @Test
    fun ledger_vazio_quando_nao_persistido() {
        assertEquals(emptySet<String>(), ApprovedCatalogLedger.parse(null))
        assertEquals(emptySet<String>(), ApprovedCatalogLedger.parse(""))
        assertEquals(emptySet<String>(), ApprovedCatalogLedger.parse("   \n\n"))
    }

    @Test
    fun parse_descarta_linhas_em_branco_e_espacos() {
        val ids = ApprovedCatalogLedger.parse(" RedeGlobo.br \n\nTVLitoralRN.br\n")
        assertEquals(setOf("RedeGlobo.br", "TVLitoralRN.br"), ids)
    }

    @Test
    fun serialize_e_estavel_e_round_trip() {
        val ids = setOf("b.br", "a.br", "c.br")
        val raw = ApprovedCatalogLedger.serialize(ids)
        assertEquals("a.br\nb.br\nc.br", raw)
        assertEquals(ids, ApprovedCatalogLedger.parse(raw))
    }

    @Test
    fun merge_e_monotonico_e_sem_duplicatas() {
        val anterior = setOf("RedeGlobo.br", "TVLitoralRN.br")
        val atual = setOf("TVLitoralRN.br", "Novo.br")
        val semente = setOf("Semente.br")
        val merged = ApprovedCatalogLedger.merge(anterior, atual, semente)
        assertEquals(setOf("RedeGlobo.br", "TVLitoralRN.br", "Novo.br", "Semente.br"), merged)
        // Nunca remove identidades já aprovadas.
        assertTrue(merged.containsAll(anterior))
        assertEquals(merged.size, merged.toList().distinct().size)
    }

    @Test
    fun merge_ignora_identificadores_em_branco() {
        assertEquals(setOf("a.br"), ApprovedCatalogLedger.merge(listOf("a.br", "", "  ")))
    }
}
