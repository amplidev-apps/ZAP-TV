package com.zap.tv.util

import com.zap.tv.data.reidoscanais.RdcEmbedDto
import java.text.Normalizer

/**
 * Regras manuais de fontes (Etapa 13) fornecidas pelo usuário em
 * `docs/novas regras.md` após testes reais no navegador.
 *
 * IMPORTANTE — natureza da evidência (Etapa 13 §03):
 *  - "funcionamento confirmado no navegador" NÃO é o mesmo que
 *    "compatível no Android WebView" nem que "reprodução confirmada na TV física".
 *  - As regras abaixo registram a PREFERÊNCIA do usuário e a usam na política de
 *    seleção de fontes permitidas (embeds HTTP/HTTPS da Fonte A + streams iptv-org).
 *  - Nada aqui extrai URLs ocultas, tokens, chaves DRM ou contorna incorporação.
 *  - Canal exclusivo do conjunto "Servidor Premium" não existe (Etapa 12): o que há é
 *    fonte alternativa do MESMO canal. Nenhuma duplicação é criada.
 */
object SourcePreferenceRegistry {

    /**
     * Intenção de fonte para um canal:
     * - [RDC_EMBED_PRIMARY]: a fonte testada/preferida é o embed da Fonte A
     *   (`rdcanais.org/<slug>`, tipicamente provedor "Servidor Premium"). Os streams
     *   iptv-org da mesma emissora permanecem como RECUPERAÇÃO.
     * - [KEEP_EXISTING]: manter a fonte já existente (iptv-org / link atual do app);
     *   NÃO promover o embed da Fonte A a primário (Etapa 13 §05).
     * - [PENDING]: correspondência ainda a confirmar (Etapa 13 §06).
     */
    enum class Preference { RDC_EMBED_PRIMARY, KEEP_EXISTING, PENDING }

    data class Rule(
        val rdcId: String,
        val displayName: String,
        val preference: Preference,
        val embedSlug: String? = null,
        val preferredProvider: String? = null,
        val note: String? = null
    )

    /** Provedor preferido quando há mais de uma fonte alternativa (Etapa 13 §04). */
    const val PREMIUM = "Servidor Premium"
    const val ALTERNATIVE = "Servidor Alternativo"

    /**
     * Redes fixas em que a fonte do IPTV-ORG (stream direto) é a PRIMEIRA tentativa,
     * conforme Etapa 15 §02. Nesses canais o embed da Fonte A, mesmo quando casado
     * (Band/Record possuem registro no Rei dos Canais), NUNCA precede o stream direto.
     * Gazeta não possui registro na Fonte A — opera somente por streams do IPTV-ORG.
     *
     * A garantia efetiva é dada por [RegionalSourcePlanner.flatten] (streams antes dos
     * embeds em cada candidato); este conjunto documenta a intenção e alimenta testes.
     */
    val IPTV_PRIMARY_NETWORKS: Set<String> = setOf("Band", "Record", "Gazeta")

    /** `true` quando a rede deve priorizar o IPTV-ORG (Etapa 15 §02). */
    fun isIptvPrimaryNetwork(network: String?): Boolean =
        network != null && IPTV_PRIMARY_NETWORKS.any { it.equals(network, ignoreCase = true) }

