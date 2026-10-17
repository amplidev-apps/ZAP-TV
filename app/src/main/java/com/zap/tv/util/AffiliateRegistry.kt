package com.zap.tv.util

/**
 * Registro centralizado de emissoras nacionais e afiliadas regionais.
 *
 * Fonte única de verdade da regionalização (Etapa 05). Todas as associações
 * rede/UF -> identificador foram verificadas contra as fontes reais:
 *  - `assets/m3u_catalog.json` (IDs gerados por M3uChannelMapper.generateChannelId);
 *  - catálogo iptv-org (IDs estáveis como `RedeGlobo.br`);
 *  - stream custom da TV Centro-Sul (StreamLock) e TV Litoral RN.
 *
 * Não há identificadores inventados: cada `channelId` abaixo existe em pelo menos
 * uma fonte reproduzível (ver docs/integracao-api-reidoscanais/07_INVENTARIO_AFILIADAS.md).
 *
 * EPG: a API Rei dos Canais fornece EPG nacional por rede (ids RDC: globo, sbt,
 * record, redetv, band, tvcultura, tvbrasil). Afiliadas regionais usam o EPG
 * nacional da respectiva rede (não há EPG por praça).
 */
object AffiliateRegistry {

    /** As 27 unidades federativas suportadas. */
    val ALL_UFS: List<String> = listOf(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
        "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
        "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    ).sorted()

    enum class Source { M3U, IPTV_ORG, CUSTOM }

    enum class Confidence { ALTA, MEDIA, PENDENTE }

    /**
     * @param network   rede nacional (deve casar com FixedChannel.displayName)
     * @param uf        UF de cobertura ou "NACIONAL" para cabeças de rede
     * @param name      nome de exibição da emissora
     * @param channelId identificador estável real (nunca inventado)
     * @param source    origem do identificador
     * @param playable  reprodução verificada (stream HLS/TS confirmado)
     * @param epgId     id de EPG conhecido (nacional da rede), ou null
     * @param isHead    cabeça de rede nacional (não é afiliada regional)
     * @param confidence grau de confiança da associação rede/UF
     */
    data class Affiliate(
        val network: String,
        val uf: String,
        val name: String,
        val channelId: String,
        val source: Source,
        val playable: Boolean,
        val epgId: String?,
        val isHead: Boolean,
        val confidence: Confidence = Confidence.ALTA
    )

    // ---------------------------------------------------------------------
    // Cabeças de rede nacionais (reproduzíveis, identificadores estáveis).
    // IDs = exatamente os usados por FixedChannel.exactId quando aplicável.
    // ---------------------------------------------------------------------
    private val heads: List<Affiliate> = listOf(
        Affiliate("Globo", "NACIONAL", "Globo", "RedeGlobo.br", Source.IPTV_ORG, true, "globo", true),
        Affiliate("SBT", "NACIONAL", "SBT", "SBTNacional.br", Source.IPTV_ORG, true, "sbt", true),
        Affiliate("Record", "NACIONAL", "Record", "Record.br", Source.IPTV_ORG, true, "record", true),
        Affiliate("RedeTV!", "NACIONAL", "RedeTV!", "RedeTV.br", Source.IPTV_ORG, true, "redetv", true),
        Affiliate("Band", "NACIONAL", "Band", "Band.br", Source.IPTV_ORG, true, "band", true),
        Affiliate("Gazeta", "NACIONAL", "Gazeta", "TVGazeta.br", Source.IPTV_ORG, true, null, true),
        Affiliate("TV Cultura", "NACIONAL", "TV Cultura", "TVCultura.br", Source.IPTV_ORG, true, "tvcultura", true),
        Affiliate("TV Brasil", "NACIONAL", "TV Brasil", "TVBrasil.br", Source.IPTV_ORG, true, "tvbrasil", true),
        Affiliate("TV Litoral RN", "NACIONAL", "TV Litoral RN", "TVLitoralRN.br", Source.IPTV_ORG, true, null, true),
        Affiliate("TV Centro Sul", "NACIONAL", "TV Centro Sul", "tv_centro_sul_br", Source.CUSTOM, true, null, true)
    )

    // ---------------------------------------------------------------------
    // Afiliadas regionais verificadas (IDs reais do m3u_catalog.json, exceto
    // quando indicado). Redes sem afiliação (RedeTV!/TV Cultura/Gazeta/TV
    // Brasil) não possuem entradas regionais — operam pela cabeça nacional.
    // ---------------------------------------------------------------------
    private val regionals: List<Affiliate> = buildList {
        // ---- Globo: 27/27 UFs ----
        add(Affiliate("Globo", "AC", "TV Rede Amazônica Rio Branco", "globo_rede_amazonica_rio_branco.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "AL", "TV Gazeta de Alagoas", "globo_tv_gazeta_alagoas.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "AP", "TV Rede Amazônica Amapá", "globo_rede_amazonica_amapa.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "AM", "TV Rede Amazônica Manaus", "globo_rede_amazonica_manaus.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "BA", "TV Bahia", "globo_rede_bahia.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "CE", "TV Verdes Mares", "globo_tv_verdes_mares.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "DF", "TV Globo Brasília", "globo_brasilia.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "ES", "TV Gazeta Vitória", "globo_tv_gazeta_vitoria.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "GO", "TV Anhanguera Goiânia", "globo_tv_anhanguera.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "MA", "TV Mirante São Luís", "globo_tv_mirante_sao_luis.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "MT", "TV Centro América", "globo_tv_centro_america.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "MS", "TV Morena", "globo_tv_morena.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "MG", "TV Globo Minas", "globo_minas.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "PA", "TV Liberal Belém", "globo_tv_liberal_belem.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "PB", "TV Cabo Branco", "globo_tv_cabo_branco.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "PR", "RPC TV Curitiba", "globo_rpc_tv_curitiba.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "PE", "TV Globo Nordeste", "globo_nordeste.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "PI", "TV Clube Teresina", "globo_tv_clube_teresina.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "RJ", "TV Globo Rio de Janeiro", "globo_rj.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "RN", "Inter TV Cabugi", "globo_inter_tv_cabugi.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "RS", "RBS TV Porto Alegre", "globo_rbs_tv_porto_alegre.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "RO", "TV Rede Amazônica Porto Velho", "globo_rede_amazonica_porto_velho.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "RR", "TV Rede Amazônica Boa Vista", "globo_rede_amazonica_boa_vista.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "SC", "NSC TV Florianópolis", "globo_nsc_tv_florianopolis.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "SP", "TV Globo São Paulo", "globo_sp.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "SE", "TV Sergipe", "globo_tv_sergipe.br", Source.M3U, true, "globo", false))
        add(Affiliate("Globo", "TO", "TV Anhanguera Palmas", "globo_tv_anhanguera_palmas.br", Source.M3U, true, "globo", false))

        // ---- SBT: 6/27 UFs ----
        add(Affiliate("SBT", "RJ", "SBT Rio de Janeiro", "sbt_rj.br", Source.M3U, true, "sbt", false))
        add(Affiliate("SBT", "SP", "SBT São Paulo", "sbt_sp.br", Source.M3U, true, "sbt", false))
        add(Affiliate("SBT", "RS", "SBT Rio Grande do Sul", "sbt_rs.br", Source.M3U, true, "sbt", false))
        add(Affiliate("SBT", "PR", "SBT Rede Massa", "sbt_rede_massa_pr.br", Source.M3U, true, "sbt", false))
        add(Affiliate("SBT", "MG", "SBT TV Alterosa", "sbt_tv_alterosa.br", Source.M3U, true, "sbt", false))
        add(Affiliate("SBT", "MT", "SBT Cuiabá", "sbtcuiaba.br", Source.M3U, true, "sbt", false))

        // ---- Record: 15/27 UFs ----
        add(Affiliate("Record", "RJ", "RecordTV Rio de Janeiro", "recordtv_rj.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "SP", "RecordTV São Paulo", "recordtv_sp.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "RS", "RecordTV Rio Grande do Sul", "recordtv_rs.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "BA", "RecordTV Itapoan", "recordtv_itapoan.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "AM", "RecordTV Manaus", "recordtv_manaus.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "PE", "RecordTV Guararapes", "recordtv_guararapes.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "PI", "RecordTV Piauí", "recordtv_piaui_a10.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "RR", "RecordTV Roraima", "recordtv_roraima.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "RO", "RecordTV SIC Rondônia", "recordtv_sic_rondonia.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "PA", "RecordTV Belém", "record_tvbelem.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "DF", "RecordTV Brasília", "record_tvbrasilia.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "GO", "RecordTV Goiás", "recordtv_goias.br", Source.M3U, true, "record", false))
        add(Affiliate("Record", "ES", "RecordTV Vila Velha", "recordtv_vila_real.br", Source.M3U, true, "record", false, Confidence.MEDIA))
        add(Affiliate("Record", "MG", "RecordTV Paranaíba Uberlândia", "recordtv_paranaiba_de_uberlandia.br", Source.M3U, true, "record", false, Confidence.MEDIA))
        add(Affiliate("Record", "PB", "RecordTV Paraíba (TV Correio)", "recordtv_paraiba_tv_correio.br", Source.M3U, true, "record", false, Confidence.MEDIA))

        // ---- Band: 1/27 UFs (SP) ----
        add(Affiliate("Band", "SP", "Band São Paulo", "band_sp.br", Source.M3U, true, "band", false))
    }

    val all: List<Affiliate> = heads + regionals

    private val byNetworkUf: Map<String, Affiliate> =
        regionals.associateBy { key(it.network, it.uf) }

    private val headByNetwork: Map<String, Affiliate> =
        heads.associateBy { it.network }

    private val byChannelId: Map<String, Affiliate> =
        all.associateBy { it.channelId.lowercase() }

    private fun key(network: String, uf: String) = "${network.uppercase()}::$uf"

    /** Redes nacionais com cabeça definida (usadas pelas posições fixas). */
    fun networks(): List<String> = listOf("Globo", "SBT", "Record", "RedeTV!", "Band", "Gazeta", "TV Cultura")

    /** Afiliada regional verificada de uma rede numa UF (ou null). */
    fun resolveRegional(network: String, uf: String): Affiliate? = byNetworkUf[key(network, uf)]

    /** Identificador real da afiliada regional, se existir. */
    fun resolveRegionalId(network: String, uf: String): String? = resolveRegional(network, uf)?.channelId

    /** Cabeça de rede nacional verificada. */
    fun head(network: String): Affiliate? = headByNetwork[network]

    /** Identificador real da cabeça de rede nacional. */
    fun headId(network: String): String? = headByNetwork[network]?.channelId

    /** EPG nacional conhecido da rede (id RDC) ou null. */
    fun epgId(network: String): String? = headByNetwork[network]?.epgId

    /**
     * Ordem determinística de candidatos para uma posição fixa de rede:
     * 1) afiliada regional da UF; 2) cabeça de rede nacional.
     */
    fun candidateIds(network: String, uf: String): List<String> {
        val ids = mutableListOf<String>()
        if (uf != "NACIONAL") resolveRegionalId(network, uf)?.let { ids += it }
        headId(network)?.let { if (it !in ids) ids += it }
        return ids
    }

    /** Emissora associada a um channelId real (case-insensitive). */
    fun affiliateForChannelId(channelId: String): Affiliate? = byChannelId[channelId.lowercase()]

    /**
     * UF associada a um channelId, somente para afiliadas regionais verificadas.
     * Cabeças nacionais retornam null (não pertencem a uma UF específica).
     */
    fun ufForChannelId(channelId: String): String? =
        byChannelId[channelId.lowercase()]?.takeIf { !it.isHead }?.uf
}
