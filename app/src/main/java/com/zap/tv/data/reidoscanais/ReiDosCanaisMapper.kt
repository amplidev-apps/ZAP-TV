package com.zap.tv.data.reidoscanais

import java.text.Normalizer

/**
 * Conversão e casamento entre o catálogo Rei dos Canais e os canais internos do ZAP-TV.
 *
 * Estratégia de casamento (identificadores estáveis, não apenas nomes crus):
 * 1. Chave normalizada ASCII do nome de exibição (idêntica à base de geração de
 *    channelIds do M3U — M3uChannelMapper.generateChannelId), o que equivale a um
 *    identificador estável dentro do projeto;
 * 2. Chave normalizada do channelId interno (fallback);
 * 3. Contenção única com guard de comprimento mínimo (apenas candidatos >= 4 chars,
 *    e apenas quando exatamente um candidato é encontrado).
 *
 * O casamento nunca remove nem renumera canais: serve apenas para enriquecer
 * metadados (logotipo/categoria) e ligar EPG.
 */
object ReiDosCanaisMapper {

    /** Chave de comparação: minúsculas, sem acentos, apenas [a-z0-9]. */
    fun normalizeKey(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        val ascii = StringBuilder()
        for (c in decomposed) {
            if (c.code < 128) ascii.append(c)
        }
        return ascii.toString().filter { it in 'a'..'z' || it in '0'..'9' }.lowercase()
    }

    private const val MIN_CONTAINMENT_LEN = 4

    // Mapeia categorias da API (PT-BR) para os slugs internos usados pelo app.
    private val CATEGORY_MAP = mapOf(
        "Canais Abertos" to "general",
        "Documentários" to "documentary",
        "Entretenimento" to "entertainment",
        "Esportes" to "sports",
        "Filmes e Séries" to "movies",
        "Infantil" to "kids",
        "Notícias" to "news"
    )

    fun mapCategory(rdcCategory: String?): String? {
        if (rdcCategory.isNullOrBlank()) return null
        return CATEGORY_MAP[rdcCategory.trim()]
    }

    fun toEpgProgram(p: RdcProgramDto?): EpgProgram? {
        if (p == null) return null
        val title = p.title?.trim().orEmpty()
        if (title.isEmpty() && p.start_time == null) return null
        return EpgProgram(
            title = title.ifEmpty { "Programação" },
            description = p.description,
            formattedTime = p.formatted_time,
            startTimeSec = p.start_time,
            endTimeSec = p.end_time,
            imageUrl = p.image
        )
    }

    fun toChannelEpg(rdcChannel: RdcChannelDto): ChannelEpg? {
        val epg = rdcChannel.epg ?: return null
        val current = toEpgProgram(epg.current)
        val next = toEpgProgram(epg.next)
        if (current == null && next == null) return null
        return ChannelEpg(
            rdcChannelId = rdcChannel.id,
            current = current,
            next = next
        )
    }

    /** Índice chave-normalizada -> canal RDC (nome mais curto vence em conflito). */
    fun buildMatchIndex(channels: List<RdcChannelDto>): Map<String, RdcChannelDto> {
        val index = LinkedHashMap<String, RdcChannelDto>()
        for (ch in channels.sortedBy { it.name.length }) {
            val key = normalizeKey(ch.name)
            if (key.isNotEmpty()) index[key] = ch
        }
        return index
    }

    /**
     * Localiza o canal RDC correspondente a um canal interno.
     * [channelName] e [channelId] são usados como chaves estáveis de comparação.
     */
    fun matchChannel(
        index: Map<String, RdcChannelDto>,
        channelId: String,
        channelName: String
    ): RdcChannelDto? {
        val nameKey = normalizeKey(channelName)
        if (nameKey.isNotEmpty()) {
            index[nameKey]?.let { return it }
        }
        val idKey = normalizeKey(channelId.substringBeforeLast('.'))
        if (idKey.isNotEmpty()) {
            index[idKey]?.let { return it }
        }
        // Contenção única (guard >= 4 chars) — evita falsos positivos com chaves curtas.
        val query = if (nameKey.length >= idKey.length) nameKey else idKey
        if (query.length >= MIN_CONTAINMENT_LEN) {
            val candidates = index.keys.filter { k ->
                k.length >= MIN_CONTAINMENT_LEN && (k.contains(query) || query.contains(k))
            }
            if (candidates.size == 1) return index[candidates.first()]
        }
        return null
    }

    /**
     * Refina a categoria interna de um canal casado com a API.
     * Não substitui categorizações específicas já existentes (ex: maratonar, mundo, news):
     * apenas canais genéricos ("general" ou vazios) recebem a categoria da API.
     */
    fun refineCategories(existing: List<String>, rdcCategory: String?): List<String> {
        val mapped = mapCategory(rdcCategory) ?: return existing
        val cleaned = existing.filter { it.isNotBlank() }
        if (cleaned.isNotEmpty() && cleaned.any { it.lowercase() != "general" }) return cleaned
        return listOf(mapped)
    }
}
