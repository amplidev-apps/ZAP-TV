package com.zap.tv.data.reidoscanais

import java.text.Normalizer

/**
 * Mapeamento do catálogo Rei dos Canais (Etapa 08).
 *
 * Papel da Fonte A: identidade, catálogo, categorias, logotipos, EPG e associações
 * regionais. A reprodução vem da Fonte Complementar (iptv-org) SOMENTE quando existe
 * uma fonte autorizada correspondente à MESMA emissora. A API não fornece streams
 * (apenas páginas `rdcanais.org`) e nenhuma página HTML é entregue ao ExoPlayer.
 *
 * Regras de associação (nunca por "semelhança de nomes"):
 * - Canais de rede aberta (Globo/SBT/Record/RedeTV!/Band/TV Cultura) resolvem para a
 *   cabeça nacional verificada da MESMA rede no iptv-org.
 * - Demais canais só casam com o iptv-org do MESMO país (BR) e por nome normalizado
 *   **idêntico**. Marcas iguais de outros países (ex.: `Globo.pt`, `TNT.ru`) são
 *   rejeitadas — são feeds de outra emissora/região.
 * - Sem correspondência comprovada: o canal permanece apenas como registro de catálogo,
 *   com indisponibilidade explícita e sem reprodução.
 */
object RdcCatalogMapper {

    /** Cabeça nacional iptv-org de cada rede aberta (verificada por identificador). */
    data class NetworkHead(val network: String, val rdcId: String, val iptvHeadId: String)

    val networkHeads: List<NetworkHead> = listOf(
        NetworkHead("Globo", "globo", "RedeGlobo.br"),
        NetworkHead("SBT", "sbt", "SBTNacional.br"),
        NetworkHead("Record", "record", "Record.br"),
        NetworkHead("RedeTV!", "redetv", "RedeTV.br"),
        NetworkHead("Band", "band", "Band.br"),
        NetworkHead("TV Cultura", "tvcultura", "TVCultura.br")
    )

    private val rdcIdToHead: Map<String, String> =
        networkHeads.associate { it.rdcId to it.iptvHeadId }

    /** Associação regional identificada pela própria API (identidade), sem reprodução. */
    data class RegionalIdentity(
        val network: String,
        val uf: String,
        val rdcSlug: String,
        val playable: Boolean = false
    )

    private val STATE_NAME_TO_UF: Map<String, String> = mapOf(
        "acre" to "AC", "alagoas" to "AL", "amapa" to "AP", "amazonas" to "AM",
        "bahia" to "BA", "ceara" to "CE", "distrito federal" to "DF",
        "espirito santo" to "ES", "goias" to "GO", "maranhao" to "MA",
        "mato grosso" to "MT", "mato grosso do sul" to "MS", "minas gerais" to "MG",
        "para" to "PA", "paraiba" to "PB", "parana" to "PR", "pernambuco" to "PE",
        "piaui" to "PI", "rio de janeiro" to "RJ", "rio grande do norte" to "RN",
        "rio grande do sul" to "RS", "rondonia" to "RO", "roraima" to "RR",
        "santa catarina" to "SC", "sao paulo" to "SP", "sergipe" to "SE",
        "tocantins" to "TO"
    )

    private val GLOBO_PROVIDER = Regex("""^Globo\s+(.+)$""", RegexOption.IGNORE_CASE)

    /**
     * UF atribuída ao provedor de um embed da Fonte A, quando a própria API o
     * identifica regionalmente (ex.: `Globo Ceará` -> `CE`). Provedores genéricos
     * (ex.: `Servidor Premium`) retornam `null` — são transmissões nacionais.
     *
     * Nunca infere UF de URLs ou de palavras soltas: usa apenas o nome de provedor
     * informado pela API. Ausência de prova => `null` (sem associação inventada).
     */
    fun regionForEmbedProvider(provider: String?): String? {
        val p = provider?.trim() ?: return null
        val m = GLOBO_PROVIDER.matchEntire(p) ?: return null
        return STATE_NAME_TO_UF[normalizeStateName(m.groupValues[1])]
    }


    /** Normaliza nome de estado: minúsculas, sem acentos, preservando espaços. */
    private fun normalizeStateName(value: String): String {
        val decomposed = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKD)
        return buildString {
            for (c in decomposed) {
                if (c.code < 128 && (c in 'a'..'z' || c == ' ')) append(c)
            }
        }.trim()
    }

    /** Chave de comparação: minúsculas, sem acentos, apenas [a-z0-9]. */
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        val sb = StringBuilder()
        for (c in decomposed) if (c.code < 128) sb.append(c)
        return sb.toString().filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.lowercase()
    }

    /**
     * Identificador iptv-org usado para reprodução de um canal da API, ou `null`
     * quando não há fonte autorizada correspondente à mesma emissora.
     *
     * @param iptvByCountryName mapa `nomeNormalizado -> id iptv` restrito ao país do canal.
     */
    fun playbackIdFor(
        rdcId: String,
        rdcName: String,
        iptvByCountryName: Map<String, String>
    ): String? {
        rdcIdToHead[rdcId]?.let { return it }
        return iptvByCountryName[normalize(rdcName)]
    }

    /** `true` quando o identificador representa cabeça de rede aberta conhecida. */
    fun isOpenNetworkHead(rdcId: String): Boolean = rdcIdToHead.containsKey(rdcId)

    /**
     * Extrai as associações regionais fornecidas pela própria API (ex.: `Globo Ceará`
     * -> CE / slug `globoce`). São identidades de catalogação, marcadas como não
     * reproduzíveis (PENDENTE) enquanto não houver fonte autorizada equivalente.
     * Não inventa associações: só devolve o que a API informa.
     */
    fun regionalIdentities(channels: List<RdcChannelDto>): List<RegionalIdentity> =
        buildList {
            for (ch in channels) {
                for (embed in ch.embeds) {
                    val provider = embed.provider ?: continue
                    val m = GLOBO_PROVIDER.matchEntire(provider.trim()) ?: continue
                    val uf = STATE_NAME_TO_UF[normalizeStateName(m.groupValues[1])] ?: continue
                    val slug = embed.embed_url?.substringAfterLast('/')?.trim().orEmpty()
                    if (slug.isNotEmpty()) add(RegionalIdentity("Globo", uf, slug))
                }
            }
        }.distinctBy { it.uf }

    /** Mapa UF -> slug de identidade regional Globo (a partir da API). */
    fun globoRegionalSlugByUf(channels: List<RdcChannelDto>): Map<String, String> =
        regionalIdentities(channels).associate { it.uf to it.rdcSlug }
}
