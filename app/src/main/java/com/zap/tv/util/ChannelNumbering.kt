package com.zap.tv.util

import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiLogoDto
import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.local.entity.ChannelNumberEntity

object ChannelNumbering {

    // Affiliates mapping UF -> preferred channel ids per network (completo 27 UFs - M3U como primario)
    val affiliateMap: Map<String, Map<String, String>> = mapOf(
        "Globo" to mapOf(
            "AC" to "globo_ac_rede_amaz_nica_rio_branco.br", "AL" to "globo_al_tv_gazeta_de_alagoas_macei.br",
            "AP" to "globo_ap_rede_amaz_nica_macap.br", "AM" to "globo_am_rede_amaz_nica_manaus.br",
            "BA" to "globo_ba_tv_bahia.br", "CE" to "globo_ce_tv_verdes_mares.br",
            "DF" to "globo_df.br", "ES" to "globo_es_tv_gazeta_vit_ria.br",
            "GO" to "globo_go_tv_anhanguera_goi_nia.br", "MA" to "globo_ma_tv_mirante_s_o_lu_s.br",
            "MT" to "globo_mt_tv_centro_am_rica.br", "MS" to "globo_ms_tv_morena.br",
            "MG" to "globo_mg_tv_integra_o_uberl_ndia.br", "PA" to "globo_pa_tv_liberal_bel_m.br",
            "PB" to "globo_pb_tv_cabo_branco.br", "PR" to "globo_pr_rpc_curitiba.br",
            "PE" to "globo_pe_globo_recife.br", "PI" to "globo_pi_tv_clube.br",
            "RJ" to "globo_rj.br", "RN" to "globo_rn_intertv_cabugi.br",
            "RS" to "globo_rs_rbs_tv_porto_alegre.br", "RO" to "globo_ro_rede_amaz_nica_porto_velho.br",
            "RR" to "globo_nacional.br", "SC" to "globo_sc_nsc_tv_florian_polis.br",
            "SP" to "globo_sp.br", "SE" to "globo_se_tv_sergipe.br",
            "TO" to "globo_to_tv_anhanguera_palmas.br"
        ),
        "SBT" to mapOf(
            "AC" to "sbt_nacional.br", "AL" to "sbt_al_ponta_verde.br",
            "AP" to "sbt_nacional.br", "AM" to "sbt_nacional.br",
            "BA" to "sbt_ba.br", "CE" to "sbt_ce_jangadeiro.br",
            "DF" to "sbt_df_bras_lia.br", "ES" to "sbt_es_tribuna.br",
            "GO" to "sbt_go.br", "MA" to "sbt_ma.br",
            "MT" to "sbt_mt_noroeste.br", "MS" to "sbt_nacional.br",
            "MG" to "sbt_mg_alterosa.br", "PA" to "sbt_pa_belem.br",
            "PB" to "sbt_pb_tamba.br", "PR" to "sbt_pr.br",
            "PE" to "sbt_pe_tv_jornal.br", "PI" to "sbt_pi_cidade_verde.br",
            "RJ" to "sbt_rj.br", "RN" to "sbt_rn.br",
            "RS" to "sbt_rs.br", "RO" to "sbt_ro_allamanda.br",
            "RR" to "sbt_nacional.br", "SC" to "sbt_sc.br",
            "SP" to "sbt_sp.br", "SE" to "sbt_nacional.br",
            "TO" to "sbt_to.br"
        ),
        "Record" to mapOf(
            "AC" to "record_news.br", "AL" to "record_al_paju_ara.br",
            "AP" to "record_news.br", "AM" to "record_am_manaus.br",
            "BA" to "record_ba_itapoan.br", "CE" to "record_ce_cidade.br",
            "DF" to "record_df_bras_lia.br", "ES" to "record_es_vit_ria.br",
            "GO" to "record_go_goi_s.br", "MA" to "record_ma_remanso.br",
            "MT" to "record_mt.br", "MS" to "record_news.br",
            "MG" to "record_mg_minas.br", "PA" to "record_pa_bel_m.br",
            "PB" to "record_pb_correio.br", "PR" to "record_pr.br",
            "PE" to "record_pe_recife_guararapes.br", "PI" to "record_pi_piau.br",
            "RJ" to "record_rj.br", "RN" to "record_rn_tropical.br",
            "RS" to "record_rs.br", "RO" to "record_ro_sic.br",
            "RR" to "record_news.br", "SC" to "record_sc_florian_polis.br",
            "SP" to "record_sp.br", "SE" to "record_se_atalaia.br",
            "TO" to "record_to.br"
        ),
        "Band" to mapOf(
            "AC" to "band_nacional.br", "AL" to "band_al.br",
            "AP" to "band_nacional.br", "AM" to "band_am.br",
            "BA" to "band_ba.br", "CE" to "band_ce.br",
            "DF" to "band_df_bras_lia.br", "ES" to "band_es_capixaba.br",
            "GO" to "band_go.br", "MA" to "band_nacional.br",
            "MT" to "band_nacional.br", "MS" to "band_nacional.br",
            "MG" to "band_mg.br", "PA" to "band_pa.br",
            "PB" to "band_pb_arapuan.br", "PR" to "band_pr.br",
            "PE" to "band_pe_recife.br", "PI" to "band_nacional.br",
            "RJ" to "band_rj.br", "RN" to "band_rn.br",
            "RS" to "band_rs.br", "RO" to "band_nacional.br",
            "RR" to "band_nacional.br", "SC" to "band_sc_barriga_verde.br",
            "SP" to "band_sp.br", "SE" to "band_nacional.br",
            "TO" to "band_to.br"
        ),
        "Gazeta" to mapOf(
            "AC" to "tv_gazeta.br", "AL" to "tv_gazeta.br", "AP" to "tv_gazeta.br", "AM" to "tv_gazeta.br",
            "BA" to "tv_gazeta.br", "CE" to "tv_gazeta.br", "DF" to "tv_gazeta.br", "ES" to "tv_gazeta.br",
            "GO" to "tv_gazeta.br", "MA" to "tv_gazeta.br", "MT" to "tv_gazeta.br", "MS" to "tv_gazeta.br",
            "MG" to "tv_gazeta.br", "PA" to "tv_gazeta.br", "PB" to "tv_gazeta.br", "PR" to "tv_gazeta.br",
            "PE" to "tv_gazeta.br", "PI" to "tv_gazeta.br", "RJ" to "tv_gazeta.br", "RN" to "tv_gazeta.br",
            "RS" to "tv_gazeta.br", "RO" to "tv_gazeta.br", "RR" to "tv_gazeta.br", "SC" to "tv_gazeta.br",
            "SP" to "tv_gazeta.br", "SE" to "tv_gazeta.br", "TO" to "tv_gazeta.br"
        )
    )

