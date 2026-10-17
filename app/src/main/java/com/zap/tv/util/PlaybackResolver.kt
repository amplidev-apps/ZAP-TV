package com.zap.tv.util

import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.reidoscanais.RdcEmbedDto

/**
 * Mecanismo de reprodução do player híbrido (Etapa 09).
 *
 * - [DIRECT_STREAM]: stream HLS/DASH/etc. reproduzível pelo Media3/ExoPlayer.
 * - [WEB_EMBED]: página de player incorporável (`embed_url`) carregada em WebView.
 * - [UNAVAILABLE]: nenhuma fonte compatível/autorizada disponível.
 */
enum class PlaybackMechanism { DIRECT_STREAM, WEB_EMBED, UNAVAILABLE }

/**
 * Região atribuída à FONTE em reprodução (Etapa 11), separada da IDENTIDADE do canal.
 *
 * - [SELECTED_UF]: afiliada da UF escolhida no assistente.
 * - [NATIONAL_HEAD]: cabeça de rede nacional.
 * - [REFERENCE_SP_RJ]: emissora de referência de São Paulo/Rio de Janeiro.
 * - [OTHER_AFFILIATE]: outra afiliada autorizada da mesma rede.
 * - [UNREGIONALIZED]: canal sem regionalização por rede (fontes do próprio canal).
 */
enum class SourceRegionKind { SELECTED_UF, NATIONAL_HEAD, REFERENCE_SP_RJ, OTHER_AFFILIATE, UNREGIONALIZED }

/**
 * Metadados da transmissão efetivamente escolhida. Permite que a UI informe a
 * FONTE REAL quando houver fallback (nunca exibir a região errada como se fosse a
 * afiliada da UF — ver Etapa 11 §04).
 */
data class PlaybackSource(
    val network: String?,
    val identityLabel: String?,
    val region: String?,
    val regionKind: SourceRegionKind,
    val label: String,
    val confidence: AffiliateRegistry.Confidence = AffiliateRegistry.Confidence.ALTA
)

/**
 * Uma tentativa de reprodução atômica. Para [PlaybackMechanism.DIRECT_STREAM] carrega
 * a lista de streams dessa fonte (o ExoPlayerManager percorre-os internamente); para
 * [PlaybackMechanism.WEB_EMBED] carrega um único embed autorizado.
 */
data class PlaybackAttempt(
    val mechanism: PlaybackMechanism,
    val source: PlaybackSource,
    val streams: List<ApiStreamDto> = emptyList(),
    val embed: RdcEmbedDto? = null
)

/**
 * Plano de reprodução: lista ORDENADA e determinística de tentativas. O player
 * percorre as tentativas na ordem; cada fonte é tentada uma única vez (com as
 * repetições transitórias internas do player), sem alternâncias infinitas.
 */
data class PlaybackPlan(
    val attempts: List<PlaybackAttempt> = emptyList()
) {
    val mechanism: PlaybackMechanism
        get() = attempts.firstOrNull()?.mechanism ?: PlaybackMechanism.UNAVAILABLE

    val isEmpty: Boolean get() = attempts.isEmpty()

    /** Streams da primeira fonte direta (compatibilidade). */
    val directStreams: List<ApiStreamDto>
        get() = attempts.firstOrNull { it.mechanism == PlaybackMechanism.DIRECT_STREAM }?.streams ?: emptyList()

    /** Embeds de todas as fontes web, na ordem do plano (compatibilidade). */
    val webEmbeds: List<RdcEmbedDto>
        get() = attempts.filter { it.mechanism == PlaybackMechanism.WEB_EMBED }.mapNotNull { it.embed }
}