    /**
     * Regras derivadas 1:1 de `docs/novas regras.md`. Canais fixos (1–9) citados no
     * documento (Globo, SBT, Record, RedeTV!, Band) NÃO aparecem aqui: continuam
     * regidos pela Etapa 11 (regionalização) e pelas posições fixas.
     */
    val rules: List<Rule> = listOf(
        // ---- Embed da Fonte A preferido (channels.slug = rdcanais.org/<slug>) ----
        Rule("adultswim", "Adult Swim", Preference.RDC_EMBED_PRIMARY, "adultswim"),
        Rule("ae", "A&E", Preference.RDC_EMBED_PRIMARY, "ae"),
        Rule("amc", "AMC", Preference.RDC_EMBED_PRIMARY, "amc"),
        Rule("animalplanet", "Animal Planet", Preference.RDC_EMBED_PRIMARY, "animalplanet"),
        Rule("axn", "AXN", Preference.RDC_EMBED_PRIMARY, "axn"),
        Rule("bandnews", "Band News", Preference.RDC_EMBED_PRIMARY, "bandnews"),
        Rule("bis", "BIS", Preference.RDC_EMBED_PRIMARY, "bis"),
        Rule("canal-brasil", "Canal Brasil", Preference.RDC_EMBED_PRIMARY, "canalbrasil"),
        Rule("canaloff", "Canal Off", Preference.RDC_EMBED_PRIMARY, "canaloff"),
        Rule("cartoonnetwork", "Cartoon Network", Preference.RDC_EMBED_PRIMARY, "cartoonnetwork"),
        Rule("cazetv", "Cazé TV", Preference.RDC_EMBED_PRIMARY, "cazetv"),
        Rule("cazetv2", "Cazé TV 2", Preference.RDC_EMBED_PRIMARY, "cazetv2"),
        Rule("cazetv3", "Cazé TV 3", Preference.RDC_EMBED_PRIMARY, "cazetv3"),
        Rule("cnnbrasil", "CNN Brasil", Preference.RDC_EMBED_PRIMARY, "cnnbrasil", PREMIUM),
        Rule("discoverychannel", "Discovery Channel", Preference.RDC_EMBED_PRIMARY, "discoverychannel"),
        Rule("discoveryheh", "Discovery Home & Health", Preference.RDC_EMBED_PRIMARY, "discoveryheh"),
        Rule("discoveryid", "Discovery ID", Preference.RDC_EMBED_PRIMARY, "discoveryid"),
        Rule("discoverykids", "Discovery Kids", Preference.RDC_EMBED_PRIMARY, "discoverykids"),
        Rule("discoveryscience", "Discovery Science", Preference.RDC_EMBED_PRIMARY, "discoveryscience", note = "Demora um pouco para conectar"),
        Rule("discoverytheater", "Discovery Theater", Preference.RDC_EMBED_PRIMARY, "discoverytheater"),
        Rule("discoveryworld", "Discovery World", Preference.RDC_EMBED_PRIMARY, "discoveryworld", note = "Demora um pouco para conectar"),
        Rule("fishtv", "Fish TV", Preference.RDC_EMBED_PRIMARY, "fishtv"),
        Rule("foodnetwork", "Food Network", Preference.RDC_EMBED_PRIMARY, "foodnetwork", note = "Demora um pouco para conectar"),
        Rule("futura", "Futura", Preference.RDC_EMBED_PRIMARY, "futura"),
        Rule("globoplaynovelas", "Globoplay Novelas", Preference.RDC_EMBED_PRIMARY, "globoplaynovelas", PREMIUM),
        Rule("gloob", "Gloob", Preference.RDC_EMBED_PRIMARY, "gloob"),
        Rule("gloobinho", "Gloobinho", Preference.RDC_EMBED_PRIMARY, "gloobinho"),
        Rule("gnt", "GNT", Preference.RDC_EMBED_PRIMARY, "gnt"),
        Rule("hbo", "HBO", Preference.RDC_EMBED_PRIMARY, "hbo", note = "Demora um pouco para conectar"),
        Rule("hbo2", "HBO 2", Preference.RDC_EMBED_PRIMARY, "hbo2", note = "Demora um pouco para conectar"),
        Rule("hbofamily", "HBO Family", Preference.RDC_EMBED_PRIMARY, "hbofamily", note = "Trava às vezes"),
        Rule("hbopop", "HBO Pop", Preference.RDC_EMBED_PRIMARY, "hbopop", note = "Trava às vezes"),
        Rule("hbosignature", "HBO Signature", Preference.RDC_EMBED_PRIMARY, "hbosignature", note = "Trava às vezes"),
        Rule("hboxtreme", "HBO Xtreme", Preference.RDC_EMBED_PRIMARY, "hboxtreme", note = "Trava às vezes"),
        Rule("hboplus", "HBO+", Preference.RDC_EMBED_PRIMARY, "hboplus", note = "Trava às vezes"),
        Rule("hgtv", "HGTV", Preference.RDC_EMBED_PRIMARY, "hgtv", note = "Trava às vezes"),
        Rule("history", "History", Preference.RDC_EMBED_PRIMARY, "history"),
        Rule("history2", "History 2", Preference.RDC_EMBED_PRIMARY, "history2", note = "Demora um pouco para conectar/trava às vezes"),
        Rule("megapix", "Megapix", Preference.RDC_EMBED_PRIMARY, "megapix"),
        Rule("modoviagem", "Modo Viagem", Preference.RDC_EMBED_PRIMARY, "modoviagem"),
        Rule("multishow", "Multishow", Preference.RDC_EMBED_PRIMARY, "multishow", PREMIUM),
        Rule("nsports", "NSports", Preference.RDC_EMBED_PRIMARY, "nsports"),
        Rule("premiereclubes", "Premiere", Preference.RDC_EMBED_PRIMARY, "premiereclubes", PREMIUM),
        Rule("premiere2", "Premiere 2", Preference.RDC_EMBED_PRIMARY, "premiere2", PREMIUM),
        Rule("premiere3", "Premiere 3", Preference.RDC_EMBED_PRIMARY, "premiere3", PREMIUM),
        Rule("premiere4", "Premiere 4", Preference.RDC_EMBED_PRIMARY, "premiere4", PREMIUM),
        Rule("premiere5", "Premiere 5", Preference.RDC_EMBED_PRIMARY, "premiere5", PREMIUM),
        Rule("premiere6", "Premiere 6", Preference.RDC_EMBED_PRIMARY, "premiere6", PREMIUM),
        Rule("premiere7", "Premiere 7", Preference.RDC_EMBED_PRIMARY, "premiere7", PREMIUM),
        Rule("premiere8", "Premiere 8", Preference.RDC_EMBED_PRIMARY, "premiere8", PREMIUM),
        Rule("space", "Space", Preference.RDC_EMBED_PRIMARY, "space"),
        Rule("sportv", "SporTV", Preference.RDC_EMBED_PRIMARY, "sportv", PREMIUM),
        Rule("sportv2", "SporTV 2", Preference.RDC_EMBED_PRIMARY, "sportv2", PREMIUM),
        Rule("sportv3", "SporTV 3", Preference.RDC_EMBED_PRIMARY, "sportv3", PREMIUM),
        Rule("sportynet", "SportyNet", Preference.RDC_EMBED_PRIMARY, "sportynet"),
        Rule("studiouniversal", "Studio Universal", Preference.RDC_EMBED_PRIMARY, "studiouniversal"),
        Rule("telecineaction", "Telecine Action", Preference.RDC_EMBED_PRIMARY, "telecineaction"),
        Rule("telecinecult", "Telecine Cult", Preference.RDC_EMBED_PRIMARY, "telecinecult"),
        Rule("telecinefun", "Telecine Fun", Preference.RDC_EMBED_PRIMARY, "telecinefun"),
        Rule("telecinepipoca", "Telecine Pipoca", Preference.RDC_EMBED_PRIMARY, "telecinepipoca", note = "Demora um pouco para conectar/trava às vezes"),
        Rule("telecinepremium", "Telecine Premium", Preference.RDC_EMBED_PRIMARY, "telecinepremium", note = "Trava às vezes"),
        Rule("telecinetouch", "Telecine Touch", Preference.RDC_EMBED_PRIMARY, "telecinetouch", note = "Demora um pouco para conectar/trava às vezes"),
        Rule("tlc", "TLC", Preference.RDC_EMBED_PRIMARY, "tlc", note = "Demora um pouco para conectar/trava às vezes"),
        Rule("tnt", "TNT", Preference.RDC_EMBED_PRIMARY, "tnt"),
        Rule("tntnovelas", "TNT Novelas", Preference.RDC_EMBED_PRIMARY, "tntnovelas", note = "Demora um pouco para conectar/trava às vezes"),
        Rule("tntseries", "TNT Séries", Preference.RDC_EMBED_PRIMARY, "tntseries", note = "Demora um pouco para conectar/trava às vezes"),
        Rule("tooncast", "Tooncast", Preference.RDC_EMBED_PRIMARY, "tooncast", note = "Trava às vezes"),
        Rule("universaltv", "Universal TV", Preference.RDC_EMBED_PRIMARY, "universaltv"),
        Rule("usa", "USA", Preference.RDC_EMBED_PRIMARY, "usa"),
        Rule("warnertv", "Warner TV", Preference.RDC_EMBED_PRIMARY, "warnertv", note = "Trava às vezes"),
        Rule("woohoo", "WooHoo", Preference.RDC_EMBED_PRIMARY, "woohoo"),
        Rule("xsports", "XSports", Preference.RDC_EMBED_PRIMARY, "xsports"),

        // ---- Manter fonte existente (iptv-org / link atual do app) ----
        Rule("globonews", "Globo News", Preference.KEEP_EXISTING, note = "Manter a do IPTV-ORG"),
        Rule("sbtnews", "SBT News", Preference.KEEP_EXISTING, note = "Manter link atual do app, já funcional"),
        Rule("sonychannel", "Sony Channel", Preference.KEEP_EXISTING, note = "Manter link atual, já funcional"),
        Rule("sonymovies", "Sony Movies", Preference.KEEP_EXISTING, note = "Manter link atual, já funcional"),
        Rule("aparecida", "TV Aparecida", Preference.KEEP_EXISTING, note = "Manter link atual, já funcional"),
        Rule("tvbrasil", "TV Brasil", Preference.KEEP_EXISTING, note = "Usar link IPTV-ORG"),
        Rule("tvcultura", "TV Cultura", Preference.KEEP_EXISTING, note = "Manter link atual"),

        // ---- Verificação pendente (Etapa 13 §06) ----
        Rule("combate", "Combate", Preference.KEEP_EXISTING, note = "PDC: conferir IPTV-ORG (encontrado Combate.br) — manter fonte existente"),
        Rule("jpnews", "JP News", Preference.PENDING, note = "Não conecta no navegador; sem correspondente reproduzível no IPTV-ORG — permanece indisponível")
    )

