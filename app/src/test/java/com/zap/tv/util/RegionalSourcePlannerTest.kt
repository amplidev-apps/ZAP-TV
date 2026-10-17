package com.zap.tv.util

import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.reidoscanais.RdcEmbedDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 11 §07: seleção da transmissão regional e fallback entre afiliadas.
 * Usa apenas fontes simuladas/verificáveis (provedores como a API os informa).
 * NÃO prova reprodução real — apenas a ordem determinística das fontes.
 */
class RegionalSourcePlannerTest {

    private fun stream(channelId: String, url: String = "https://cdn.test/$channelId.m3u8") = ApiStreamDto(
        channel = channelId, feed = null, title = "HD", url = url,
        referrer = null, user_agent = null, quality = "HD", label = "HD"
    )

    private fun globoEmbed(ufName: String, slug: String) =
        RdcEmbedDto(provider = "Globo $ufName", quality = "FULL HD", embed_url = "https://rdcanais.org/$slug")

    private fun nationalEmbed(slug: String) =
        RdcEmbedDto(provider = "Servidor Premium", quality = "FULL HD", embed_url = "https://rdcanais.org/$slug")

    private val globoHead = stream("RedeGlobo.br")

    private fun plan(uf: String, embeds: List<RdcEmbedDto>, streams: List<ApiStreamDto> = listOf(globoHead)) =
        RegionalSourcePlanner.planFixedNetwork(
            network = "Globo",
            uf = uf,
            entityChannelId = "RedeGlobo.br",
            identityLabel = "Globo",
            directStreams = streams,
            embeds = embeds
        )

    @Test
    fun ceara_seleciona_afiliada_cearense_quando_disponivel() {
        val p = plan(
            "CE",
            listOf(
                globoEmbed("São Paulo", "globosp"),
                globoEmbed("Rio de Janeiro", "globorj"),
                globoEmbed("Ceará", "globoce")
            )
        )
        val first = p.attempts.first()
        assertEquals(SourceRegionKind.SELECTED_UF, first.source.regionKind)
        assertEquals("CE", first.source.region)
        assertEquals("https://rdcanais.org/globoce", first.embed?.embed_url)
    }

    @Test
    fun sao_paulo_seleciona_afiliada_paulista() {
        val p = plan("SP", listOf(globoEmbed("Ceará", "globoce"), globoEmbed("São Paulo", "globosp")))
        val first = p.attempts.first()
        assertEquals(SourceRegionKind.SELECTED_UF, first.source.regionKind)
        assertEquals("SP", first.source.region)
        assertEquals("https://rdcanais.org/globosp", first.embed?.embed_url)
    }

    @Test
    fun rio_grande_do_sul_seleciona_afiliada_correspondente() {
        val p = plan(
            "RS",
            listOf(globoEmbed("São Paulo", "globosp"), globoEmbed("Rio Grande do Sul", "globors"))
        )
        val first = p.attempts.first()
        assertEquals("RS", first.source.region)
        assertEquals("https://rdcanais.org/globors", first.embed?.embed_url)
    }

    @Test
    fun afiliada_da_uf_tem_prioridade_sobre_cabeca_nacional() {
        val p = plan("CE", listOf(globoEmbed("Ceará", "globoce")), streams = listOf(globoHead))
        // Primeira = UF (web), segunda = cabeça nacional (stream direto).
        assertEquals(SourceRegionKind.SELECTED_UF, p.attempts[0].source.regionKind)
        assertEquals(PlaybackMechanism.WEB_EMBED, p.attempts[0].mechanism)
        assertEquals(SourceRegionKind.NATIONAL_HEAD, p.attempts[1].source.regionKind)
        assertEquals(PlaybackMechanism.DIRECT_STREAM, p.attempts[1].mechanism)
    }

    @Test
    fun identidade_exibida_corresponde_ao_canal_e_fonte_e_a_real() {
        val p = plan("CE", listOf(globoEmbed("Ceará", "globoce")))
        val src = p.attempts.first().source
        assertEquals("Globo", src.identityLabel)      // identidade do canal 1
        assertEquals("TV Verdes Mares", src.label)    // fonte real (afiliada CE)
        assertEquals("CE", src.region)
    }

    @Test
    fun fallback_para_cabeca_nacional_quando_sem_afiliada_da_uf() {
        // SBT não possui afiliadas regionais na Fonte A: usa cabeça nacional.
        val p = RegionalSourcePlanner.planFixedNetwork(
            network = "SBT",
            uf = "CE",
            entityChannelId = "SBTNacional.br",
            identityLabel = "SBT",
            directStreams = listOf(stream("SBTNacional.br")),
            embeds = listOf(nationalEmbed("sbt"))
        )
        assertEquals(SourceRegionKind.NATIONAL_HEAD, p.attempts[0].source.regionKind)
        assertEquals(PlaybackMechanism.DIRECT_STREAM, p.attempts[0].mechanism)
        assertTrue(p.attempts.any { it.mechanism == PlaybackMechanism.WEB_EMBED })
    }

