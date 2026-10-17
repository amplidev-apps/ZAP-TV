package com.zap.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do registro centralizado de afiliadas (Etapa 05).
 * Somente associações verificadas — nenhum identificador inventado.
 * NÃO executados nesta etapa (compilação/testes adiados).
 */
class AffiliateRegistryTest {

    @Test
    fun globo_cobre_as_27_ufs_com_afiliada_regional() {
        for (uf in AffiliateRegistry.ALL_UFS) {
            val id = AffiliateRegistry.resolveRegionalId("Globo", uf)
            assertNotNull("Globo sem afiliada regional para $uf", id)
            assertTrue("ID vazio para Globo/$uf", id!!.isNotBlank())
        }
        assertEquals(27, AffiliateRegistry.ALL_UFS.size)
    }

    @Test
    fun sbt_cobre_as_ufs_verificadas_e_nenhuma_alem() {
        val cobertas = AffiliateRegistry.ALL_UFS.filter {
            AffiliateRegistry.resolveRegionalId("SBT", it) != null
        }.toSet()
        assertEquals(setOf("RJ", "SP", "RS", "PR", "MG", "MT"), cobertas)
        // UF sem afiliada não retorna nenhum ID (não usa outro estado).
        assertNull(AffiliateRegistry.resolveRegionalId("SBT", "CE"))
        assertNull(AffiliateRegistry.resolveRegionalId("SBT", "BA"))
    }

    @Test
    fun record_cobre_as_ufs_verificadas() {
        val cobertas = AffiliateRegistry.ALL_UFS.filter {
            AffiliateRegistry.resolveRegionalId("Record", it) != null
        }.toSet()
        assertEquals(
            setOf("RJ", "SP", "RS", "BA", "AM", "PE", "PI", "RR", "RO", "PA", "DF", "GO", "ES", "MG", "PB"),
            cobertas
        )
    }

    @Test
    fun band_cobre_apenas_sp() {
        val cobertas = AffiliateRegistry.ALL_UFS.filter {
            AffiliateRegistry.resolveRegionalId("Band", it) != null
        }.toSet()
        assertEquals(setOf("SP"), cobertas)
    }

    @Test
    fun cabecas_de_rede_nacionais_existem_e_sao_reproduziveis() {
        for (net in AffiliateRegistry.networks()) {
            val head = AffiliateRegistry.head(net)
            assertNotNull("Sem cabeça de rede para $net", head)
            assertTrue("Cabeça de rede não reproduzível: $net", head!!.playable)
            assertTrue("Cabeça de rede deveria ser nacional", head.isHead)
            assertNotNull("Cabeça de rede sem ID: $net", AffiliateRegistry.headId(net))
        }
    }

    @Test
    fun epg_nacional_conhecido_por_rede() {
        assertEquals("globo", AffiliateRegistry.epgId("Globo"))
        assertEquals("sbt", AffiliateRegistry.epgId("SBT"))
        assertEquals("record", AffiliateRegistry.epgId("Record"))
        assertEquals("band", AffiliateRegistry.epgId("Band"))
        assertEquals("redetv", AffiliateRegistry.epgId("RedeTV!"))
        assertEquals("tvcultura", AffiliateRegistry.epgId("TV Cultura"))
        // Gazeta não possui EPG conhecido na fonte.
        assertNull(AffiliateRegistry.epgId("Gazeta"))
    }

    @Test
    fun candidate_ids_priorizam_afiliada_da_uf_e_depois_cabeca() {
        // CE: Globo tem afiliada -> primeiro candidato é a afiliada, segundo a cabeça.
        val ce = AffiliateRegistry.candidateIds("Globo", "CE")
        assertEquals("globo_tv_verdes_mares.br", ce.first())
        assertEquals("RedeGlobo.br", ce.last())

        // CE sem afiliada SBT -> somente a cabeça nacional.
        val sbtCe = AffiliateRegistry.candidateIds("SBT", "CE")
        assertEquals(listOf("SBTNacional.br"), sbtCe)

        // NACIONAL -> somente a cabeça.
        assertEquals(listOf("RedeGlobo.br"), AffiliateRegistry.candidateIds("Globo", "NACIONAL"))
    }

    @Test
    fun uf_por_channel_id_somente_para_afiliadas_regionais() {
        assertEquals("CE", AffiliateRegistry.ufForChannelId("globo_tv_verdes_mares.br"))
        assertEquals("SP", AffiliateRegistry.ufForChannelId("globo_sp.br"))
        assertEquals("AM", AffiliateRegistry.ufForChannelId("recordtv_manaus.br"))
        // Cabeças nacionais não pertencem a uma UF.
        assertNull(AffiliateRegistry.ufForChannelId("RedeGlobo.br"))
        assertNull(AffiliateRegistry.ufForChannelId("SBTNacional.br"))
        // Desconhecido.
        assertNull(AffiliateRegistry.ufForChannelId("canal_inexistente.br"))
    }

    @Test
    fun todos_os_ids_sao_estaveis_e_nao_vazios() {
        for (aff in AffiliateRegistry.all) {
            assertTrue("ID vazio para ${aff.network}/${aff.uf}", aff.channelId.isNotBlank())
            assertTrue(
                "Afiliada regional deve ter UF válida: ${aff.network}/${aff.uf}",
                aff.isHead || aff.uf in AffiliateRegistry.ALL_UFS
            )
        }
        // Nenhuma afiliada regional pode estar marcada como cabeça de rede.
        assertTrue(AffiliateRegistry.all.filter { !it.isHead }.all { it.uf in AffiliateRegistry.ALL_UFS })
    }
}