    private val byRdcId: Map<String, Rule> =
        rules.associateBy { it.rdcId.lowercase() }

    private val byNameKey: Map<String, Rule> =
        rules.associateBy { key(it.displayName) }

    /** Chaves normalizadas de TODOS os canais citados no documento (para ordenação). */
    private val featuredNameKeys: Set<String> =
        rules.map { key(it.displayName) }.filter { it.isNotBlank() }.toSet()

    private val featuredRdcIds: Set<String> =
        rules.map { it.rdcId.lowercase() }.toSet()

    private fun rdcIdOf(entityChannelId: String): String = entityChannelId.removePrefix("rdc:").lowercase()

    /**
     * Normalização LOCAL e robusta (minúsculas, sem acentos, apenas [a-z0-9]). Não
     * reutiliza `ReiDosCanaisMapper.normalizeKey` de propósito: aquele método filtra
     * `a..z` ANTES de minusculizar e descarta letras maiúsculas — o que é inofensivo
     * quando os dois lados passam pelo mesmo caminho, mas inútil para comparar um
     * nome de canal (Title Case) com uma chave de regra escrita à mão.
     */
    private fun key(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        val sb = StringBuilder()
        for (c in decomposed) {
            if (c.code < 128) {
                val lc = c.lowercaseChar()
                if (lc in 'a'..'z' || lc in '0'..'9') sb.append(lc)
            }
        }
        return sb.toString()
    }