    @Test
    fun fallback_para_referencia_sp_rj_quando_sem_afiliada_da_uf() {
        val p = plan(
            "CE",
            listOf(globoEmbed("São Paulo", "globosp"), globoEmbed("Rio de Janeiro", "globorj"))
        )
        // Etapa 15 §03 (Globo): sem CE, tenta SP -> RJ ANTES da cabeça nacional.
        assertEquals(SourceRegionKind.REFERENCE_SP_RJ, p.attempts[0].source.regionKind)
        assertEquals("SP", p.attempts[0].source.region)
        assertEquals(SourceRegionKind.REFERENCE_SP_RJ, p.attempts[1].source.regionKind)
        assertEquals("RJ", p.attempts[1].source.region)
        assertEquals(SourceRegionKind.NATIONAL_HEAD, p.attempts[2].source.regionKind)
    }

    @Test
    fun fallback_para_outra_afiliada_da_mesma_rede() {
        val p = plan("CE", listOf(globoEmbed("Bahia", "globoba")), streams = emptyList())
        val other = p.attempts.first { it.source.regionKind == SourceRegionKind.OTHER_AFFILIATE }
        assertEquals("BA", other.source.region)
        assertEquals("https://rdcanais.org/globoba", other.embed?.embed_url)
    }

    @Test
    fun ausencia_de_fontes_mantem_plano_vazio_indisponivel() {
        val p = plan("CE", emptyList(), streams = emptyList())
        assertTrue(p.isEmpty)
        assertEquals(PlaybackMechanism.UNAVAILABLE, p.mechanism)
    }

    @Test
    fun determinismo_e_sem_repeticoes_de_fonte() {
        val embeds = listOf(
            globoEmbed("Ceará", "globoce"),
            globoEmbed("São Paulo", "globosp"),
            globoEmbed("Rio de Janeiro", "globorj"),
            globoEmbed("Bahia", "globoba"),
            globoEmbed("Paraná", "globopr")
        )
        val a = plan("CE", embeds)
        val b = plan("CE", embeds)
        assertEquals(a.attempts.map { it.embed?.embed_url ?: it.streams.firstOrNull()?.url },
            b.attempts.map { it.embed?.embed_url ?: it.streams.firstOrNull()?.url })
        val urls = a.attempts.mapNotNull { it.embed?.embed_url }
        assertEquals(urls.size, urls.distinct().size)
    }

    @Test
    fun outras_afiliadas_sao_limitadas() {
        // 6 afiliadas com nomes de estado verificáveis (além de CE).
        val all = listOf(
            globoEmbed("Ceará", "globoce"),
            globoEmbed("Bahia", "globoba"),
            globoEmbed("Paraná", "globopr"),
            globoEmbed("Minas Gerais", "globomg"),
            globoEmbed("Pernambuco", "globope"),
            globoEmbed("Amazonas", "globoam"),
            globoEmbed("Pará", "globopa")
        )
        val p = plan("CE", all, streams = emptyList())
        val otherRegions = p.attempts
            .filter { it.source.regionKind == SourceRegionKind.OTHER_AFFILIATE && it.source.region != null }
            .mapNotNull { it.source.region }
            .distinct()
        assertTrue("outras afiliadas limitadas", otherRegions.size <= RegionalSourcePlanner.MAX_OTHER_AFFILIATES)
        assertTrue(p.attempts.size <= RegionalSourcePlanner.MAX_ATTEMPTS)
    }

    @Test
    fun rede_sem_embeds_usa_apenas_stream_da_cabeca() {
        val p = plan("CE", emptyList(), streams = listOf(globoHead))
        assertEquals(1, p.attempts.size)
        assertEquals(SourceRegionKind.NATIONAL_HEAD, p.attempts[0].source.regionKind)
        assertNotNull(p.attempts[0].streams.firstOrNull())
    }

    @Test
    fun posicoes_8_e_9_nao_sao_regionalizadas() {
        assertEquals(null, ChannelNumbering.networkForFixedPosition(8))
        assertEquals("TV Litoral RN", ChannelNumbering.networkForFixedPosition(9))
        assertEquals("Globo", ChannelNumbering.networkForFixedPosition(1))
    }

    // ------------------------------------------------------------------
    // Etapa 15 §03 / 20 §05 — fallback regional da Globo:
    // UF -> SP -> RJ -> demais afiliadas -> cabeça nacional.
    // ------------------------------------------------------------------

