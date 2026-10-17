package com.zap.tv.util

import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.reidoscanais.RdcCatalogMapper
import com.zap.tv.data.reidoscanais.RdcEmbedDto

/**
 * Planejador de transmissão regional (Etapa 11).
 *
 * Produz a lista DETERMINÍSTICA de tentativas de reprodução de um canal de rede
 * fixa, aplicando a política de prioridade:
 *
 *  1. Afiliada correspondente à UF selecionada.
 *  2. Cabeça nacional da mesma rede.
 *  3. Emissora de referência de SP ou RJ.
 *  4. Outra afiliada autorizada da mesma rede (limitada e determinística).
 *  5. Indisponível (lista vazia).
 *
 * Exceção Etapa 15 §03 / 20 §05 (Globo): a política de fallback é
 * UF -> GLOBO SÃO PAULO -> GLOBO RIO DE JANEIRO -> demais afiliadas -> cabeça nacional
 * (fonte nacional genérica) -> indisponível. Para a rede [REFERENCE_BEFORE_HEAD_NETWORKS]
 * a emissora de referência de SP é avaliada ANTES da cabeça nacional e a cabeça nacional
 * só é tentada DEPOIS das demais afiliadas.
 *
 * A transmissão da UF é SEMPRE a primeira candidata quando existe fonte compatível.
 * Não reordena por latência/resolução. Cada fonte é tentada uma única vez (sem
 * alternâncias infinitas); a repetição de falhas transitórias é responsabilidade do
 * player, não deste plano.
 *
 * As associações regionais vêm SOMENTE da API (provedor "Globo <Estado>") e do
 * AffiliateRegistry — nunca de inferência por nome de URL. O grau de confiança é
 * propagado para diagnóstico e nunca afirmamos reprodução comprovada.
 */
object RegionalSourcePlanner {

    /** Limite de afiliadas "de outra região" tentadas após a UF/nacional/referência. */
    const val MAX_OTHER_AFFILIATES = 3

    /** Limite total de tentativas (evita alternâncias longas/indesejadas). */
    const val MAX_ATTEMPTS = 8

    private val REFERENCE_UFS = listOf("SP", "RJ")

    /**
     * Redes em que a emissora de referência (SP/RJ) precede a cabeça nacional, conforme
     * a política de fallback da Etapa 15 §03 (Globo: UF -> SP -> outra afiliada).
     */
    private val REFERENCE_BEFORE_HEAD_NETWORKS = setOf("Globo")

    private data class Candidate(
        val regionKind: SourceRegionKind,
        val region: String?,
        val label: String,
        val streams: List<ApiStreamDto>,
        val embeds: List<RdcEmbedDto>,
        val confidence: AffiliateRegistry.Confidence
    )

