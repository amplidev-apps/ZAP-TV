package com.zap.tv.data.repository

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import com.zap.tv.BuildConfig
import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiLogoDto
import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.api.IptvApiService
import com.zap.tv.data.local.dao.*
import com.zap.tv.data.local.entity.AppFlagEntity
import com.zap.tv.data.local.entity.ChannelNumberEntity
import com.zap.tv.data.local.entity.FavoriteEntity
import com.zap.tv.data.local.entity.LastChannelEntity
import com.zap.tv.data.local.entity.StateConfigEntity
import com.zap.tv.data.m3u.M3uCatalog
import com.zap.tv.data.m3u.M3uChannelGroup
import com.zap.tv.data.m3u.M3uStreamInfo
import com.zap.tv.data.m3u.toApiStream
import com.zap.tv.data.reidoscanais.RdcCatalogMapper
import com.zap.tv.data.reidoscanais.RdcChannelDto
import com.zap.tv.data.reidoscanais.ReiDosCanaisMapper
import com.zap.tv.data.reidoscanais.ReiDosCanaisRepository
import com.zap.tv.util.ApprovedCatalogLedger
import com.zap.tv.util.CatalogFilters
import com.zap.tv.util.ChannelNumbering
import com.zap.tv.util.ParentalUtils
import com.zap.tv.util.StreamHealthChecker
import com.zap.tv.util.StreamPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val api: IptvApiService,
    private val rdcRepository: ReiDosCanaisRepository,
    private val channelNumberDao: ChannelNumberDao,
    private val stateConfigDao: StateConfigDao,
    private val lastChannelDao: LastChannelDao,
    private val favoriteDao: FavoriteDao,
    private val appFlagDao: AppFlagDao,
    private val parentalDao: ParentalPinDao,
    @ApplicationContext private val ctx: Context
) {
    // Custom streams that don't exist in iptv-org API
    private val customStreams = mapOf(
        "tv_centro_sul_br" to ApiStreamDto(
            channel = "tv_centro_sul_br",
            feed = null,
            title = "TV Centro Sul",
            url = "https://5c483b9d1019c.streamlock.net/tvcentrosul/tvcentrosul/playlist.m3u8",
            referrer = null,
            user_agent = null,
            quality = "HD",
            label = "TV Centro Sul"
        )
    )

    // M3U Catalog cache
    private var m3uCatalog: M3uCatalog? = null

    // Cache de streams do iptv-org (Etapa 10 §11): evita baixar streams.json a cada
    // troca de canal. TTL curto para não servir dados velhos indefinidamente.
    @Volatile private var streamsCache: List<ApiStreamDto>? = null
    @Volatile private var streamsCacheAtMs: Long = 0L

    private companion object {
        // Verificação inicial: no máximo N canais e T ms (nunca bloqueia o setup).
        const val VERIFY_MAX_CHANNELS = 80
        const val VERIFY_TIMEOUT_MS = 20_000L
        const val STREAMS_TTL_MS = 10 * 60 * 1000L

        // Etapa 18: semente empacotada do catálogo complementar aprovado (primeira
        // execução, independente de rede). Ver assets/approved_catalog_seed.json.
        const val APPROVED_SEED_ASSET = "approved_catalog_seed.json"
    }

    /** Streams do iptv-org com cache em memória (evita requisições por troca de canal). */
    private suspend fun getStreamsCached(): List<ApiStreamDto> {
        val now = System.currentTimeMillis()
        streamsCache?.let { if (now - streamsCacheAtMs < STREAMS_TTL_MS) return it }
        val fresh = api.getStreams()
        streamsCache = fresh
        streamsCacheAtMs = now
        return fresh
    }

    /**
     * Etapa 14 §03: reutiliza a lista de streams já baixada no sync/sintonia como cache
     * quente. Evita um segundo download de `streams.json` na primeira troca de canal
     * (a maior espera observada ao sintonizar logo após o assistente).
     */
    private fun seedStreamsCache(streams: List<ApiStreamDto>) {
        if (streams.isEmpty()) return
        streamsCache = streams
        streamsCacheAtMs = System.currentTimeMillis()
    }

    // ------------------------------------------------------------------ Etapa 18
    // Ledger persistente do catálogo complementar aprovado (identidades estáveis).

    /** Lê o ledger persistido. Falha => conjunto vazio (nunca derruba a sincronização). */
    private suspend fun loadApprovedLedger(): Set<String> {
        val raw = try { appFlagDao.get(ApprovedCatalogLedger.FLAG_KEY) } catch (_: Exception) { null }
        return ApprovedCatalogLedger.parse(raw)
    }

    /** Persiste o ledger (monotônico, nunca remove identidades). Falha é não destrutiva. */
    private suspend fun persistApprovedLedger(ids: Set<String>) {
        if (ids.isEmpty()) return
        try {
            appFlagDao.set(AppFlagEntity(ApprovedCatalogLedger.FLAG_KEY, ApprovedCatalogLedger.serialize(ids)))
        } catch (_: Exception) {
            // Persistência é resiliente: uma falha não pode remover canais da grade.
        }
    }

    /**
     * Semente empacotada do catálogo aprovado (assets/approved_catalog_seed.json).
     * Independente de rede/stream: garante que a PRIMEIRA execução (banco vazio) já
     * nasça com a composição aprovada, sem depender do download de streams.
     */
    private fun loadSeedApprovedIds(): Set<String> = try {
        ctx.assets.open(APPROVED_SEED_ASSET).bufferedReader().use { reader ->
            Gson().fromJson(reader, ApprovedSeedFile::class.java)?.ids?.toSet() ?: emptySet()
        }
    } catch (_: Exception) {
        emptySet()
    }

    private data class ApprovedSeedFile(val version: Int = 1, val ids: List<String> = emptyList())

    suspend fun isSetupComplete(): Boolean = appFlagDao.get("setup_complete") == "1"

    suspend fun completeSetup(uf: String, pin: String?) = withContext(Dispatchers.IO) {
        stateConfigDao.save(StateConfigEntity(uf = uf))
        val hash = pin?.takeIf { it.length==4 }?.let { ParentalUtils.hash(it) }
        parentalDao.save(com.zap.tv.data.local.entity.ParentalPinEntity(pinHash = hash))
        appFlagDao.set(com.zap.tv.data.local.entity.AppFlagEntity("setup_complete","1"))
        syncChannelsWithM3u(uf)
    }

    suspend fun completeSetupWithVerification(uf: String, pin: String?, onProgress: (currentName: String, checked: Int, total: Int, alive: Int, added: Int) -> Unit = {_,_,_,_,_ ->}) = withContext(Dispatchers.IO) {
        stateConfigDao.save(StateConfigEntity(uf = uf))
        val hash = pin?.takeIf { it.length==4 }?.let { ParentalUtils.hash(it) }
        parentalDao.save(com.zap.tv.data.local.entity.ParentalPinEntity(pinHash = hash))
        appFlagDao.set(com.zap.tv.data.local.entity.AppFlagEntity("setup_complete","1"))
        syncChannelsWithVerification(uf, onProgress)
    }

    suspend fun syncChannels(uf: String): List<ChannelNumberEntity> = withContext(Dispatchers.IO) {
        val channels = api.getChannels()
        val streams = api.getStreams()
        val logos = api.getLogos()
        val numbered = ChannelNumbering.buildNumberedChannels(channels, streams, logos, uf)
        channelNumberDao.clear()
        channelNumberDao.insertAll(numbered)
        numbered
    }

    suspend fun syncChannelsWithM3u(uf: String): List<ChannelNumberEntity> = withContext(Dispatchers.IO) {
        // Carrega catálogo M3U se ainda não carregado
        val m3uCatalog = loadM3uCatalog()
        
        // Baixa dados do iptv-org
        val channels = api.getChannels()
        val streams = api.getStreams()
        val logos = api.getLogos()
        
        // Merge: iptv-org + M3U
        val numbered = mergeChannelsWithM3u(channels, streams, logos, uf, m3uCatalog)
        
        channelNumberDao.clear()
        channelNumberDao.insertAll(numbered)
        numbered
    }

    suspend fun syncChannelsWithVerification(uf: String, onProgress: (currentName: String, checked: Int, total: Int, alive: Int, added: Int) -> Unit = {_,_,_,_,_ ->}): List<ChannelNumberEntity> = withContext(Dispatchers.IO) {

        val (channels, streams, logos) = try {
            Triple(api.getChannels(), api.getStreams(), api.getLogos())
        } catch (e: Exception) {
            // Fonte B indisponível: nunca bloqueia o setup — preserva o catálogo já
            // existente (se houver) em vez de esvaziá-lo (Etapa 07 §04).
            val existing = channelNumberDao.getAll()
            if (existing.isNotEmpty()) {
                onProgress("Catálogo anterior mantido", 1, 1, 0, existing.size)
                return@withContext existing
            }
            Triple(emptyList<ApiChannelDto>(), emptyList<ApiStreamDto>(), emptyList<ApiLogoDto>())
        }
        seedStreamsCache(streams)

        // Catálogo legado: com as listas BR antigas desativadas, contém somente o
        // catálogo adulto preservado (recurso do app) — ver CatalogFilters.
        val m3uCatalog = loadM3uCatalog()

        val streamsByChannel = streams.groupBy { it.channel }.toMutableMap()

        // Etapa 10 §11: popula o cache de streams para as trocas de canal seguintes.
        streamsCache = streams
        streamsCacheAtMs = System.currentTimeMillis()

        if (m3uCatalog != null) {
            for (channel in m3uCatalog.brChannels + m3uCatalog.ptChannels + m3uCatalog.usChannels + m3uCatalog.adultChannels) {
                if (CatalogFilters.isTematic24h(channel.channelId, channel.displayName, channel.categories)) continue
                val m3uStreams = channel.streams.map { toApiStream(it, channel.channelId) }
                    .filter { CatalogFilters.isPlayableStreamUrl(it.url) }
                val existing = streamsByChannel[channel.channelId] ?: emptyList()
                streamsByChannel[channel.channelId] = existing + m3uStreams
            }
        }

        customStreams.forEach { (channelId, stream) ->
            val existing = streamsByChannel[channelId] ?: emptyList()
            streamsByChannel[channelId] = existing + stream
        }

        // Composição do catálogo ativo.
        // - Modo padrão (Etapa 08): a Fonte A (Rei dos Canais) é a origem PRINCIPAL do
        //   catálogo brasileiro, importada de forma independente. A reprodução só é
        //   ligada quando há fonte autorizada da MESMA emissora no iptv-org.
        // - Modo legado reversível (CatalogFilters.LEGACY_BR_CATALOG_ENABLED = true):
        //   comportamento anterior (M3U + iptv-org + enriquecimento por nome).
        // Grade anterior à regravação: base do ledger (instalações existentes) e da
        // preservação de favoritos/último canal (por channelId).
        val previous = channelNumberDao.getAll()

        var assembledReport: AssembledCatalog? = null
        var mergedNumbered: List<ChannelNumberEntity> = if (CatalogFilters.LEGACY_BR_CATALOG_ENABLED) {
            enrichWithRdc(mergeChannelsWithM3u(channels, streams, logos, uf, m3uCatalog))
        } else {
            val rdcChannels = try {
                rdcRepository.fetchChannels()
            } catch (_: Exception) {
                emptyList()
            }
            // Etapa 18: o pertencimento ao catálogo ativo passa a ser uma decisão de
            // IDENTIDADE (ledger), nunca de stream. Fontes unidas monotonicamente:
            //  - ledger persistido (execuções anteriores);
            //  - composição já gravada na grade local (instalações existentes);
            //  - semente empacotada (primeira execução, sem depender de rede);
            //  - identidades das posições fixas 1-9.
            val baseApproved = ApprovedCatalogLedger.merge(
                loadApprovedLedger(),
                previous.map { it.channelId },
                loadSeedApprovedIds(),
                ChannelNumbering.fixedApprovedIds
            )
            assembleRdcPrimaryCatalog(channels, streams, logos, uf, m3uCatalog, rdcChannels, baseApproved)
                .also {
                    assembledReport = it
                    persistApprovedLedger(it.approvedIds)
                }
                .entities
        }

        // Filtro de segurança (defesa em profundidade): nunca persistir temáticos 24h.
        mergedNumbered = mergedNumbered.filterNot {
            CatalogFilters.isTematic24h(it.channelId, it.channelName, it.categories.split(','))
        }

        // Etapa 13 §08: a grade final é contada UMA vez por canalId (sem duplicatas),
        // independentemente de haver reprodução. O número exibido na sintonia é
        // exatamente este (nunca a amostra verificada, nunca apenas os reproduzíveis).
        mergedNumbered = mergedNumbered.distinctBy { it.channelId }
        val addedCount = mergedNumbered.size

        // Verificação LIMITADA e NÃO bloqueante: serve apenas para o indicador de
        // progresso da sintonia inicial e nunca remove canais da grade nem trava o setup.
        runBoundedVerification(mergedNumbered, streamsByChannel, addedCount, onProgress)

        // Etapa 13 §09: preserva favoritos e último canal por `channelId` (nunca por
        // número) ANTES de regravar a grade — a numeração pode mudar com o novo bloco.
        channelNumberDao.clear()
        channelNumberDao.insertAll(mergedNumbered)
        preserveUserState(previous, mergedNumbered)

        // Etapa 10 §04 / Etapa 13 §08: métricas auditáveis por fonte.
        logCatalogMetrics(uf, mergedNumbered, assembledReport)
        logTuningAudit(uf, channels, streams, mergedNumbered, assembledReport)
        onProgress("Sintonia concluída", addedCount, addedCount, addedCount, addedCount)
        mergedNumbered
    }

    /**
     * Auditoria da contagem da sintonia (Etapa 13 §08). Loga, em DEBUG, os itens
     * A–I exigidos, de forma somável e reproduzível:
     *  A) registros retornados pela API Rei dos Canais;
     *  B) registros retornados pelo iptv-org;
     *  D) descartados por duplicação (antes/depois do distinctBy);
     *  E) excluídos legitimamente (temáticos 24h);
     *  F) canais adicionados à grade (= G, persistidos);
     *  H) canais com fontes de reprodução (streamCount > 0);
     *  I) canais com reprodução confirmada = amostra verificada (NÃO é o total).
     */
    private fun logTuningAudit(
        uf: String,
        iptvChannels: List<ApiChannelDto>,
        iptvStreams: List<ApiStreamDto>,
        entities: List<ChannelNumberEntity>,
        report: AssembledCatalog?
    ) {
        if (!BuildConfig.DEBUG) return
        val playableStreams = iptvStreams.filter { CatalogFilters.isPlayableStreamUrl(it.url) }
        val withSource = entities.count { it.streamCount > 0 }
        val rdcGeral = entities.count { it.channelId.startsWith("rdc:") }
        Log.d(
            "TuningAudit",
            "uf=$uf A_apiRDC=${report?.rdcDiscovered ?: 0} B_iptvOrg=${iptvChannels.size} " +
                "B_streams=${playableStreams.size} F_adicionados=${entities.size} H_comFonte=$withSource " +
                "RDC_representados=${report?.rdcRepresented ?: 0} RDC_somenteCatalogo=${report?.rdcCatalogOnly ?: 0} " +
                "RDC_geral=$rdcGeral"
        )
    }

    /**
     * Preserva o estado do usuário diante de renumeração (Etapa 13 §09). Favoritos e
     * último canal são chaveados por NÚMERO no schema atual (imutável sem migração):
     * traduzimos o número antigo → `channelId` → número novo, garantindo que o mesmo
     * canal continue favorito/último mesmo que sua posição mude.
     */
    private suspend fun preserveUserState(
        previous: List<ChannelNumberEntity>,
        next: List<ChannelNumberEntity>
    ) {
        if (previous.isEmpty()) return
        val oldChannelByNumber = previous.associate { it.number to it.channelId }
        val newNumberByChannel = next.associate { it.channelId to it.number }

        val removals = ArrayList<Int>()
        val additions = ArrayList<FavoriteEntity>()
        for (f in favoriteDao.getAll()) {
            val cid = oldChannelByNumber[f.channelNumber] ?: continue
            val newNum = newNumberByChannel[cid] ?: continue
            if (newNum != f.channelNumber) {
                removals += f.channelNumber
                additions += FavoriteEntity(newNum, f.addedAt)
            }
        }
        removals.forEach { favoriteDao.remove(it) }
        additions.forEach { favoriteDao.add(it) }

        lastChannelDao.get()?.let { lc ->
            val newNum = oldChannelByNumber[lc.channelNumber]?.let { newNumberByChannel[it] }
            if (newNum != null && newNum != lc.channelNumber) {
                lastChannelDao.save(LastChannelEntity(channelNumber = newNum))
            }
        }
    }

    private fun logCatalogMetrics(uf: String, entities: List<ChannelNumberEntity>, report: AssembledCatalog?) {
        if (!BuildConfig.DEBUG) return
        val fixed = entities.count { it.number in 1..9 }
        val adult = entities.count { it.isNsfw }
        Log.d(
            "CatalogSync",
            "uf=$uf total=${entities.size} fixos=$fixed adulto=$adult " +
                "rdcDescobertos=${report?.rdcDiscovered ?: 0} " +
                "rdcRepresentados=${report?.rdcRepresented ?: 0} " +
                "rdcSomenteCatalogo=${report?.rdcCatalogOnly ?: 0} " +
                "aprovados=${report?.approvedIds?.size ?: 0}"
        )
    }

    /**
     * Verificação de sinal limitada aos canais relevantes para o indicador inicial
     * (fixos 1-9 + primeiros canais brasileiros), com timeout global. Preserva a
     * grade mesmo quando a fonte está indisponível e nunca bloqueia indefinidamente.
     */
    private suspend fun runBoundedVerification(
        numbered: List<ChannelNumberEntity>,
        streamsByChannel: Map<String?, List<ApiStreamDto>>,
        added: Int,
        onProgress: (currentName: String, checked: Int, total: Int, alive: Int, added: Int) -> Unit
    ) {
        val fixed = numbered.filter { it.number in 1..9 }
        val brHead = numbered.filter { it.number > 9 && !it.isNsfw }.take(VERIFY_MAX_CHANNELS)
        val subset = (fixed + brHead).distinctBy { it.number }.mapNotNull { entity ->
            val s = streamsByChannel[entity.channelId]?.filter { CatalogFilters.isPlayableStreamUrl(it.url) }
            if (s.isNullOrEmpty()) null else entity to s
        }
        if (subset.isEmpty()) {
            onProgress("Catálogo pronto", 1, 1, 0, added)
            return
        }
        try {
            withTimeoutOrNull(VERIFY_TIMEOUT_MS) {
                StreamHealthChecker.filterAlive(
                    subset,
                    maxConcurrent = 12,
                    onProgress = { name, checked, total, alive ->
                        // O total de canais adicionados NÃO depende do êxito da verificação
                        // (Etapa 13 §08): `added` é sempre o tamanho real da grade.
                        onProgress(name, checked, total, alive, added)
                    }
                )
            }
        } catch (_: Exception) {
            // Verificação é apenas informativa; qualquer falha é ignorada.
        }
    }

    /** Aplica metadados da Fonte A (logotipo/categoria) sem nunca derrubar o fluxo. */
    private suspend fun enrichWithRdc(entities: List<ChannelNumberEntity>): List<ChannelNumberEntity> =
        try {
            rdcRepository.enrichEntities(entities)
        } catch (_: Exception) {
            entities
        }

    private suspend fun loadM3uCatalog(): M3uCatalog? = withContext(Dispatchers.IO) {
        if (m3uCatalog != null) return@withContext m3uCatalog
        try {
            val assetManager: AssetManager = ctx.assets
            val inputStream = assetManager.open("m3u_catalog.json")
            val gson = Gson()
            val json = inputStream.bufferedReader().readText()
            val catalog = gson.fromJson(json, M3uCatalog::class.java)

            // Mecanismo reversível (Etapa 07 §02): com as listas brasileiras antigas
            // desativadas, apenas o catálogo ADULTO é preservado (recurso do ZAP-TV,
            // protegido por PIN). As seções BR/PT/US obsoletas são descartadas.
            // O asset original permanece no repositório/APK para recuperação total.
            val active = if (CatalogFilters.LEGACY_BR_CATALOG_ENABLED) catalog
            else catalog.copy(brChannels = emptyList(), ptChannels = emptyList(), usChannels = emptyList())

            // Fonte A (Rei dos Canais): enriquece metadados (logotipo/categoria).
            // Falha da API => catálogo original preservado integralmente.
            val enriched = try {
                rdcRepository.enrichCatalog(active)
            } catch (_: Exception) {
                active
            }
            m3uCatalog = enriched
            enriched
        } catch (e: Exception) {
            println("Erro ao carregar catálogo M3U: ${e.message}")
            null
        }
    }

    private fun mergeChannelsWithM3u(
        channels: List<ApiChannelDto>,
        streams: List<ApiStreamDto>,
        logos: List<ApiLogoDto>,
        uf: String,
        m3uCatalog: M3uCatalog?
    ): List<ChannelNumberEntity> {
        // Nunca compor com URLs que não sejam streams reproduzíveis (embeds web/etc.).
        val playableStreams = streams.filter { CatalogFilters.isPlayableStreamUrl(it.url) }

        // Primeiro, gera canais base do iptv-org
        val baseNumbered = ChannelNumbering.buildNumberedChannels(channels, playableStreams, logos, uf)
        
        if (m3uCatalog == null) return baseNumbered
        
        // Mapa de canais iptv-org por channelId para merge rápido
        val iptvMap = baseNumbered.associateBy { it.channelId }
        
        // Mapa de streams iptv-org por channelId
        val iptvStreamsMap = playableStreams.groupBy { it.channel }
        
        // Merge M3U BR channels
        val mergedEntities = mutableListOf<ChannelNumberEntity>()
        val processedChannelIds = mutableSetOf<String>()
        
        // 1. Processa canais BR do M3U - merge com iptv-org ou adiciona novos
        for (m3uChannel in m3uCatalog.brChannels) {
            // Etapa 07 §03: temáticos 24h nunca entram no catálogo ativo.
            if (CatalogFilters.isTematic24h(m3uChannel.channelId, m3uChannel.displayName, m3uChannel.categories)) continue
            val iptvEntity = iptvMap[m3uChannel.channelId]
            val iptvStreams = iptvStreamsMap[m3uChannel.channelId] ?: emptyList()
            val m3uStreams = m3uChannel.streams.map { toApiStream(it, m3uChannel.channelId) }
                .filter { CatalogFilters.isPlayableStreamUrl(it.url) }
            
            val allStreams = iptvStreams + m3uStreams
            
            val entity = if (iptvEntity != null) {
                // Merge: usa dados do iptv-org + streams M3U como fallbacks
                processedChannelIds.add(m3uChannel.channelId)
                iptvEntity.copy(
                    streamCount = allStreams.size,
                    // Mantém número original do iptv-org
                )
            } else {
                // Canal novo do M3U - será numerado depois
                // Detecta se é filial regional para que o filtro de UF funcione corretamente
                ChannelNumberEntity(
                    number = -1, // Será renumerado
                    channelId = m3uChannel.channelId,
                    channelName = m3uChannel.displayName,
                    country = m3uChannel.country,
                    categories = m3uChannel.categories.joinToString(","),
                    logoUrl = m3uChannel.logoUrl,
                    isNsfw = m3uChannel.isNsfw,
                    streamCount = allStreams.size,
                    affiliateState = com.zap.tv.util.ChannelNumbering.getAffiliateState(
                        m3uChannel.channelId, m3uChannel.displayName
                    )
                )
            }
            mergedEntities.add(entity)
            processedChannelIds.add(m3uChannel.channelId)
        }
        
        // 2. Adiciona canais iptv-org que não estão no M3U
        for (entity in baseNumbered) {
            if (entity.channelId !in processedChannelIds) {
                mergedEntities.add(entity)
            }
        }
        
        // 3. Adiciona canais PT/US do M3U (novos canais)
        for (m3uChannel in m3uCatalog.ptChannels + m3uCatalog.usChannels) {
            if (m3uChannel.channelId !in processedChannelIds) {
                val m3uStreams = m3uChannel.streams.map { toApiStream(it,m3uChannel.channelId) }
                mergedEntities.add(ChannelNumberEntity(
                    number = -1,
                    channelId = m3uChannel.channelId,
                    channelName = m3uChannel.displayName,
                    country = m3uChannel.country,
                    categories = m3uChannel.categories.joinToString(","),
                    logoUrl = m3uChannel.logoUrl,
                    isNsfw = m3uChannel.isNsfw,
                    streamCount = m3uChannel.streams.size,
                    affiliateState = null
                ))
                processedChannelIds.add(m3uChannel.channelId)
            }
        }
        
        // 4. Adiciona canais adulto do M3U
        for (m3uChannel in m3uCatalog.adultChannels) {
            if (m3uChannel.channelId !in processedChannelIds) {
                val m3uStreams = m3uChannel.streams.map { toApiStream(it,m3uChannel.channelId) }
                mergedEntities.add(ChannelNumberEntity(
                    number = -1,
                    channelId = m3uChannel.channelId,
                    channelName = m3uChannel.displayName,
                    country = m3uChannel.country,
                    categories = m3uChannel.categories.joinToString(","),
                    logoUrl = m3uChannel.logoUrl,
                    isNsfw = true,
                    streamCount = m3uChannel.streams.size,
                    affiliateState = null
                ))
                processedChannelIds.add(m3uChannel.channelId)
            }
        }
        
        // 5. Resolve os 9 canais fixos diretamente sobre a lista unificada M3U + iptv-org
        // garantindo que a filial regional do estado do usuário (ex: Globo CE, SBT CE, etc.)
        // ocupe prioritariamente a posição 1, 2, 3, 6.
        val (fixedResolved, assignedFixedChannelIds) = com.zap.tv.util.ChannelNumbering.resolveFixedEntities(mergedEntities, uf)

        val remainingEntities = mergedEntities.filter { it.channelId !in assignedFixedChannelIds }

        // Agrupa os canais restantes por tipo
        val remainingBr = remainingEntities.filter { it.country == "BR" && !it.isNsfw }.sortedBy { it.channelName.lowercase() }
        val remainingPtUs = remainingEntities.filter { it.country != "BR" && !it.isNsfw }.sortedBy { it.channelName.lowercase() }
        val remainingAdult = remainingEntities.filter { it.isNsfw }.sortedBy { it.channelName.lowercase() }

        val finalNumbered = mutableListOf<ChannelNumberEntity>()
        // 1-9 fixos não recebem nova numeração e sempre são mantidos
        finalNumbered.addAll(fixedResolved)

        val allToNumber = mutableListOf<ChannelNumberEntity>()
        allToNumber.addAll(remainingBr)
        allToNumber.addAll(remainingPtUs)
        allToNumber.addAll(remainingAdult)

        // Filtra os canais ANTES de numerar para evitar buracos (gaps) na numeração
        val filteredToNumber = if (uf != "NACIONAL") {
            allToNumber.filter { entity ->
                entity.affiliateState == null || entity.affiliateState == uf
            }
        } else {
            allToNumber
        }

        var nextNumber = 10
        for (entity in filteredToNumber) {
            if (nextNumber > 20000) break
            finalNumbered.add(entity.copy(number = nextNumber))
            nextNumber++
        }

        return finalNumbered.sortedBy { it.number }
    }

    /**
     * Catálogo ativo com a Fonte A (Rei dos Canais) como origem PRINCIPAL brasileira
     * (Etapa 08). Regras:
     *  - Todos os canais da API entram no catálogo, de forma independente.
     *  - A reprodução vem do iptv-org SOMENTE quando há fonte autorizada da MESMA
     *    emissora (cabeça de rede verificada ou nome normalizado idêntico no BR).
     *  - Canais sem fonte reproduzível permanecem como registro de catálogo
     *    (`isAvailable = false`), nunca entregando páginas de embed ao ExoPlayer.
     *  - Posições 1-9 preservadas (8 = TV Centro-Sul, 9 = TV Litoral RN).
     *  - Sem duplicação: um mesmo canal do iptv-org não é numerado duas vezes.
     *  - Canais internacionais do iptv-org e o catálogo adulto são preservados.
     */
    private fun assembleRdcPrimaryCatalog(
        channels: List<ApiChannelDto>,
        streams: List<ApiStreamDto>,
        logos: List<ApiLogoDto>,
        uf: String,
        m3uCatalog: M3uCatalog?,
        rdcChannels: List<RdcChannelDto>,
        baseApprovedIds: Set<String>
    ): AssembledCatalog {
        // Nunca compor com URLs que não sejam streams reproduzíveis.
        val playableStreams = streams.filter { CatalogFilters.isPlayableStreamUrl(it.url) }
        val playableIds = playableStreams.mapTo(HashSet()) { it.channel }
        val iptvIds = channels.mapTo(HashSet()) { it.id }

        // Índice BR por nome normalizado (mesma emissora, mesmo país). Marcas iguais
        // de outros países (ex.: Globo.pt, TNT.ru) ficam de fora: são outro feed.
        val iptvBrNameIndex = channels
            .filter { it.country.equals("BR", ignoreCase = true) }
            .mapNotNull { ch ->
                RdcCatalogMapper.normalize(ch.name).takeIf { it.isNotEmpty() }?.let { it to ch.id }
            }
            .toMap()

        val catalogOnlyIds = linkedSetOf<String>()
        val rdcApiChannels = mutableListOf<ApiChannelDto>()
        val metadataById = mutableMapOf<String, RdcChannelDto>()

        for (rdc in rdcChannels) {
            val pid = RdcCatalogMapper.playbackIdFor(rdc.id, rdc.name, iptvBrNameIndex)
            if (pid != null && pid in playableIds) {
                // Já reproduzível via iptv-org: apenas enriquece metadados.
                metadataById[pid] = rdc
                continue
            }
            val id = pid ?: "rdc:${rdc.id}"
            if (id in playableIds) continue
            if (!catalogOnlyIds.add(id)) continue
            metadataById[id] = rdc
            if (id !in iptvIds) {
                rdcApiChannels.add(
                    ApiChannelDto(
                        id = id,
                        name = rdc.name,
                        country = "BR",
                        categories = listOf(ReiDosCanaisMapper.mapCategory(rdc.category) ?: "general"),
                        is_nsfw = false,
                        owners = emptyList()
                    )
                )
            }
        }

        // Etapa 18: ledger final de identidades aprovadas = pertencimento prévio
        // (ledger/semente/grade local) + TODAS as identidades da Fonte A representadas
        // (metadados e catalog-only). Nenhum registro bruto do iptv-org é importado.
        val approvedIds = LinkedHashSet<String>(baseApprovedIds)
        approvedIds.addAll(metadataById.keys)

        var numbered = ChannelNumbering.buildNumberedChannels(
            channels = channels + rdcApiChannels,
            streams = playableStreams,
            logos = logos,
            selectedUf = uf,
            catalogOnlyIds = catalogOnlyIds,
            approvedIds = approvedIds
        )

        numbered = applyRdcMetadata(numbered, metadataById)

        // Etapa 10 §05: ordem definitiva — fixos (1-9) -> Fonte A (RDC) -> iptv-org.
        numbered = ChannelNumbering.orderGrade(numbered, metadataById.keys)

        if (m3uCatalog != null) {
            numbered = appendAdultChannels(numbered, m3uCatalog.adultChannels)
        }

        return AssembledCatalog(
            entities = numbered.sortedBy { it.number },
            rdcDiscovered = rdcChannels.size,
            rdcRepresented = metadataById.size,
            rdcCatalogOnly = catalogOnlyIds.size,
            approvedIds = approvedIds
        )
    }

    /** Catálogo montado + métricas por fonte (Etapa 10 §04) para diagnóstico. */
    private data class AssembledCatalog(
        val entities: List<ChannelNumberEntity>,
        val rdcDiscovered: Int,
        val rdcRepresented: Int,
        val rdcCatalogOnly: Int,
        val approvedIds: Set<String> = emptySet()
    )

    /** Aplica logotipo/categoria da Fonte A (sem substituir dados melhores já existentes). */
    private fun applyRdcMetadata(
        entities: List<ChannelNumberEntity>,
        metadataById: Map<String, RdcChannelDto>
    ): List<ChannelNumberEntity> = entities.map { entity ->
        val meta = metadataById[entity.channelId] ?: return@map entity
        val cats = entity.categories.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val newCats = ReiDosCanaisMapper.refineCategories(cats, meta.category)
        entity.copy(
            logoUrl = entity.logoUrl ?: meta.logo_url,
            categories = newCats.joinToString(",")
        )
    }

    /** Acrescenta o catálogo adulto preservado (recurso do app), sem duplicar. */
    private fun appendAdultChannels(
        base: List<ChannelNumberEntity>,
        adultGroups: List<M3uChannelGroup>
    ): List<ChannelNumberEntity> {
        val existingIds = base.mapTo(HashSet()) { it.channelId }
        var next = (base.maxOfOrNull { it.number } ?: 9) + 1
        val added = mutableListOf<ChannelNumberEntity>()
        for (group in adultGroups.sortedBy { it.displayName.lowercase() }) {
            if (group.channelId in existingIds) continue
            if (CatalogFilters.isTematic24h(group.channelId, group.displayName, group.categories)) continue
            val streams = group.streams.map { toApiStream(it, group.channelId) }
                .filter { CatalogFilters.isPlayableStreamUrl(it.url) }
            if (streams.isEmpty()) continue
            if (next > 20000) break
            added.add(
                ChannelNumberEntity(
                    number = next,
                    channelId = group.channelId,
                    channelName = group.displayName,
                    country = group.country,
                    categories = group.categories.joinToString(","),
                    logoUrl = group.logoUrl,
                    isNsfw = true,
                    streamCount = streams.size,
                    affiliateState = null,
                    isAvailable = true
                )
            )
            next++
        }
        return base + added
    }

    suspend fun getNumberedChannels(): List<ChannelNumberEntity> = channelNumberDao.getAll()
    suspend fun getAvailableChannels(): List<ChannelNumberEntity> = channelNumberDao.getAllAvailable()
    suspend fun getChannelByNumber(n: Int): ChannelNumberEntity? = channelNumberDao.getByNumber(n)
    suspend fun count(): Int = channelNumberDao.count()
    suspend fun countAvailable(): Int = channelNumberDao.countAvailable()

    suspend fun getNext(current: Int): ChannelNumberEntity? =
        ChannelNumbering.nextChannel(channelNumberDao.getAll(), current)

    suspend fun getPrev(current: Int): ChannelNumberEntity? =
        ChannelNumbering.prevChannel(channelNumberDao.getAll(), current)

    suspend fun saveLastChannel(n: Int) = lastChannelDao.save(LastChannelEntity(channelNumber = n))
    suspend fun getLastChannelNumber(): Int? = lastChannelDao.get()?.channelNumber ?: channelNumberDao.getAll().firstOrNull()?.number

    suspend fun getStreamsForChannel(channelId: String): List<ApiStreamDto> = withContext(Dispatchers.IO) {
        val apiStreams = getStreamsCached().filter { it.channel == channelId }

        // Também busca no M3U catalog (apenas quando o legado está ativo / reversível)
        var m3uStreams = emptyList<ApiStreamDto>()
        val catalog = loadM3uCatalog()
        if (catalog != null) {
            for (channel in catalog.brChannels + catalog.ptChannels + catalog.usChannels + catalog.adultChannels) {
                if (channel.channelId == channelId) {
                    m3uStreams = channel.streams.map { toApiStream(it, channelId) }
                    break
                }
            }
        }

        val customStream = customStreams[channelId]
        val allStreams = mutableListOf<ApiStreamDto>()
        allStreams.addAll(apiStreams)
        allStreams.addAll(m3uStreams)
        if (customStream != null) allStreams.add(customStream)

        // Somente fontes reproduzíveis autorizadas; nunca entregar páginas de embed.
        // Etapa 14 §05: prioriza estabilidade (HTTPS/domínio) antes da qualidade,
        // sem alterar a emissora — corrige Band/Gazeta que abriam por IP/HTTP instável.
        StreamPreference.order(
            allStreams.filter { CatalogFilters.isPlayableStreamUrl(it.url) }
                .distinctBy { it.url }
        )
    }
    
    suspend fun getSelectedUf(): String = stateConfigDao.get()?.uf ?: "NACIONAL"

    /**
     * Atualiza a UF/região do usuário e reconstrói a grade regionalizada reutilizando
     * os mecanismos existentes (persiste em state_config + re-sync). Não reinicia o setup,
     * não altera PIN, favoritos nem último canal. Idempotente quando a UF não muda.
     *
     * Etapa 05: habilita a troca de região posterior sem recriar o assistente. A UI de
     * seleção pós-setup não faz parte desta etapa.
     */
    suspend fun updateRegion(uf: String): List<ChannelNumberEntity> = withContext(Dispatchers.IO) {
        if (getSelectedUf() == uf) return@withContext getNumberedChannels()
        stateConfigDao.save(StateConfigEntity(uf = uf))
        syncChannelsWithVerification(uf)
    }
}