    @Test
    fun globo_ordem_uf_sp_rj_demais_afiliadas_cabeca() {
        val p = plan(
            "CE",
            listOf(
                globoEmbed("Ceará", "globoce"),
                globoEmbed("São Paulo", "globosp"),
                globoEmbed("Rio de Janeiro", "globorj"),
                globoEmbed("Bahia", "globoba")
            )
        )
        // Etapa 20 §05: a cabeça nacional (NACIONAL) vai DEPOIS das demais afiliadas.
        assertEquals(
            listOf("CE", "SP", "RJ", "BA", "NACIONAL"),
            p.attempts.map { it.source.region }
        )
    }

    @Test
    fun globo_cabeca_nacional_nao_precede_demais_afiliadas() {
        val p = plan(
            "CE",
            listOf(globoEmbed("Ceará", "globoce"), globoEmbed("Bahia", "globoba"))
        )
        val headIdx = p.attempts.indexOfFirst { it.source.regionKind == SourceRegionKind.NATIONAL_HEAD }
        val otherIdx = p.attempts.indexOfFirst { it.source.regionKind == SourceRegionKind.OTHER_AFFILIATE }
        assertTrue("cabeça existe", headIdx >= 0)
        assertTrue("outra afiliada existe", otherIdx >= 0)
        assertTrue("cabeça nacional não preceder demais afiliadas", otherIdx < headIdx)
    }

    @Test
    fun globo_sem_fonte_da_uf_usa_sp_antes_da_cabeca_nacional() {
        val p = plan("CE", listOf(globoEmbed("São Paulo", "globosp")), streams = listOf(globoHead))
        assertEquals(SourceRegionKind.REFERENCE_SP_RJ, p.attempts[0].source.regionKind)
        assertEquals("SP", p.attempts[0].source.region)
        assertEquals(SourceRegionKind.NATIONAL_HEAD, p.attempts[1].source.regionKind)
    }

    @Test
    fun globo_sem_sp_cai_para_outra_afiliada() {
        val p = plan("CE", listOf(globoEmbed("Bahia", "globoba")), streams = emptyList())
        assertEquals(SourceRegionKind.OTHER_AFFILIATE, p.attempts[0].source.regionKind)
        assertEquals("BA", p.attempts[0].source.region)
    }

    // ------------------------------------------------------------------
    // Etapa 15 §02 — Band, Record e Gazeta com IPTV-ORG como fonte principal.
    // ------------------------------------------------------------------

    private fun fixedNetworkPlan(
        network: String,
        entityId: String,
        streams: List<ApiStreamDto>,
        embeds: List<RdcEmbedDto>
    ) = RegionalSourcePlanner.planFixedNetwork(
        network = network,
        uf = "NACIONAL",
        entityChannelId = entityId,
        identityLabel = network,
        directStreams = streams,
        embeds = embeds
    )

    @Test
    fun band_record_gazeta_priorizam_iptv_org_antes_do_embed() {
        assertTrue(SourcePreferenceRegistry.isIptvPrimaryNetwork("Band"))
        assertTrue(SourcePreferenceRegistry.isIptvPrimaryNetwork("Record"))
        assertTrue(SourcePreferenceRegistry.isIptvPrimaryNetwork("Gazeta"))

        val band = fixedNetworkPlan(
            "Band", "Band.br", listOf(stream("Band.br")), listOf(nationalEmbed("band"))
        )
        assertEquals(PlaybackMechanism.DIRECT_STREAM, band.attempts.first().mechanism)
        val bandEmbedIdx = band.attempts.indexOfFirst { it.mechanism == PlaybackMechanism.WEB_EMBED }
        assertTrue("embed nunca antes do stream (Band)", bandEmbedIdx > 0)

        val record = fixedNetworkPlan(
            "Record", "Record.br", listOf(stream("Record.br")), listOf(nationalEmbed("record"))
        )
        assertEquals(PlaybackMechanism.DIRECT_STREAM, record.attempts.first().mechanism)
        val recordEmbedIdx = record.attempts.indexOfFirst { it.mechanism == PlaybackMechanism.WEB_EMBED }
        assertTrue("embed nunca antes do stream (Record)", recordEmbedIdx > 0)
    }

    @Test
    fun gazeta_usa_somente_stream_do_iptv_org() {
        val p = fixedNetworkPlan("Gazeta", "TVGazeta.br", listOf(stream("TVGazeta.br")), emptyList())
        assertEquals(1, p.attempts.size)
        assertEquals(PlaybackMechanism.DIRECT_STREAM, p.attempts[0].mechanism)
    }
}
