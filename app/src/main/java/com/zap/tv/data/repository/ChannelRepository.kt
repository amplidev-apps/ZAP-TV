package com.zap.tv.data.repository

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.Gson
import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiLogoDto
import com.zap.tv.data.api.ApiStreamDto
import com.zap.tv.data.api.IptvApiService
import com.zap.tv.data.local.dao.*
import com.zap.tv.data.local.entity.ChannelNumberEntity
import com.zap.tv.data.local.entity.LastChannelEntity
import com.zap.tv.data.local.entity.StateConfigEntity
import com.zap.tv.data.m3u.M3uCatalog
import com.zap.tv.data.m3u.M3uChannelGroup
import com.zap.tv.data.m3u.M3uStreamInfo
import com.zap.tv.data.m3u.toApiStream
import com.zap.tv.util.ChannelNumbering
import com.zap.tv.util.ParentalUtils
import com.zap.tv.util.StreamHealthChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val api: IptvApiService,
    private val channelNumberDao: ChannelNumberDao,
    private val stateConfigDao: StateConfigDao,
    private val lastChannelDao: LastChannelDao,
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

    suspend fun isSetupComplete(): Boolean = appFlagDao.get("setup_complete") == "1"

    suspend fun completeSetup(uf: String, pin: String?) = withContext(Dispatchers.IO) {
        stateConfigDao.save(StateConfigEntity(uf = uf))
        val hash = pin?.takeIf { it.length==4 }?.let { ParentalUtils.hash(it) }
        parentalDao.save(com.zap.tv.data.local.entity.ParentalPinEntity(pinHash = hash))
        appFlagDao.set(com.zap.tv.data.local.entity.AppFlagEntity("setup_complete","1"))
        syncChannelsWithM3u(uf)
    }

    suspend fun completeSetupWithVerification(uf: String, pin: String?, onProgress: (currentName: String, checked: Int, total: Int, alive: Int) -> Unit = {_,_,_,_ ->}) = withContext(Dispatchers.IO) {
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

    suspend fun syncChannelsWithVerification(uf: String, onProgress: (currentName: String, checked: Int, total: Int, alive: Int) -> Unit = {_,_,_,_ ->}): List<ChannelNumberEntity> = withContext(Dispatchers.IO) {

        val channels = api.getChannels()
        val streams = api.getStreams()
        val logos = api.getLogos()
        val m3uCatalog = loadM3uCatalog()

        val streamsByChannel = streams.groupBy { it.channel }.toMutableMap()

        if (m3uCatalog != null) {
            for (channel in m3uCatalog.brChannels + m3uCatalog.ptChannels + m3uCatalog.usChannels + m3uCatalog.adultChannels) {
                val m3uStreams = channel.streams.map { toApiStream(it, channel.channelId) }
                val existing = streamsByChannel[channel.channelId] ?: emptyList()
                streamsByChannel[channel.channelId] = existing + m3uStreams
            }
        }

        customStreams.forEach { (channelId, stream) ->
            val existing = streamsByChannel[channelId] ?: emptyList()
            streamsByChannel[channelId] = existing + stream
        }

        // Get all channels including M3U merged
        val mergedNumbered = mergeChannelsWithM3u(channels, streams, logos, uf, m3uCatalog)
        
        // Prepare pairs of (Channel, List<Stream>) for concurrent checking
        val channelsToVerify = mergedNumbered.map { entity ->
            val channelStreams = streamsByChannel[entity.channelId] ?: emptyList()
            entity to channelStreams
        }
        
        // Use StreamHealthChecker — apenas para progresso/indicador, mas mantém todos (m3u tjtor/up.kiwi instável, não pode esvaziar grade CanaisBR01/02)
        try { StreamHealthChecker.filterAlive(channelsToVerify, maxConcurrent = 15, onProgress = onProgress) } catch (_: Exception) {}
        val finalNumbered = mergedNumbered
        
        channelNumberDao.clear()
        channelNumberDao.insertAll(finalNumbered)
        finalNumbered
    }

    private suspend fun loadM3uCatalog(): M3uCatalog? = withContext(Dispatchers.IO) {
        if (m3uCatalog != null) return@withContext m3uCatalog
        try {
            val assetManager: AssetManager = ctx.assets
            val inputStream = assetManager.open("m3u_catalog.json")
            val gson = Gson()
            val json = inputStream.bufferedReader().readText()
            val catalog = gson.fromJson(json, M3uCatalog::class.java)
            m3uCatalog = catalog
            catalog
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
        // Primeiro, gera canais base do iptv-org
        val baseNumbered = ChannelNumbering.buildNumberedChannels(channels, streams, logos, uf)
        
        if (m3uCatalog == null) return baseNumbered
        
        // Mapa de canais iptv-org por channelId para merge rápido
        val iptvMap = baseNumbered.associateBy { it.channelId }
        
        // Mapa de streams iptv-org por channelId
        val iptvStreamsMap = streams.groupBy { it.channel }
        
        // Merge M3U BR channels
        val mergedEntities = mutableListOf<ChannelNumberEntity>()
        val processedChannelIds = mutableSetOf<String>()
        
        // 1. Processa canais BR do M3U - merge com iptv-org ou adiciona novos
        for (m3uChannel in m3uCatalog.brChannels) {
            val iptvEntity = iptvMap[m3uChannel.channelId]
            val iptvStreams = iptvStreamsMap[m3uChannel.channelId] ?: emptyList()
            val m3uStreams = m3uChannel.streams.map { toApiStream(it,m3uChannel.channelId) }
            
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

    suspend fun getNumberedChannels(): List<ChannelNumberEntity> = channelNumberDao.getAll()
    suspend fun getAvailableChannels(): List<ChannelNumberEntity> = channelNumberDao.getAllAvailable()
    suspend fun getChannelByNumber(n: Int): ChannelNumberEntity? = channelNumberDao.getByNumber(n)
    suspend fun count(): Int = channelNumberDao.count()
    suspend fun countAvailable(): Int = channelNumberDao.countAvailable()

    suspend fun getNext(current: Int): ChannelNumberEntity? {
        val all = channelNumberDao.getAllAvailable().ifEmpty { channelNumberDao.getAll() }
        if (all.isEmpty()) return null
        val idx = all.indexOfFirst { it.number == current }
        return if (idx==-1) all.first() else all[(idx+1) % all.size]
    }
    suspend fun getPrev(current: Int): ChannelNumberEntity? {
        val all = channelNumberDao.getAllAvailable().ifEmpty { channelNumberDao.getAll() }
        if (all.isEmpty()) return null
        val idx = all.indexOfFirst { it.number == current }
        return if (idx==-1) all.last() else all[(idx-1 + all.size) % all.size]
    }

    suspend fun saveLastChannel(n: Int) = lastChannelDao.save(LastChannelEntity(channelNumber = n))
    suspend fun getLastChannelNumber(): Int? = lastChannelDao.get()?.channelNumber ?: channelNumberDao.getAllAvailable().firstOrNull()?.number ?: channelNumberDao.getAll().firstOrNull()?.number

    suspend fun getStreamsForChannel(channelId: String): List<ApiStreamDto> = withContext(Dispatchers.IO) {
        val apiStreams = api.getStreams().filter { it.channel == channelId }
        val custom = customStreams[channelId]
        
        // Também busca no M3U catalog
        var m3uStreams = emptyList<ApiStreamDto>()
        val catalog = loadM3uCatalog()
        if (catalog != null) {
            for (channel in catalog.brChannels + catalog.ptChannels + catalog.usChannels + catalog.adultChannels) {
                if (channel.channelId == channelId) {
                    m3uStreams = channel.streams.map { toApiStream(it,channelId) }
                    break
                }
            }
        }
        
        val customStream = customStreams[channelId]
        val allStreams = mutableListOf<ApiStreamDto>()
        allStreams.addAll(apiStreams)
        allStreams.addAll(m3uStreams)
        if (customStream != null) allStreams.add(customStream)
        
        // Ordena: SD primeiro, depois HD, FHD, 4K
        allStreams.distinctBy { it.url }.sortedBy { stream ->
            when (stream.quality?.uppercase()) {
                "SD" -> 0
                "SD+" -> 1
                "HD" -> 2
                "HD+" -> 3
                "FHD" -> 3
                "FHD H265" -> 4
                "4K" -> 5
                else -> 2
            }
        }
}
    
    suspend fun getSelectedUf(): String = stateConfigDao.get()?.uf ?: "NACIONAL"
}