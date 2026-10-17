package com.zap.tv.util

import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiLogoDto
import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.local.entity.ChannelNumberEntity

object ChannelNumbering {

    // Affiliates mapping UF -> preferred channel ids per network.
    // Construído a partir do AffiliateRegistry (identificadores reais e verificados - Etapa 05).
    // Não contém IDs fictícios: cada valor existe em m3u_catalog.json ou iptv-org.
    val affiliateMap: Map<String, Map<String, String>> = run {
        val ufs = AffiliateRegistry.ALL_UFS
        AffiliateRegistry.networks().associateWith { network ->
            val regionalEntries = ufs.mapNotNull { uf ->
                AffiliateRegistry.resolveRegionalId(network, uf)?.let { uf to it }
            }.toMap()
            if (regionalEntries.isNotEmpty()) {
                regionalEntries
            } else {
                // Redes sem afiliação regional (ex.: Gazeta) operam pela cabeça de rede.
                AffiliateRegistry.headId(network)?.let { head -> ufs.associateWith { head } } ?: emptyMap()
            }
        }
    }

    // Reverse map: channelId real de afiliada regional -> UF (cabeças nacionais não entram).
    private val reverseAffiliateMap: Map<String, String> by lazy {
        AffiliateRegistry.all
            .filter { !it.isHead }
            .associate { it.channelId.lowercase() to it.uf }
    }

