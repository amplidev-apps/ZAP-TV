package com.zap.tv.data.reidoscanais

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do mapeamento de reprodução da Fonte A (Etapa 08).
 * Garante: cabeças de rede resolvem para o iptv-org verificado; casamento por nome
 * apenas dentro do índice do país; associações regionais extraídas da própria API;
 * normalização tolerante a acentos.
 */
class RdcCatalogMapperTest {

    private fun channel(
        id: String,
        name: String,
        embeds: List<RdcEmbedDto> = emptyList()
    ) = RdcChannelDto(id = id, name = name, embeds = embeds)

    @Test
    fun cabeca_de_rede_resolve_para_iptv_verificado() {
        assertEquals("RedeGlobo.br", RdcCatalogMapper.playbackIdFor("globo", "Globo", emptyMap()))
        assertEquals("SBTNacional.br", RdcCatalogMapper.playbackIdFor("sbt", "SBT", emptyMap()))
        assertEquals("Record.br", RdcCatalogMapper.playbackIdFor("record", "Record", emptyMap()))
        assertEquals("RedeTV.br", RdcCatalogMapper.playbackIdFor("redetv", "RedeTV!", emptyMap()))
        assertEquals("Band.br", RdcCatalogMapper.playbackIdFor("band", "Band", emptyMap()))
        assertEquals("TVCultura.br", RdcCatalogMapper.playbackIdFor("tvcultura", "TV Cultura", emptyMap()))
    }

    @Test
    fun cabeca_de_rede_ignora_indice_de_nomes() {
        // Mesmo havendo um "globo" no índice do país, a cabeça verificada vence.
        val index = mapOf("globo" to "Globo.pt")
        assertEquals("RedeGlobo.br", RdcCatalogMapper.playbackIdFor("globo", "Globo", index))
    }

    @Test
    fun canal_comum_casa_por_nome_normalizado_identico() {
        val index = mapOf("cnnbrasil" to "CNNBrasil.br", "axn" to "AXN.br")
        assertEquals("CNNBrasil.br", RdcCatalogMapper.playbackIdFor("cnn-brasil", "CNN Brasil", index))
        assertEquals("AXN.br", RdcCatalogMapper.playbackIdFor("axn", "AXN", index))
    }

    @Test
    fun sem_correspondencia_retorna_null_para_ser_catalogo_apenas() {
        val index = mapOf("axn" to "AXN.br")
        assertNull(RdcCatalogMapper.playbackIdFor("premiere2", "Premiere 2", index))
    }

    @Test
    fun normalizacao_remove_acentos_e_pontuacao() {
        assertEquals("tvaparecida", RdcCatalogMapper.normalize("TV Aparecida"))
        assertEquals("sao", RdcCatalogMapper.normalize("São"))
        assertEquals("e", RdcCatalogMapper.normalize("E!"))
        assertEquals("redetv", RdcCatalogMapper.normalize("RedeTV!"))
    }

    @Test
    fun identifica_apenas_cabecas_de_rede_conhecidas() {
        assertTrue(RdcCatalogMapper.isOpenNetworkHead("globo"))
        assertFalse(RdcCatalogMapper.isOpenNetworkHead("premiere2"))
    }

    @Test
    fun extrai_identidades_regionais_da_propria_api() {
        val channels = listOf(
            channel(
                id = "globo",
                name = "Globo",
                embeds = listOf(
                    RdcEmbedDto(provider = "Globo Ceará", embed_url = "https://rdcanais.org/globoce"),
                    RdcEmbedDto(provider = "Globo São Paulo", embed_url = "https://rdcanais.org/globosp"),
                    RdcEmbedDto(provider = "Globo Nacional", embed_url = "https://rdcanais.org/globo")
                )
            )
        )
        val byUf = RdcCatalogMapper.globoRegionalSlugByUf(channels)
        assertEquals("globoce", byUf["CE"])
        assertEquals("globosp", byUf["SP"])
        // "Globo Nacional" não é uma UF -> não entra.
        assertNull(byUf["Nacional"])
    }

    @Test
    fun nao_inventa_associacao_quando_provider_nao_e_estado() {
        val channels = listOf(
            channel(
                id = "globo",
                name = "Globo",
                embeds = listOf(RdcEmbedDto(provider = "Globo Premium", embed_url = "https://rdcanais.org/glbpremium"))
            )
        )
        assertTrue(RdcCatalogMapper.regionalIdentities(channels).isEmpty())
    }

    @Test
    fun identidades_regionais_sao_deduplicadas_por_uf() {
        val channels = listOf(
            channel(
                id = "globo",
                name = "Globo",
                embeds = listOf(
                    RdcEmbedDto(provider = "Globo Ceará", embed_url = "https://rdcanais.org/globoce"),
                    RdcEmbedDto(provider = "Globo Ceará", embed_url = "https://rdcanais.org/globoce2")
                )
            )
        )
        assertEquals(1, RdcCatalogMapper.regionalIdentities(channels).size)
    }
}