    /**
     * @param network        rede nacional (ex.: "Globo"), conforme a identidade da posição fixa.
     * @param uf             UF escolhida no assistente ("NACIONAL" quando não há UF).
     * @param entityChannelId identificador real do canal da posição fixa (cabeça/afiliada).
     * @param identityLabel  rótulo da identidade exibida (ex.: "Globo").
     * @param directStreams  streams diretos do canal da posição (cabeça/afiliada), já filtrados.
     * @param embeds         embeds da Fonte A casados à rede (podem ser regionais por provedor).
     */
    fun planFixedNetwork(
        network: String,
        uf: String,
        entityChannelId: String,
        identityLabel: String,
        directStreams: List<ApiStreamDto>,
        embeds: List<RdcEmbedDto>
    ): PlaybackPlan {
        val valid = PlaybackResolver.validEmbeds(embeds)
        val usedEmbeds = HashSet<String>()

        fun takeEmbedsFor(region: String?): List<RdcEmbedDto> {
            val taken = valid.filter { e ->
                val r = RdcCatalogMapper.regionForEmbedProvider(e.provider)
                r == region && e.embed_url?.trim()?.let { it !in usedEmbeds } == true
            }
            taken.forEach { it.embed_url?.trim()?.let { u -> usedEmbeds += u } }
            return taken
        }

        // Região do próprio canal da posição fixa (cabeça => null).
        val entityUf = AffiliateRegistry.ufForChannelId(entityChannelId)
        val head = AffiliateRegistry.head(network)
        val isHeadEntity = head?.channelId?.equals(entityChannelId, ignoreCase = true) == true

        // Streams diretos pertencem à região do próprio canal.
        fun directFor(region: String?): List<ApiStreamDto> = when {
            entityUf == null && isHeadEntity && region == null -> directStreams
            entityUf != null && entityUf == region -> directStreams
            else -> emptyList()
        }

        val ufTier = mutableListOf<Candidate>()
        val headTier = mutableListOf<Candidate>()
        val referenceTier = mutableListOf<Candidate>()
        val otherTier = mutableListOf<Candidate>()
        val leftoverTier = mutableListOf<Candidate>()

        // ---- Tier 1: afiliada da UF selecionada ----
        if (uf != "NACIONAL") {
            val aff = AffiliateRegistry.resolveRegional(network, uf)
            val em = takeEmbedsFor(uf)
            val st = directFor(uf)
            if (em.isNotEmpty() || st.isNotEmpty()) {
                ufTier += Candidate(
                    SourceRegionKind.SELECTED_UF, uf,
                    aff?.name ?: "$network $uf", st, em,
                    aff?.confidence ?: AffiliateRegistry.Confidence.PENDENTE
                )
            }
        }

        // ---- Tier 2: cabeça nacional ----
        run {
            val em = takeEmbedsFor(null)
            val st = directFor(null)
            if (em.isNotEmpty() || st.isNotEmpty()) {
                headTier += Candidate(
                    SourceRegionKind.NATIONAL_HEAD, "NACIONAL",
                    head?.name ?: "$network (nacional)", st, em,
                    head?.confidence ?: AffiliateRegistry.Confidence.MEDIA
                )
            }
        }

        // ---- Tier 3: referência SP/RJ (uma ou ambas, na ordem SP -> RJ) ----
        val referenceUsed = mutableSetOf<String>()
        for (ref in REFERENCE_UFS) {
            if (ref == uf) continue
            val aff = AffiliateRegistry.resolveRegional(network, ref)
            val em = takeEmbedsFor(ref)
            val st = directFor(ref)
            if (em.isNotEmpty() || st.isNotEmpty()) {
                referenceTier += Candidate(
                    SourceRegionKind.REFERENCE_SP_RJ, ref,
                    aff?.name ?: "$network $ref", st, em,
                    aff?.confidence ?: AffiliateRegistry.Confidence.MEDIA
                )
                referenceUsed += ref
            }
        }

        // ---- Tier 4: outra afiliada autorizada da mesma rede (limitada) ----
        var otherCount = 0
        for (other in AffiliateRegistry.ALL_UFS) {
            if (otherCount >= MAX_OTHER_AFFILIATES) break
            if (other == uf || other in referenceUsed || other == entityUf) continue
            val aff = AffiliateRegistry.resolveRegional(network, other) ?: continue
            val em = takeEmbedsFor(other)
            val st = directFor(other)
            if (em.isNotEmpty() || st.isNotEmpty()) {
                otherTier += Candidate(
                    SourceRegionKind.OTHER_AFFILIATE, other,
                    aff.name, st, em, aff.confidence
                )
                otherCount++
            }
        }

        // ---- Cinto de segurança: embeds ainda não consumidos (nunca descartar) ----
        val leftover = valid.filter { e -> e.embed_url?.trim()?.let { it !in usedEmbeds } == true }
        if (leftover.isNotEmpty()) {
            leftover.forEach { it.embed_url?.trim()?.let { u -> usedEmbeds += u } }
            leftoverTier += Candidate(
                SourceRegionKind.OTHER_AFFILIATE, null,
                "$network (alternativa)", emptyList(), leftover,
                AffiliateRegistry.Confidence.MEDIA
            )
        }

        // Etapa 20 §05: para a Globo, a ordem é UF -> SP -> RJ -> DEMAIS AFILIADAS ->
        // cabeça nacional (fonte nacional genérica) -> sobras. A cabeça nacional NÃO
        // pode preceder as demais afiliadas (não introduz prioridade superior). As demais
        // redes mantêm a Etapa 11 (UF -> cabeça -> referência SP/RJ -> outras).
        val candidates = if (network in REFERENCE_BEFORE_HEAD_NETWORKS) {
            ufTier + referenceTier + otherTier + headTier + leftoverTier
        } else {
            ufTier + headTier + referenceTier + otherTier + leftoverTier
        }

        val attempts = flatten(network, identityLabel, candidates).take(MAX_ATTEMPTS)
        return PlaybackPlan(attempts)
    }

    private fun flatten(
        network: String,
        identityLabel: String,
        candidates: List<Candidate>
    ): List<PlaybackAttempt> = buildList {
        for (c in candidates) {
            val source = PlaybackSource(
                network = network,
                identityLabel = identityLabel,
                region = c.region,
                regionKind = c.regionKind,
                label = c.label,
                confidence = c.confidence
            )
            if (c.streams.isNotEmpty()) {
                add(PlaybackAttempt(PlaybackMechanism.DIRECT_STREAM, source, streams = c.streams))
            }
            for (e in c.embeds) {
                add(PlaybackAttempt(PlaybackMechanism.WEB_EMBED, source, embed = e))
            }
        }
    }
}