/**
 * Seleção centralizada do mecanismo/fonte de reprodução (nunca um componente por canal).
 *
 * A regionalização (Etapa 11) é aplicada em [resolveForChannel] quando o canal é de
 * rede fixa: a transmissão da UF escolhida é a PRIMEIRA candidata, seguida de cabeça
 * nacional, referência SP/RJ, outra afiliada e, por fim, indisponível.
 *
 * Segurança: apenas esquemas HTTP/HTTPS são aceitos; nenhuma verificação TLS é
 * afrouxada, nenhuma URL interna é extraída de players protegidos e nenhuma
 * restrição de incorporação/autenticação/DRM é contornada.
 */
object PlaybackResolver {

    /** `true` quando a URL pode ser carregada em WebView (HTTP/HTTPS). */
    fun isEmbeddableUrl(url: String?): Boolean {
        val u = url?.trim()?.lowercase() ?: return false
        return u.startsWith("https://") || u.startsWith("http://")
    }

    /** Embeds válidos, preservando provedor/qualidade e sem duplicar URLs. */
    fun validEmbeds(embeds: List<RdcEmbedDto>): List<RdcEmbedDto> =
        embeds.filter { isEmbeddableUrl(it.embed_url) }
            .distinctBy { it.embed_url?.trim() }

    /**
     * Fonte não regionalizada (demais canais): streams diretos do próprio canal
     * primeiro, depois seus embeds. Preserva o comportamento da Etapa 09.
     */
    fun resolve(directStreams: List<ApiStreamDto>, embeds: List<RdcEmbedDto>): PlaybackPlan =
        resolve(channelId = "", displayName = "", directStreams = directStreams, embeds = embeds)

    /**
     * Fonte não regionalizada COM preferência manual (Etapa 13). Quando o canal é
     * citado em `novas regras.md` com [SourcePreferenceRegistry.Preference.RDC_EMBED_PRIMARY],
     * o embed testado da Fonte A passa a ser a FONTE PRIMÁRIA e os streams iptv-org da
     * mesma emissora ficam como recuperação. Canais não citados mantêm a Etapa 09.
     */
    fun resolve(
        channelId: String,
        displayName: String,
        directStreams: List<ApiStreamDto>,
        embeds: List<RdcEmbedDto>
    ): PlaybackPlan {
        val we = validEmbeds(embeds)
        val preferred = SourcePreferenceRegistry.preferredEmbed(channelId, displayName, we)
        val preferredUrl = preferred?.embed_url?.trim()
        val source = PlaybackSource(
            network = null,
            identityLabel = null,
            region = null,
            regionKind = SourceRegionKind.UNREGIONALIZED,
            label = "Fonte direta",
            confidence = AffiliateRegistry.Confidence.ALTA
        )
        val attempts = buildList {
            if (preferred != null) {
                add(PlaybackAttempt(PlaybackMechanism.WEB_EMBED, source, embed = preferred))
            }
            if (directStreams.isNotEmpty()) {
                add(PlaybackAttempt(PlaybackMechanism.DIRECT_STREAM, source, streams = directStreams))
            }
            for (e in we) {
                if (e.embed_url?.trim() == preferredUrl) continue
                add(PlaybackAttempt(PlaybackMechanism.WEB_EMBED, source, embed = e))
            }
        }
        return PlaybackPlan(attempts)
    }

    /**
     * Plano com regionalização real (Etapa 11) para canais de rede fixa; ou o
     * comportamento padrão quando [fixedNetwork] é nulo (canais não fixos).
     */
    fun resolveForChannel(
        fixedNetwork: String?,
        uf: String,
        entityChannelId: String,
        identityLabel: String?,
        directStreams: List<ApiStreamDto>,
        embeds: List<RdcEmbedDto>
    ): PlaybackPlan =
        if (fixedNetwork == null) {
            resolve(
                channelId = entityChannelId,
                displayName = identityLabel ?: "",
                directStreams = directStreams,
                embeds = embeds
            )
        } else {
            RegionalSourcePlanner.planFixedNetwork(
                network = fixedNetwork,
                uf = uf,
                entityChannelId = entityChannelId,
                identityLabel = identityLabel ?: fixedNetwork,
                directStreams = directStreams,
                embeds = embeds
            )
        }
}