    // Reverse map: channelId -> UF
    private val reverseAffiliateMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        affiliateMap.values.forEach { stateMap ->
            stateMap.forEach { (state, id) ->
                // Mapeia apenas filiais regionais reais, ignora canais que são nacionais (ex: globo_nacional.br)
                if (!id.contains("nacional", ignoreCase = true) && !id.contains("news", ignoreCase = true) && !id.contains("tv_gazeta", ignoreCase = true)) {
                    map[id] = state
                }
            }
        }
        map
    }

    // Mapa de fallback para quando um estado não tem filial de uma emissora
    private val stateFallbacks = mapOf(
        "AC" to listOf("RO", "AM", "DF", "SP", "RJ"),
        "AL" to listOf("PE", "SE", "PB", "RN", "BA", "SP", "RJ"),
        "AP" to listOf("PA", "MA", "AM", "DF", "SP", "RJ"),
        "AM" to listOf("RR", "RO", "AC", "PA", "DF", "SP", "RJ"),
        "BA" to listOf("SE", "AL", "PE", "MG", "ES", "SP", "RJ"),
        "CE" to listOf("PI", "RN", "PB", "PE", "SP", "RJ"),
        "DF" to listOf("GO", "MG", "SP", "RJ"),
        "ES" to listOf("RJ", "MG", "BA", "SP"),
        "GO" to listOf("DF", "MG", "MS", "MT", "SP", "RJ"),
        "MA" to listOf("PI", "CE", "PA", "TO", "SP", "RJ"),
        "MT" to listOf("MS", "GO", "RO", "DF", "SP", "RJ"),
        "MS" to listOf("MT", "GO", "PR", "SP", "RJ"),
        "MG" to listOf("ES", "RJ", "SP", "GO", "DF"),
        "PA" to listOf("AP", "MA", "AM", "TO", "DF", "SP", "RJ"),
        "PB" to listOf("RN", "PE", "CE", "AL", "SP", "RJ"),
        "PR" to listOf("SC", "SP", "MS", "RS", "RJ"),
        "PE" to listOf("PB", "AL", "RN", "CE", "SP", "RJ"),
        "PI" to listOf("MA", "CE", "PE", "BA", "SP", "RJ"),
        "RJ" to listOf("ES", "MG", "SP", "DF"),
        "RN" to listOf("PB", "CE", "PE", "SP", "RJ"),
        "RS" to listOf("SC", "PR", "SP", "RJ"),
        "RO" to listOf("AC", "AM", "MT", "DF", "SP", "RJ"),
        "RR" to listOf("AM", "PA", "DF", "SP", "RJ"),
        "SC" to listOf("PR", "RS", "SP", "RJ"),
        "SP" to listOf("RJ", "MG", "PR", "DF"),
        "SE" to listOf("AL", "BA", "PE", "SP", "RJ"),
        "TO" to listOf("GO", "PA", "MA", "PI", "BA", "DF", "SP", "RJ")
    )

    // Fixed top 9 channels configuration
    // Each config: position, displayName, exactId (preferred), exactName (fallback), keywords (last resort)
    private val fixedTop9Config = listOf(
        FixedChannel(1, "Globo", "RedeGlobo.br", "Globo", setOf("redeglobo", "tvglobo")),
        FixedChannel(2, "SBT", "SBTNacional.br", "SBT", setOf("sbt nacional", "sbtv")),
        FixedChannel(3, "Record", "Record.br", "Record", setOf("recordtv", "record tv")),
        FixedChannel(4, "RedeTV!", "RedeTV.br", "RedeTV!", setOf("redetv", "rede tv")),
        FixedChannel(5, "Gazeta", "tv_gazeta.br", "Gazeta", setOf("tvgazeta", "tv gazeta")),
        FixedChannel(6, "Band", "Band.br", "Band", setOf("bandtv", "band tv", "bandnews")),
        FixedChannel(7, "TV Cultura", "TVCultura.br", "TV Cultura", setOf("tvcultura", "tv cultura")),
        FixedChannel(8, "TV Centro Sul", null, "TV Centro Sul", setOf("centro sul", "centrosul")), // custom
        FixedChannel(9, "TV Litoral RN", "TVLitoralRN.br", "TV Litoral RN", setOf("tv litoral", "tvlitoral"))
    )

    data class FixedChannel(
        val position: Int,
        val displayName: String,
        val exactId: String?, // preferred exact channel ID from iptv-org
        val exactName: String, // exact name match (case-insensitive)
        val keywords: Set<String> // fallback: word-boundary keyword match
    )

    // Custom stream for TV Centro Sul (position 8)
    private val tvCentroSulStream = ApiStreamDto(
        channel = "tv_centro_sul_br",
        feed = null,
        title = "TV Centro Sul",
        url = "https://5c483b9d1019c.streamlock.net/tvcentrosul/tvcentrosul/playlist.m3u8",
        referrer = null,
        user_agent = null,
        quality = "HD",
        label = "TV Centro Sul"
    )

    private val tvCentroSulChannel = ApiChannelDto(
        id = "tv_centro_sul_br",
        name = "TV Centro Sul",
        country = "BR",
        categories = listOf("general"),
        is_nsfw = false,
        owners = emptyList()
    )

    private val tvCentroSulLogo = ApiLogoDto(
        channel = "tv_centro_sul_br",
        feed = null,
        url = "https://yt3.googleusercontent.com/Sb4wyUWeUMDz05hExE3-VdQ0AWIVnb9qvwsI86XZlXtDndRnikmyub6dRoKsyN7XiJBC-hSfvOM=s900-c-k-c0x00ffffff-no-rj",
        width = 900,
        height = 900
    )

    private fun categoryWeight(cats: List<String>): Int = when {
        cats.contains("general") -> 1
        cats.contains("news") -> 2
        cats.contains("sports") -> 3
        cats.contains("movies") -> 4
        cats.contains("kids") -> 5
        cats.contains("entertainment") -> 6
        cats.contains("lifestyle") -> 7
        cats.contains("music") -> 7
        cats.contains("documentary") -> 8
        cats.contains("religious") -> 9
        else -> 10
    }

    private fun countryWeight(country: String): Int = when (country) {
        "BR" -> 0
        "PT" -> 1000
        "US" -> 2000
        "ES" -> 3000
        "AR", "MX", "CL", "CO", "PE" -> 4000
        "FR", "DE", "IT", "UK", "GB" -> 5000
        else -> 9000
    }

    fun buildNumberedChannels(
        channels: List<ApiChannelDto>,
        streams: List<ApiStreamDto>,
        logos: List<ApiLogoDto>,
        selectedUf: String
    ): List<ChannelNumberEntity> {
        val streamsByChannel = streams.groupBy { it.channel }.toMutableMap()
        val logoByChannel = logos.groupBy { it.channel }
        fun logoFor(id: String): String? = logoByChannel[id]?.firstOrNull()?.url

        // Inject custom stream for TV Centro Sul into streams map
        streamsByChannel["tv_centro_sul_br"] = listOf(tvCentroSulStream)

        // Prefer affiliate for BR general channels
        val affiliatePreferredIds = mutableSetOf<String>()
        affiliateMap.values.forEach { map ->
            map[selectedUf]?.let { affiliatePreferredIds.add(it) }
        }

        val sorted = channels.sortedWith(compareBy(
            { countryWeight(it.country) },
            { categoryWeight(it.categories) },
            { it.name.lowercase() }
        ))

        val withAffiliateBoost = sorted.sortedWith(Comparator { a, b ->
            val aAff = if (affiliatePreferredIds.contains(a.id)) 0 else 1
            val bAff = if (affiliatePreferredIds.contains(b.id)) 0 else 1
            if (aAff != bAff) return@Comparator aAff - bAff
            val cwA = countryWeight(a.country); val cwB = countryWeight(b.country)
            if (cwA != cwB) return@Comparator cwA - cwB
            val catA = categoryWeight(a.categories); val catB = categoryWeight(b.categories)
            if (catA != catB) return@Comparator catA - catB
            a.name.lowercase().compareTo(b.name.lowercase())
        })

        val filtered = withAffiliateBoost.filter { ch ->
            !streamsByChannel[ch.id].isNullOrEmpty()
        }

        // --- Build fixed top 9 channels ---
        val fixedEntities = mutableListOf<ChannelNumberEntity>()
        val fixedChannelIds = mutableSetOf<String>()

        for (config in fixedTop9Config) {
            val targetNumber = config.position
            when {
                config.exactId == null -> {
                    // Custom channel: TV Centro Sul (position 8)
                    if (targetNumber == 8) {
                        fixedEntities.add(ChannelNumberEntity(
                            number = targetNumber,
                            channelId = tvCentroSulChannel.id,
                            channelName = tvCentroSulChannel.name,
                            country = tvCentroSulChannel.country,
                            categories = tvCentroSulChannel.categories.joinToString(","),
                            logoUrl = tvCentroSulLogo.url,
                            isNsfw = tvCentroSulChannel.is_nsfw,
                            streamCount = 1,
                            affiliateState = null
                        ))
                        fixedChannelIds.add(tvCentroSulChannel.id)
                    }
                }
                else -> {
                    // Obtém o ID da filial regional esperada para este estado, com fallback para estados vizinhos
                    val networkMap = affiliateMap[config.displayName]
                    var expectedRegionalId: String? = null
                    
                    if (networkMap != null) {
                        val fallbacks = stateFallbacks[selectedUf] ?: emptyList()
                        val checkUfs = listOf(selectedUf) + fallbacks
                        
                        for (ufToTry in checkUfs) {
                            val idToTry = networkMap[ufToTry]
                            if (idToTry != null && filtered.any { it.id == idToTry }) {
                                expectedRegionalId = idToTry
                                break
                            }
                        }
                    }

                    // Match priority: 0) expected regional ID, 1) exact ID, 2) exact name (case-insensitive), 3) word-boundary keyword
                    val match = filtered.find { ch ->
                        // 0) Regional affiliate ID match (highest priority)
                        expectedRegionalId != null && ch.id == expectedRegionalId
                    } ?: filtered.find { ch ->
                        // 1) Exact ID match (fallback 1)
                        ch.id == config.exactId
                    } ?: filtered.find { ch ->
                        // 2) Exact name match (fallback 2)
                        ch.name.equals(config.exactName, ignoreCase = true)
                    } ?: filtered.find { ch ->
                        // 3) Word-boundary keyword match (last resort)
                        config.keywords.any { kw ->
                            val kwLower = kw.lowercase()
                            ch.id.lowercase().contains(kwLower) || ch.name.lowercase().contains(kwLower)
                        }
                    }
                    
                    if (match != null) {
                        val logo = logoFor(match.id)
                        val count = streamsByChannel[match.id]?.size ?: 0
                        val isAffiliate = affiliatePreferredIds.contains(match.id)
                        fixedEntities.add(ChannelNumberEntity(
                            number = targetNumber,
                            channelId = match.id,
                            channelName = config.displayName,
                            country = match.country,
                            categories = match.categories.joinToString(","),
                            logoUrl = logo,
                            isNsfw = match.is_nsfw,
                            streamCount = count,
                            affiliateState = getAffiliateState(match.id, config.displayName)
                        ))
                        fixedChannelIds.add(match.id)
                    }
                }
            }
        }

        // Remove fixed channels from filtered to avoid duplicates
        val remainingFiltered = filtered.filter { it.id !in fixedChannelIds }

        val entities = mutableListOf<ChannelNumberEntity>()
        entities.addAll(fixedEntities)

        // Filtra os canais por UF antes de quebrar em blocos de numeração (evita gaps)
        val ufFilteredRemaining = if (selectedUf != "NACIONAL") {
            remainingFiltered.filter { c ->
                val state = getAffiliateState(c.id, c.name)
                state == null || state == selectedUf
            }
        } else {
            remainingFiltered
        }

        // Continue numbering from 10 for remaining channels
        var number = 10
        
        val maratonar = ufFilteredRemaining.filter { it.categories.contains("maratonar") }
        val mundo = ufFilteredRemaining.filter { it.country == "INTL" || it.categories.contains("mundo") }
        val br = ufFilteredRemaining.filter { it.country == "BR" && !maratonar.contains(it) && !mundo.contains(it) }

        fun addGroup(group: List<ApiChannelDto>, limit: Int) {
            for (c in group) {
                if (number >= limit) break
                val logo = logoFor(c.id)
                val count = streamsByChannel[c.id]?.size ?: 0
                entities.add(ChannelNumberEntity(
                    number = number,
                    channelId = c.id,
                    channelName = c.name,
                    country = c.country,
                    categories = c.categories.joinToString(","),
                    logoUrl = logo,
                    isNsfw = c.is_nsfw,
                    streamCount = count,
                    affiliateState = getAffiliateState(c.id, c.name)
                ))
                number++
            }
        }

        // BR: 10 - 1999
        addGroup(br, 2000)
        
        // Maratonar: 2000 - 2999
        number = maxOf(number, 2000)
        addGroup(maratonar, 3000)
        
        // Mundo / INTL: 3000 - 9999
        number = maxOf(number, 3000)
        addGroup(mundo, 10000)

        // Any leftovers (just in case)
        val others = ufFilteredRemaining.filter { !maratonar.contains(it) && !mundo.contains(it) && !br.contains(it) }
        number = maxOf(number, 4000)
        addGroup(others, 10000)

        return entities.sortedBy { it.number }
    }

    /**
     * Attempts to find the Brazilian state (UF) a channel belongs to,
     * using the reverseAffiliateMap first, and then falling back to name detection.
     */
    fun getAffiliateState(channelId: String, channelName: String): String? {
        // 1. Verifica no mapa oficial de filiais do iptv-org
        reverseAffiliateMap[channelId]?.let { return it }

        // 2. Fallback: tenta detectar pela sigla no nome/id
        val ufs = setOf(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
        )

        val normId   = channelId.uppercase().replace(Regex("[_\\-./]"), " ")
        val normName = channelName.uppercase().replace(Regex("[_\\-./]"), " ")

        val idTokens   = normId.split(" ").filter { it.isNotBlank() }
        val nameTokens = normName.split(" ").filter { it.isNotBlank() }

        return (nameTokens + idTokens).firstOrNull { it in ufs }
    }

    /**
     * Resolve os 9 canais fixos diretamente sobre a lista completa e unificada de canais (M3U + iptv-org),
     * garantindo que a filial regional do estado do usuário (ex: Globo CE, SBT CE, etc.) ocupe
     * prioritariamente a posição 1, 2, 3, 6.
     */
    fun resolveFixedEntities(
        allEntities: List<ChannelNumberEntity>,
        selectedUf: String
    ): Pair<List<ChannelNumberEntity>, Set<String>> {
        val fixedEntities = mutableListOf<ChannelNumberEntity>()
        val fixedChannelIds = mutableSetOf<String>()

        for (config in fixedTop9Config) {
            val targetNumber = config.position
            if (targetNumber == 8) {
                val match = allEntities.find { 
                    it.channelId == tvCentroSulChannel.id || 
                    it.channelName.contains("Centro Sul", ignoreCase = true) 
                }
                if (match != null) {
                    fixedEntities.add(match.copy(number = 8, channelName = "TV Centro Sul"))
                    fixedChannelIds.add(match.channelId)
                }
                continue
            }
            if (targetNumber == 9) {
                val match = allEntities.find { 
                    it.channelId == "tv_litoral_rn.br" || 
                    it.channelName.contains("Litoral RN", ignoreCase = true) 
                }
                if (match != null) {
                    fixedEntities.add(match.copy(number = 9, channelName = "TV Litoral RN"))
                    fixedChannelIds.add(match.channelId)
                }
                continue
            }

            val networkMap = affiliateMap[config.displayName]
            var matchedEntity: ChannelNumberEntity? = null

            if (networkMap != null && selectedUf != "NACIONAL") {
                val fallbacks = stateFallbacks[selectedUf] ?: emptyList()
                val checkUfs = listOf(selectedUf) + fallbacks

                for (ufToTry in checkUfs) {
                    val expectedId = networkMap[ufToTry]
                    if (expectedId != null) {
                        matchedEntity = allEntities.find { ent ->
                            ent.channelId.equals(expectedId, ignoreCase = true) ||
                            ent.channelName.contains("${config.displayName} $ufToTry", ignoreCase = true) ||
                            ent.channelId.contains("${config.displayName.lowercase()}_${ufToTry.lowercase()}", ignoreCase = true)
                        }
                        if (matchedEntity != null) break
                    }
                }
            }

            if (matchedEntity == null) {
                // 1) Match por ID exato
                matchedEntity = allEntities.find { it.channelId == config.exactId }
                    // 2) Match por Nome exato
                    ?: allEntities.find { it.channelName.equals(config.exactName, ignoreCase = true) }
                    // 3) Match por palavra-chave
                    ?: allEntities.find { ent ->
                        config.keywords.any { kw ->
                            val kwLower = kw.lowercase()
                            ent.channelId.lowercase().contains(kwLower) || ent.channelName.lowercase().contains(kwLower)
                        }
                    }
            }

            if (matchedEntity != null) {
                fixedEntities.add(matchedEntity.copy(
                    number = targetNumber,
                    channelName = config.displayName
                ))
                fixedChannelIds.add(matchedEntity.channelId)
            }
        }

        return Pair(fixedEntities, fixedChannelIds)
    }
}