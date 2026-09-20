package com.zap.tv.data.m3u

import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiStreamDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object M3uChannelMapper {

    // Prioridade de qualidade: SD (0) > HD (1) > FHD (2) > 4K (3)
    private val QUALITY_ORDER = mapOf(
        "SD" to 0, "SD+" to 1,
        "HD" to 2, "HD+" to 3,
        "FHD" to 4, "FHD H265" to 5,
        "4K" to 6
    )

    // Palavras-chave para canais de filme/VOD que devem ser filtrados (maratonar mantido como categoria)
    private val VOD_KEYWORDS = setOf(
        "FILME", "SERIE", "SÉRIE", "MOVIE", "SERIES", "VOD", "ON DEMAND",
        "007", "BACK TO BACK", "ESPECIAL"
    )

    // Remoção hard solicitada: canais desativados / futebol antigo / BBB
    private val BLOCKED_EXACT = setOf(
        "CINTURAO VERDE", "CINTURÃO VERDE",
        "ELY TV", "ELYTV",
        "MBC BRASIL", "MBCBRASIL TV", "MBCBRASIL",
        "RCTV BRASIL", "RCTVBrasil", "RCTVBRASIL",
        "TV3 CURITIBA", "TV3CURITIBA",
        "TV ALTERNATIVA", "TVALTERNATIVA",
        "8NTV"
    )
    private val BLOCKED_KEYWORDS = setOf("BBB", "BIG BROTHER")
    private val FUTEBOL_TIMED_REGEX = Regex("""\[\d{1,2}:\d{2}\].*\sX\s""", RegexOption.IGNORE_CASE)

    private fun isBlocked(name: String): Boolean {
        val upper = name.uppercase().trim()
        if (BLOCKED_EXACT.any { upper == it || upper.contains(it) }) return true
        if (BLOCKED_KEYWORDS.any { upper.contains(it) }) return true
        if (FUTEBOL_TIMED_REGEX.containsMatchIn(name)) return true
        return false
    }

    // Palavras-chave para canais válidos de TV ao vivo
    private val LIVE_KEYWORDS = setOf(
        "GLOBO", "BAND", "RECORD", "SBT", "REDE TV", "REDE TV!", "GAZETA", "CULTURA",
        "CNN", "NEWS", "NOTICIAS", "NOTÍCIAS", "JOVEM PAN",
        "SPORTV", "ESPN", "PREMIERE", "COMBATE", "NSPORTS", "BANDSPORTS", "SPORT",
        "MULTISHOW", "GNT", "VIVA", "GLOOB", "DISCOVERY", "HISTORY", "ANIMAL PLANET",
        "NAT GEO", "NATIONAL GEOGRAPHIC", "FOOD NETWORK", "HGTV", "LIFETIME", "E!",
        "COMEDY CENTRAL", "PARAMOUNT", "MTV", "VH1", "BIS", "CURTA", "ARTE 1",
        "CANAL BRASIL", "TELECINE", "HBO", "MAX", "PARAMOUNT+", "MEGAPIX",
        "PRIME BOX", "CINEMAX", "STAR", "TCM", "SONY", "FX", "FOX", "WARNER",
        "CARTOON", "NICKELODEON", "DISNEY", "BABY TV", "BOOMERANG",
        "SIC", "RTP", "TVI", "CMTV", "SPORT TV", "ELEVEN", "HOLLYWOOD", "AMC",
        "AXN", "FOX LIFE", "FOX CRIME", "FOX COMEDY", "NAT GEO WILD",
        "DISNEY JUNIOR", "PANDA", "BABYTV", "BENFICA TV", "SPORTING TV",
        "ABC", "CBS", "NBC", "FOX US", "CNN US", "ESPN US", "HBO US",
        "CINEMAX US", "SHOWTIME", "STARZ", "TNT", "TBS", "USA", "TLC",
        "A&E", "VICE", "BBC", "BLOOMBERG", "CNBC"
    )

    data class StreamCandidate(
        val url: String,
        val quality: String,
        val referrer: String? = null,
        val userAgent: String? = null,
        val qualityRank: Int
    )

    // Função principal: processa entries do parser → catálogos separados
    suspend fun mapToCatalogs(entries: List<M3uParser.ParsedEntry>): M3uCatalog = withContext(Dispatchers.IO) {
        val brGroups = mutableMapOf<String, MutableList<StreamCandidate>>()
        val brDisplayNames = mutableMapOf<String, String>()
        val brCategories = mutableMapOf<String, MutableList<String>>()
        val brLogos = mutableMapOf<String, String>()
        
        val ptGroups = mutableMapOf<String, MutableList<StreamCandidate>>()
        val ptDisplayNames = mutableMapOf<String, String>()
        val ptCategories = mutableMapOf<String, MutableList<String>>()
        
        val usGroups = mutableMapOf<String, MutableList<StreamCandidate>>()
        val usDisplayNames = mutableMapOf<String, String>()
        val usCategories = mutableMapOf<String, MutableList<String>>()
        
        val adultGroups = mutableMapOf<String, MutableList<StreamCandidate>>()
        val adultDisplayNames = mutableMapOf<String, String>()
        val adultCategories = mutableMapOf<String, MutableList<String>>()

        for (entry in entries) {
            val country = M3uParser.extractCountry(entry.name)
            val cleanName = M3uParser.removeCountryPrefix(entry.name)
            val isAdult = M3uParser.isAdultChannel(cleanName)
            val normalizedName = M3uParser.normalizeName(cleanName)
            val quality = M3uParser.extractQuality(entry.rawExtinf)
            val qualityRank = QUALITY_ORDER[quality] ?: 2

            // Remoção hard solicitada (futebol/BBB/canais desativados)
            if (isBlocked(normalizedName)) continue

            // Skip VOD/filmes/séries (maratonar mantido)
            if (isVodChannel(normalizedName)) continue

            val streamCandidate = StreamCandidate(
                url = entry.url,
                quality = quality,
                referrer = null,
                userAgent = null,
                qualityRank = qualityRank
            )

            // Gera channelId normalizado
            val channelId = generateChannelId(normalizedName, country)

            // Categoriza baseado no nome
            val categories = categorizeChannel(normalizedName)

            when {
                isAdult -> {
                    adultGroups.getOrPut(channelId) { mutableListOf() }.add(streamCandidate)
                    adultDisplayNames[channelId] = normalizedName
                    adultCategories.getOrPut(channelId) { mutableListOf() }.addAll(categories)
                }
                country == "PT" -> {
                    ptGroups.getOrPut(channelId) { mutableListOf() }.add(streamCandidate)
                    ptDisplayNames[channelId] = normalizedName
                    ptCategories.getOrPut(channelId) { mutableListOf() }.addAll(categories)
                }
                country == "US" -> {
                    usGroups.getOrPut(channelId) { mutableListOf() }.add(streamCandidate)
                    usDisplayNames[channelId] = normalizedName
                    usCategories.getOrPut(channelId) { mutableListOf() }.addAll(categories)
                }
                else -> {
                    brGroups.getOrPut(channelId) { mutableListOf() }.add(streamCandidate)
                    brDisplayNames[channelId] = normalizedName
                    brCategories.getOrPut(channelId) { mutableListOf() }.addAll(categories)
                }
            }
        }

        // Constrói catálogos filtrando apenas canais com streams válidos
        val brChannels = buildChannelGroups(brGroups, brDisplayNames, brCategories, brLogos, "BR")
        val ptChannels = buildChannelGroups(ptGroups, ptDisplayNames, ptCategories, mutableMapOf(), "PT")
        val usChannels = buildChannelGroups(usGroups, usDisplayNames, usCategories, mutableMapOf(), "US")
        val adultChannels = buildChannelGroups(adultGroups, adultDisplayNames, adultCategories, mutableMapOf(), "BR", true)

        M3uCatalog(brChannels, ptChannels, usChannels, adultChannels)
    }

    private fun isVodChannel(name: String): Boolean {
        val upper = name.uppercase()
        return VOD_KEYWORDS.any { upper.contains(it) }
    }

    private fun isLikelyLiveChannel(name: String): Boolean {
        val upper = name.uppercase()
        return LIVE_KEYWORDS.any { upper.contains(it) }
    }

    private fun categorizeChannel(name: String): List<String> {
        val upper = name.uppercase()
        return when {
            upper.contains("NEWS") || upper.contains("NOTICIAS") || upper.contains("NOTÍCIAS") || 
                 upper.contains("JOVEM PAN") || upper.contains("CNN") || upper.contains("BANDNEWS") ||
                 upper.contains("GLOBONEWS") || upper.contains("RECORD NEWS") -> listOf("news")
            upper.contains("SPORT") || upper.contains("ESPN") || upper.contains("PREMIERE") ||
                 upper.contains("COMBATE") || upper.contains("NSPORTS") || upper.contains("BANDSPORTS") -> listOf("sports")
            upper.contains("KIDS") || upper.contains("CARTOON") || upper.contains("NICKELODEON") ||
                 upper.contains("DISNEY") || upper.contains("GLOOB") || upper.contains("BABY") ||
                 upper.contains("BOOMERANG") || upper.contains("PANDA") -> listOf("kids")
            upper.contains("MOVIE") || upper.contains("CINE") || upper.contains("TELECINE") ||
                 upper.contains("HBO") || upper.contains("MAX") || upper.contains("MEGAPIX") ||
                 upper.contains("PRIME BOX") || upper.contains("CINEMAX") || upper.contains("TCM") -> listOf("movies")
            upper.contains("MULTISHOW") || upper.contains("GNT") || upper.contains("VIVA") ||
                 upper.contains("GLOOB") || upper.contains("DISCOVERY") || upper.contains("HISTORY") ||
                 upper.contains("ANIMAL PLANET") || upper.contains("NAT GEO") || upper.contains("FOOD") ||
                 upper.contains("HGTV") || upper.contains("LIFETIME") || upper.contains("E!") ||
                 upper.contains("COMEDY") || upper.contains("PARAMOUNT") || upper.contains("MTV") ||
                 upper.contains("VH1") || upper.contains("BIS") || upper.contains("CURTA") ||
                 upper.contains("ARTE 1") -> listOf("entertainment")
            upper.contains("GLOBO") || upper.contains("BAND") || upper.contains("RECORD") ||
                 upper.contains("SBT") || upper.contains("REDE TV") || upper.contains("GAZETA") ||
                 upper.contains("CULTURA") -> listOf("general")
            else -> listOf("general")
        }
    }

    private fun generateChannelId(name: String, country: String): String {
        val normalized = name
            .lowercase()
            .replace(" ", "_")
            .replace("-", "_")
            .replace("'", "")
            .replace(".", "")
            .replace("/", "_")
        return "${normalized}.${country.lowercase()}"
    }

    private fun buildChannelGroups(
        groups: Map<String, MutableList<StreamCandidate>>,
        displayNames: Map<String, String>,
        categories: Map<String, MutableList<String>>,
        logos: Map<String, String>,
        country: String,
        isNsfw: Boolean = false
    ): List<M3uChannelGroup> {
        return groups.entries.mapNotNull { (channelId, streams) ->
            // Ordena streams: SD primeiro, depois HD, FHD, 4K
            val sortedStreams = streams
                .distinctBy { it.url }
                .sortedBy { it.qualityRank }
            
            // Para PT/US, só inclui se tem stream válido (SD ou HD)
            if (country != "BR" && sortedStreams.none { it.qualityRank <= 2 }) {
                return@mapNotNull null
            }
            
            M3uChannelGroup(
                channelId = channelId,
                displayName = displayNames[channelId] ?: channelId,
                country = country,
                categories = categories[channelId]?.distinct() ?: listOf("general"),
                streams = sortedStreams.map { 
                    M3uStreamInfo(
                        url = it.url,
                        quality = it.quality,
                        referrer = it.referrer,
                        userAgent = it.userAgent
                    )
                },
                logoUrl = logos[channelId],
                isNsfw = isNsfw
            )
        }
    }
}