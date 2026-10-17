package com.zap.tv.util

/**
 * Políticas de composição do catálogo ativo (Etapa 07).
 *
 * Fontes ativas aprovadas:
 *  - FONTE A — Rei dos Canais (`api.reidoscanais.si`): metadados (nome, logotipo,
 *    categoria) e EPG. NÃO fornece streams reproduzíveis (embeds web) — nunca é
 *    entregue ao ExoPlayer.
 *  - FONTE B — iptv-org (`iptv-org.github.io/api`): canais/streams reproduzíveis,
 *    incluindo as bases internacionais e os canais brasileiros.
 *  - EXCEÇÕES — TV Centro-Sul (stream StreamLock próprio, posição 8) e
 *    TV Litoral RN (posição 9).
 *
 * As listas brasileiras antigas (CanaisBR01/02/03.m3u8 -> m3u_catalog.json, seção
 * `brChannels`) são a origem do volume obsoleto (canais mortos e temáticos 24h).
 * Sua ingestão fica DESATIVADA por padrão, de forma reversível: os arquivos
 * originais permanecem no repositório e basta reativar a constante abaixo para
 * recuperar o comportamento anterior.
 *
 * O catálogo ADULTO legado é mantido (recurso do ZAP-TV protegido por PIN), pois a
 * Fonte B praticamente não oferece esse conteúdo. O tratamento está em ChannelRepository.
 */
object CatalogFilters {

    /**
     * Mecanismo reversível de recuperação (Etapa 07 §02).
     * `false` = catálogo ativo sem as listas brasileiras antigas (padrão).
     * `true`  = reativa a ingestão legada (CanaisBR/m3u_catalog.json), preservada.
     */
    const val LEGACY_BR_CATALOG_ENABLED: Boolean = false

    /**
     * Organização da grade (Etapa 13 §09): quando `true`, os canais citados em
     * `docs/novas regras.md` (`SourcePreferenceRegistry.isFeatured`) formam um BLOCO
     * alfabético logo após as posições fixas 1–9, antes dos demais canais da Fonte A.
     * Os canais não citados seguem na ordem determinística já existente (Etapa 10).
     *
     * `false` desativa o bloco e volta à ordenação anterior (mecanismo reversível),
     * sem alterar nenhuma outra decisão.
     */
    const val FEATURED_ALPHA_BLOCK: Boolean = true

    // Prefixo temático [24h]/[24H] no início do nome (convenção das listas antigas).
    private val TEMATIC_24H_PREFIX = Regex("""^\s*\[\s*24\s*h\s*\]""", RegexOption.IGNORE_CASE)
    private val TEMATIC_24H_TEXT = Regex("""\b24\s*horas\b""", RegexOption.IGNORE_CASE)
    private val TEMATIC_24H_ID = Regex("""^24h[_\-.]""", RegexOption.IGNORE_CASE)

    // Nunca excluir notícia / TV ao vivo legítima por transmitir 24h.
    private val NEWS_LIVE_HINTS = listOf(
        "news", "noticia", "notícia", "jornal", "cnn", "bandnews",
        "globonews", "record news", "jovem pan"
    )

    /**
     * Canais temáticos de programação contínua 24h (maratonas), identificados por:
     *  - categoria explícita `maratonar`; ou
     *  - nome com prefixo `[24h]`/`[24H]` ou marcador `24 horas`; ou
     *  - identificador iniciando em `24h_`/`24h-`/`24h.`.
     *
     * Guarda: canais de notícia/TV ao vivo NUNCA são excluídos por esta regra,
     * mesmo que transmitam 24 horas por dia.
     */
    fun isTematic24h(channelId: String, displayName: String, categories: List<String>): Boolean {
        val cats = categories.map { it.trim().lowercase() }
        if (cats.contains("maratonar")) return true

        val name = displayName.trim()
        val id = channelId.trim()
        val flagged = TEMATIC_24H_PREFIX.containsMatchIn(name) ||
            TEMATIC_24H_TEXT.containsMatchIn(name) ||
            TEMATIC_24H_ID.containsMatchIn(id)
        if (!flagged) return false

        // Guarda anti-falso-positivo: notícia/live permanece.
        if (cats.contains("news")) return false
        val lname = name.lowercase()
        val lid = id.lowercase()
        if (NEWS_LIVE_HINTS.any { lname.contains(it) || lid.contains(it) }) return false

        return true
    }

    /**
     * Somente URLs tecnicamente compatíveis com o ExoPlayer (HTTP/HTTPS diretos).
     * Páginas de embed (ex.: rdcanais.org) NÃO são streams e nunca devem ser
     * reproduzidas. Não há extração de DRM/token nem invenção de links alternativos.
     */
    fun isPlayableStreamUrl(url: String?): Boolean {
        val u = url?.trim()?.lowercase() ?: return false
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false
        if (u.contains("rdcanais.org") || u.contains("reidoscanais.si")) return false
        if (u.contains("/embed")) return false
        return true
    }
}