    /** Regra aplicável a um canal interno (por id da Fonte A ou por nome normalizado). */
    fun ruleFor(entityChannelId: String, displayName: String): Rule? {
        byRdcId[rdcIdOf(entityChannelId)]?.let { return it }
        byNameKey[key(displayName)]?.let { return it }
        return null
    }

    /** Preferência do canal, ou `null` quando o documento não o cita (comportamento padrão). */
    fun preferenceFor(entityChannelId: String, displayName: String): Preference? =
        ruleFor(entityChannelId, displayName)?.preference

    /**
     * O canal está citado no documento de regras manuais? Usado para a organização
     * (bloco alfabético após os fixos). Só canais brasileiros (ou registros `rdc:`)
     * contam — evita capturar homônimos de outros países no iptv-org.
     */
    fun isFeatured(entityChannelId: String, displayName: String, country: String): Boolean {
        val isRdc = entityChannelId.startsWith("rdc:")
        if (!isRdc && !country.equals("BR", ignoreCase = true)) return false
        if (rdcIdOf(entityChannelId) in featuredRdcIds) return true
        return key(displayName) in featuredNameKeys
    }

    /**
     * Seleciona o embed preferido entre as fontes de um canal, segundo a regra:
     *  - [Preference.RDC_EMBED_PRIMARY]: o embed do `slug` indicado (preferindo o
     *    provedor "Servidor Premium" quando houver mais de um);
     *  - [Preference.KEEP_EXISTING] / [Preference.PENDING]: `null` (não promove embed).
     */
    fun preferredEmbed(entityChannelId: String, displayName: String, embeds: List<RdcEmbedDto>): RdcEmbedDto? {
        val rule = ruleFor(entityChannelId, displayName) ?: return null
        if (rule.preference != Preference.RDC_EMBED_PRIMARY) return null
        val slug = rule.embedSlug ?: return null
        val candidates = embeds.filter { e ->
            val u = e.embed_url?.trim()?.lowercase() ?: return@filter false
            u.endsWith("/$slug") || u.endsWith("/$slug/")
        }
        if (candidates.isEmpty()) return null
        rule.preferredProvider?.let { prov ->
            candidates.firstOrNull { it.provider?.equals(prov, ignoreCase = true) == true }?.let { return it }
        }
        return candidates.first()
    }

    /** Observação do usuário (lentidão/travamento) para diagnóstico (Etapa 13 §07). */
    fun noteFor(entityChannelId: String, displayName: String): String? =
        ruleFor(entityChannelId, displayName)?.note

    /**
     * Chave canônica de ordenação alfabética (Etapa 14 §06): minúscula, sem acentos,
     * apenas dígitos/letras. Estável e determinística — usada no bloco dos citados para
     * que a ordem não dependa de acentuação nem de capitalização do nome de exibição.
     */
    fun nameOrderKey(displayName: String?): String = key(displayName)

    /**
     * Etapa 17 §03: chave de FAMÍLIA usada como critério primário do bloco destacado,
     * garantindo que variações da mesma emissora permaneçam consecutivas. Remove o
     * sufixo numérico de variante ("2", "3", ...) para agrupar "Premiere 2" com
     * "Premiere", "Cazé TV 2" com "Cazé TV", "HBO 2" com "HBO", etc. Em seguida o
     * desempate é alfabético por [nameOrderKey], preservando a ordem estável.
     */
    fun familyOrderKey(displayName: String?): String =
        KEY_VARIANT_SUFFIX.replace(key(displayName), "")

    private val KEY_VARIANT_SUFFIX = Regex("\\d+$")
}
