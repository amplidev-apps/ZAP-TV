package com.zap.tv.data.reidoscanais

import com.zap.tv.data.local.entity.ChannelNumberEntity
import com.zap.tv.data.m3u.M3uCatalog
import com.zap.tv.data.m3u.M3uChannelGroup
import com.zap.tv.util.PlaybackResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fonte A — Rei dos Canais (https://api.reidoscanais.si).
 *
 * Papel no ZAP-TV: metadados brasileiros (nomes, logotipos, categorias) e EPG
 * (current/next). A API NÃO fornece streams reproduzíveis: os embeds são páginas
 * web (rdcanais.org) e por isso a reprodução continua vindo das fontes já
 * existentes (catálogo M3U interno + iptv-org), preservadas integralmente.
 *
 * Regras de robustez:
 * - Falha de rede/indisponibilidade => degrada silenciosamente (retorna null),
 *   sem nunca derrubar o fluxo existente do app.
 * - Cache único de /channels com TTL de 5 minutos, alinhado ao intervalo de
 *   atualização do EPG informado pela documentação da API (evita consultas excessivas).
 */
@Singleton
class ReiDosCanaisRepository @Inject constructor(
    private val api: ReiDosCanaisApi
) {
    companion object {
        const val BASE_URL = "https://api.reidoscanais.si/"
        // Documentação da API: EPG atualizado no servidor a cada 5 minutos.
        private const val CHANNELS_TTL_MS = 5 * 60 * 1000L
    }

    @Volatile
    private var channelsCache: List<RdcChannelDto> = emptyList()

    @Volatile
    private var channelsCacheAtMs: Long = 0L

    @Volatile
    private var matchIndex: Map<String, RdcChannelDto> = emptyMap()

    private val fetchMutex = Mutex()

    /** Verifica a saúde da API (GET /health). Nunca lança exceção. */
    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            api.health().success
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Obtém o catálogo de canais da API com cache de 5 minutos.
     * Retorna lista vazia em caso de falha (rede, 5xx, resposta inválida) — o
     * chamador deve seguir com o fluxo existente, sem alterações.
     */
    suspend fun fetchChannels(forceRefresh: Boolean = false): List<RdcChannelDto> =
        withContext(Dispatchers.IO) {
            val fresh = !forceRefresh &&
                channelsCache.isNotEmpty() &&
                System.currentTimeMillis() - channelsCacheAtMs < CHANNELS_TTL_MS
            if (fresh) return@withContext channelsCache
            fetchMutex.withLock {
                val freshLocked = !forceRefresh &&
                    channelsCache.isNotEmpty() &&
                    System.currentTimeMillis() - channelsCacheAtMs < CHANNELS_TTL_MS
                if (freshLocked) return@withLock channelsCache
                try {
                    val response = api.getChannels()
                    if (response.success && response.data.isNotEmpty()) {
                        channelsCache = response.data
                        channelsCacheAtMs = System.currentTimeMillis()
                        matchIndex = ReiDosCanaisMapper.buildMatchIndex(response.data)
                    }
                } catch (_: Exception) {
                    // Mantém cache anterior (se houver); falha é não destrutiva.
                }
                channelsCache
            }
        }

    /**
     * Enriquece o catálogo M3U existente com metadados da API:
     * - Logotipo: preenchido APENAS quando o canal não possui logo (nunca substitui
     *   logos já funcionantes);
     * - Categoria: refinada apenas para canais genéricos ("general"), sem tocar em
     *   categorizações específicas (maratonar, mundo, news...).
     *
     * Não remove, não renumera e não injeta canais novos na grade — canais RDC sem
     * stream reproduzível permanecem fora da numeração (ver docs 04/06).
     * Em caso de falha da API, retorna o catálogo original intacto.
     */
    suspend fun enrichCatalog(catalog: M3uCatalog): M3uCatalog = withContext(Dispatchers.IO) {
        val rdcChannels = fetchChannels()
        if (rdcChannels.isEmpty()) return@withContext catalog
        val index = if (matchIndex.isNotEmpty()) matchIndex
        else ReiDosCanaisMapper.buildMatchIndex(rdcChannels).also { matchIndex = it }

        fun enrichList(groups: List<M3uChannelGroup>): List<M3uChannelGroup> = groups.map { group ->
            val match = ReiDosCanaisMapper.matchChannel(index, group.channelId, group.displayName)
            if (match == null) group
            else group.copy(
                logoUrl = group.logoUrl ?: match.logo_url,
                categories = ReiDosCanaisMapper.refineCategories(group.categories, match.category)
            )
        }

        catalog.copy(
            brChannels = enrichList(catalog.brChannels),
            ptChannels = catalog.ptChannels,
            usChannels = catalog.usChannels,
            adultChannels = catalog.adultChannels
        )
    }

    /**
     * Enriquece as entidades do catálogo ATIVO (Fonte B + exceções) com metadados
     * da Fonte A:
     * - Logotipo: preenchido apenas quando ausente (nunca substitui logo existente);
     * - Categoria: refinada apenas para canais genéricos ("general").
     *
     * Nunca adiciona, remove ou renumera canais; a API não fornece stream
     * reproduzível, portanto não altera a reprodução. Falha da API => lista original.
     */
    suspend fun enrichEntities(entities: List<ChannelNumberEntity>): List<ChannelNumberEntity> =
        withContext(Dispatchers.IO) {
            val rdcChannels = fetchChannels()
            if (rdcChannels.isEmpty()) return@withContext entities
            val index = if (matchIndex.isNotEmpty()) matchIndex
            else ReiDosCanaisMapper.buildMatchIndex(rdcChannels).also { matchIndex = it }

            entities.map { entity ->
                val match = ReiDosCanaisMapper.matchChannel(index, entity.channelId, entity.channelName)
                    ?: return@map entity
                val newLogo = entity.logoUrl ?: match.logo_url
                val cats = entity.categories.split(',').map { it.trim() }.filter { it.isNotBlank() }
                val newCats = ReiDosCanaisMapper.refineCategories(cats, match.category)
                entity.copy(logoUrl = newLogo, categories = newCats.joinToString(","))
            }
        }

    /**
     * EPG (current/next) para um canal interno. Casa o canal com a API por
     * identificador estável (nome normalizado / channelId) e devolve a programação
     * embutida na listagem, que já é servida pelo cache de 5 minutos — logo não há
     * consultas adicionais à API por sintonia. Retorna null quando não há casamento,
     * quando a API falhou ou quando o canal não possui EPG — o app segue normal.
     */
    suspend fun getEpgForChannel(channelId: String, channelName: String): ChannelEpg? =
        withContext(Dispatchers.IO) {
            val rdcChannels = fetchChannels()
            if (rdcChannels.isEmpty()) return@withContext null
            val index = if (matchIndex.isNotEmpty()) matchIndex
            else ReiDosCanaisMapper.buildMatchIndex(rdcChannels).also { matchIndex = it }

            val match = ReiDosCanaisMapper.matchChannel(index, channelId, channelName)
                ?: return@withContext null

            ReiDosCanaisMapper.toChannelEpg(match)
        }

    /**
     * Fontes de reprodução web (`embed_url`) de um canal interno (Etapa 09).
     *
     * Reutiliza o casamento por identificador estável e o cache de 5 minutos da
     * listagem (sem consultas extras por sintonia). Retorna apenas embeds HTTP/HTTPS
     * válidos, preservando provedor/qualidade e sem duplicar URLs. Lista vazia
     * quando não há casamento, a API falhou ou o canal não possui embeds.
     *
     * Os embeds são páginas de player incorporáveis — nunca entregues ao ExoPlayer.
     */
    suspend fun getEmbedsForChannel(channelId: String, channelName: String): List<RdcEmbedDto> =
        withContext(Dispatchers.IO) {
            val rdcChannels = fetchChannels()
            if (rdcChannels.isEmpty()) return@withContext emptyList()
            val index = if (matchIndex.isNotEmpty()) matchIndex
            else ReiDosCanaisMapper.buildMatchIndex(rdcChannels).also { matchIndex = it }

            val match = ReiDosCanaisMapper.matchChannel(index, channelId, channelName)
                ?: return@withContext emptyList()

            PlaybackResolver.validEmbeds(match.embeds)
        }
}