    // Fixed top 9 channels configuration
    // Each config: position, displayName, exactId (preferred), exactName (fallback), keywords (last resort)
    private val fixedTop9Config = listOf(
        FixedChannel(1, "Globo", "RedeGlobo.br", "Globo", setOf("redeglobo", "tvglobo")),
        FixedChannel(2, "SBT", "SBTNacional.br", "SBT", setOf("sbt nacional", "sbtv")),
        FixedChannel(3, "Record", "Record.br", "Record", setOf("recordtv", "record tv")),
        FixedChannel(4, "RedeTV!", "RedeTV.br", "RedeTV!", setOf("redetv", "rede tv")),
        FixedChannel(5, "Gazeta", "TVGazeta.br", "Gazeta", setOf("tvgazeta", "tv gazeta")),
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

    /**
     * Etapa 17 §04: identificadores que NUNCA podem representar uma posição fixa,
     * mesmo que casem por nome/palavra-chave. Corrige a TV Cultura (posição 7), que
     * estava reproduzindo o sinal da Cultura Fast em vez da emissora principal
     * (`TVCultura.br`). A Cultura Fast continua existindo como canal próprio, apenas
     * não substitui a TV Cultura na posição fixa.
     */
    private val forbiddenFixedIds: Map<String, Set<String>> = mapOf(
        "TV Cultura" to setOf("culturafast.br")
    )

    /**
     * Rede nacional associada a uma posição fixa (1-9), ou `null` quando a posição
     * não corresponde a uma rede regionalizável. A posição 8 (TV Centro Sul) é
     * custom e não participa da regionalização por rede.
     */
    fun networkForFixedPosition(position: Int): String? =
        if (position == 8) null
        else fixedTop9Config.firstOrNull { it.position == position }?.displayName

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

    /**
     * Complemento Etapa 18: identidades que SEMPRE pertencem ao catálogo aprovado por
     * representarem as posições fixas 1-9. Garante que nenhuma posição fixa desapareça
     * da grade por ausência de stream — a disponibilidade afeta somente a reprodução,
     * nunca a identidade nem o número da posição. (A posição 8 é o canal custom
     * TV Centro-Sul, incluído explicitamente.)
     */
    val fixedApprovedIds: Set<String> =
        fixedTop9Config.mapNotNull { it.exactId }.toSet() + tvCentroSulChannel.id

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

    /**
     * @param catalogOnlyIds identificadores de canais que devem entrar na grade MESMO
     *   sem fonte reproduzível (registro de catálogo da Fonte A, Etapa 08 §02/§05).
     *   Eles são numerados normalmente, marcados `isAvailable = false`, e NUNCA
     *   concorrem às posições fixas 1-9 (reservadas a emissoras reproduzíveis).
     * @param approvedIds Etapa 18: pertencimento ao catálogo complementar APROVADO
     *   (ledger de identidades estáveis). Quando informado (não vazio), é o ÚNICO
     *   critério de existência para canais iptv-org — a presença de stream deixa de
     *   decidir quem entra na grade. Vazio = comportamento legado (stream/catálogo).
     */
    fun buildNumberedChannels(
        channels: List<ApiChannelDto>,
        streams: List<ApiStreamDto>,
        logos: List<ApiLogoDto>,
        selectedUf: String,
        catalogOnlyIds: Set<String> = emptySet(),
        approvedIds: Set<String> = emptySet()
    ): List<ChannelNumberEntity> {
        val streamsByChannel = streams.groupBy { it.channel }.toMutableMap()
        val logoByChannel = logos.groupBy { it.channel }
        fun logoFor(id: String): String? = logoByChannel[id]?.firstOrNull()?.url

        // Etapa 07 §03: canais temáticos de programação contínua 24h nunca entram
        // no catálogo ativo (notícia/TV ao vivo são preservadas pelo filtro).
        val activeChannels = channels.filterNot {
            CatalogFilters.isTematic24h(it.id, it.name, it.categories)
        }

        // Inject custom stream for TV Centro Sul into streams map
        streamsByChannel["tv_centro_sul_br"] = listOf(tvCentroSulStream)

        // Prefer affiliate for BR general channels
        val affiliatePreferredIds = mutableSetOf<String>()
        affiliateMap.values.forEach { map ->
            map[selectedUf]?.let { affiliatePreferredIds.add(it) }
        }

        val sorted = activeChannels.sortedWith(compareBy(
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
            ch.id in approvedIds ||
                ch.id in catalogOnlyIds ||
                // Compatibilidade (Etapa 07/08): sem ledger informado, mantém o
                // comportamento anterior (stream reproduzível ou registro de catálogo).
                (approvedIds.isEmpty() && !streamsByChannel[ch.id].isNullOrEmpty())
        }

        // Complemento Etapa 18: as posições fixas 1-9 existem independentemente da
        // disponibilidade da transmissão. Um canal aprovado pelo ledger pode ocupar a
        // posição mesmo sem stream (a indisponibilidade afeta só a reprodução). Os
        // registros catalog-only continuam impedidos de ocupar posições fixas, exceto
        // quando explicitamente aprovados pelo ledger (identidade de emissora fixa).
        val fixedPool = filtered.filter { it.id !in catalogOnlyIds || it.id in approvedIds }

        // --- Build fixed top 9 channels (política única - AffiliateRegistry) ---
        val fixedEntities = mutableListOf<ChannelNumberEntity>()
        val fixedChannelIds = mutableSetOf<String>()

        for (config in fixedTop9Config) {
            val targetNumber = config.position

            // Regra absoluta: TV Centro-Sul preservada na posição 8.
            if (config.exactId == null) {
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
                continue
            }

            val match = selectFixedEntity(
                config = config,
                uf = selectedUf,
                available = fixedPool,
                idOf = { it.id },
                nameOf = { it.name }
            ) ?: continue

            val logo = logoFor(match.id)
            val count = streamsByChannel[match.id]?.size ?: 0
            fixedEntities.add(ChannelNumberEntity(
                number = targetNumber,
                channelId = match.id,
                channelName = config.displayName,
                country = match.country,
                categories = match.categories.joinToString(","),
                logoUrl = logo,
                isNsfw = match.is_nsfw,
                streamCount = count,
                affiliateState = getAffiliateState(match.id, config.displayName),
                isAvailable = count > 0
            ))
            fixedChannelIds.add(match.id)
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
                    affiliateState = getAffiliateState(c.id, c.name),
                    isAvailable = count > 0
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
     * Identifica a UF de um canal.
     * 1) Metadado explícito verificado no AffiliateRegistry (channelId real de afiliada regional).
     * 2) Token de UF exato no identificador (delimitado por separadores — não substring arbitrária).
     * 3) Sem informação suficiente: região desconhecida (null).
     *
     * O parâmetro `channelName` é mantido por compatibilidade de assinatura, mas NÃO é usado
     * para inferência: a UF não é deduzida de palavras soltas no nome.
     */
    fun getAffiliateState(channelId: String, channelName: String): String? {
        // 1. Associação verificada rede/UF -> channelId real (reverse map do AffiliateRegistry).
        reverseAffiliateMap[channelId.lowercase()]?.let { return it }

        // 2. Token de UF explícito no identificador (ex.: globo_sp.br -> SP).
        val ufs = AffiliateRegistry.ALL_UFS.toHashSet()
        val idTokens = channelId.uppercase()
            .split('_', '-', '.', '/', ' ')
            .filter { it.isNotBlank() }
        idTokens.firstOrNull { it in ufs }?.let { return it }

        // 3. Região desconhecida.
        return null
    }

    /**
     * Política ÚNICA de seleção da emissora de cada posição fixa (determinística).
     * Ordem: (1) afiliada regional reproduzível da UF; (2) cabeça de rede nacional;
     * (3) nome exato; (4) palavra-chave. NUNCA seleciona afiliada de outro estado.
     */
    private fun <T> selectFixedEntity(
        config: FixedChannel,
        uf: String,
        available: List<T>,
        idOf: (T) -> String,
        nameOf: (T) -> String
    ): T? {
        // Etapa 17 §04: exclui identificadores proibidos para esta posição fixa.
        val blocked = forbiddenFixedIds[config.displayName].orEmpty()
        val pool = if (blocked.isEmpty()) available else available.filter {
            idOf(it).lowercase() !in blocked
        }

        val candidateIds = AffiliateRegistry.candidateIds(config.displayName, uf).toMutableList()
        config.exactId?.let { if (it !in candidateIds) candidateIds += it }

        // 1) afiliada regional da UF; 2) cabeça de rede nacional / ID exato previsto
        for (id in candidateIds) {
            pool.firstOrNull { idOf(it).equals(id, ignoreCase = true) }?.let { return it }
        }

        // Rede de segurança (Risco A): os fallbacks por nome/palavra-chave NUNCA podem
        // escolher uma afiliada regional de OUTRO estado conhecida pelo registro.
        fun isForeignAffiliate(ent: T): Boolean {
            if (uf == "NACIONAL") return false
            val aff = AffiliateRegistry.affiliateForChannelId(idOf(ent)) ?: return false
            return !aff.isHead && aff.uf != uf
        }

        // 3) nome exato (outra fonte nacional já prevista na arquitetura)
        pool.firstOrNull {
            !isForeignAffiliate(it) && nameOf(it).equals(config.exactName, ignoreCase = true)
        }?.let { return it }
        // 4) palavra-chave (último recurso)
        return pool.firstOrNull { ent ->
            !isForeignAffiliate(ent) && config.keywords.any { kw ->
                val k = kw.lowercase()
                idOf(ent).lowercase().contains(k) || nameOf(ent).lowercase().contains(k)
            }
        }
    }

    /**
     * Resolve os 9 canais fixos sobre a lista unificada (M3U + iptv-org), aplicando a
     * política única: afiliada da UF -> cabeça de rede nacional -> indisponível.
     * Determinístico para o mesmo catálogo/UF. Nunca usa afiliada de outro estado.
     */
    fun resolveFixedEntities(
        allEntities: List<ChannelNumberEntity>,
        selectedUf: String
    ): Pair<List<ChannelNumberEntity>, Set<String>> {
        val fixedEntities = mutableListOf<ChannelNumberEntity>()
        val fixedChannelIds = mutableSetOf<String>()

        for (config in fixedTop9Config) {
            val targetNumber = config.position

            // Preservação absoluta: TV Centro-Sul permanece na posição 8.
            // Risco B: identificar pela ID exata; o fallback por nome é estrito (igualdade),
            // para nunca reconhecer um canal diferente cujo nome apenas contenha "Centro Sul".
            if (targetNumber == 8) {
                val match = allEntities.find {
                    it.channelId.equals(tvCentroSulChannel.id, ignoreCase = true)
                } ?: allEntities.find {
                    it.channelName.trim().equals("TV Centro Sul", ignoreCase = true)
                }
                if (match != null) {
                    fixedEntities.add(match.copy(number = 8, channelName = "TV Centro Sul"))
                    fixedChannelIds.add(match.channelId)
                }
                continue
            }

            if (config.exactId == null) continue

            val matched = selectFixedEntity(
                config = config,
                uf = selectedUf,
                available = allEntities,
                idOf = { it.channelId },
                nameOf = { it.channelName }
            ) ?: continue

            fixedEntities.add(matched.copy(
                number = targetNumber,
                channelName = config.displayName
            ))
            fixedChannelIds.add(matched.channelId)
        }

        return Pair(fixedEntities, fixedChannelIds)
    }

    /**
     * Ordem definitiva da grade (Etapa 10 §05, revisada na Etapa 13 §09), determinística:
     *  Bloco 1: posições fixas 1-9 (preservadas exatamente como resolvidas).
     *  Bloco 2: canais CITADOS em `novas regras.md` — bloco alfabético (se
     *           [CatalogFilters.FEATURED_ALPHA_BLOCK] estiver ativo e não for fixo).
     *  Bloco 3: demais canais representados pela Fonte A (Rei dos Canais) — [rdcIds].
     *  Bloco 4: demais canais (iptv-org / internacionais / complementares).
     *
     * Nunca altera as posições 1-9, nunca duplica `channelId` (a entrada já é única)
     * e não depende de ordenação alfabética global. Renumera o restante a partir de 10.
     * A preservação dos canais do usuário (favoritos/último canal) é feita por
     * rastreamento de `channelId` no repositório, nunca por número fixo.
     */
    fun orderGrade(
        entities: List<ChannelNumberEntity>,
        rdcIds: Set<String>
    ): List<ChannelNumberEntity> {
        val fixed = entities.filter { it.number in 1..9 }.sortedBy { it.number }
        val rest = entities.filter { it.number !in 1..9 }

        val featured = if (CatalogFilters.FEATURED_ALPHA_BLOCK) {
            rest.filter { SourcePreferenceRegistry.isFeatured(it.channelId, it.channelName, it.country) }
                .sortedWith(compareBy(
                    { SourcePreferenceRegistry.familyOrderKey(it.channelName) },
                    { SourcePreferenceRegistry.nameOrderKey(it.channelName) },
                    { it.channelId }
                ))
        } else {
            emptyList()
        }
        val featuredIds = featured.mapTo(HashSet()) { it.channelId }

        val remainder = rest.filter { it.channelId !in featuredIds }

        val rdc = remainder.filter { it.channelId in rdcIds }
            .sortedWith(compareBy(
                { categoryWeight(it.categories.split(',').map { c -> c.trim() }) },
                { it.channelName.lowercase() },
                { it.channelId }
            ))
        val others = remainder.filter { it.channelId !in rdcIds }
            .sortedWith(compareBy(
                { countryWeight(it.country) },
                { categoryWeight(it.categories.split(',').map { c -> c.trim() }) },
                { it.channelName.lowercase() },
                { it.channelId }
            ))

        val out = ArrayList<ChannelNumberEntity>(entities.size)
        out.addAll(fixed)
        var number = 10
        for (e in featured) { out.add(e.copy(number = number)); number++ }
        for (e in rdc) { out.add(e.copy(number = number)); number++ }
        for (e in others) { out.add(e.copy(number = number)); number++ }
        return out.sortedBy { it.number }
    }

    /**
     * Etapa 17 §02: navegação sequencial pela GRADE ATIVA COMPLETA (por número), e não
     * por disponibilidade. Canais presentes na grade mas sem fonte reproduzível
     * (registros de catálogo da Fonte A) PERMANECEM na sequência — nunca são pulados.
     * Isso elimina o salto artificial (ex.: 9 -> 14) causado por ocultar canais
     * indisponíveis. A ordem é sempre crescente por número e a busca envolve as pontas.
     */
    fun nextChannel(ordered: List<ChannelNumberEntity>, current: Int): ChannelNumberEntity? {
        if (ordered.isEmpty()) return null
        val list = ordered.sortedBy { it.number }
        val idx = list.indexOfFirst { it.number == current }
        return if (idx == -1) list.first() else list[(idx + 1) % list.size]
    }

    /** Ver [nextChannel]; retorna o canal anterior na grade, com envolução nas pontas. */
    fun prevChannel(ordered: List<ChannelNumberEntity>, current: Int): ChannelNumberEntity? {
        if (ordered.isEmpty()) return null
        val list = ordered.sortedBy { it.number }
        val idx = list.indexOfFirst { it.number == current }
        return if (idx == -1) list.last() else list[(idx - 1 + list.size) % list.size]
    }
}