package com.zap.tv.util

import com.zap.tv.data.api.ApiStreamDto
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamHealthChecker {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Cache curto de resultados (Etapa 07 §04): evita verificações repetitivas entre
    // execuções próximas da sintonia. TTL curto para não fixar estado obsoleto.
    private const val CACHE_TTL_MS = 60_000L
    private val aliveCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Boolean>>()

    private fun cacheGet(url: String): Boolean? {
        val entry = aliveCache[url] ?: return null
        if (System.currentTimeMillis() - entry.first > CACHE_TTL_MS) {
            aliveCache.remove(url)
            return null
        }
        return entry.second
    }

    private fun cachePut(url: String, alive: Boolean) {
        if (aliveCache.size > 4096) aliveCache.clear()
        aliveCache[url] = System.currentTimeMillis() to alive
    }

    /**
     * Testa rapidamente uma URL de stream sem precisar de ApiStreamDto.
     * Usado durante o setup para verificar filiais regionais antes de confirmá-las.
     * Timeout de 5s: HEAD primeiro, depois GET parcial como fallback.
     */
    suspend fun testUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val headReq = Request.Builder().url(url).head()
                .header("User-Agent", "ZAP-TV/1.0")
                .build()
            client.newCall(headReq).execute().use { resp ->
                if (resp.code in 200..299) return@withContext true
            }
            // fallback: GET com range parcial
            val getReq = Request.Builder().url(url).get()
                .header("Range", "bytes=0-2047")
                .header("User-Agent", "ZAP-TV/1.0")
                .build()
            client.newCall(getReq).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (resp.code in 200..299 || resp.code == 206) return@withContext true
                if (body.contains("#EXTM3U") || body.contains("#EXT-X") || body.contains("#EXTINF")) return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isStreamAlive(dto: ApiStreamDto): Boolean = withContext(Dispatchers.IO) {
        cacheGet(dto.url)?.let { return@withContext it }
        val alive = probeStream(dto)
        cachePut(dto.url, alive)
        alive
    }

    private suspend fun probeStream(dto: ApiStreamDto): Boolean = withContext(Dispatchers.IO) {
        try {
            // HEAD first
            val headReq = Request.Builder()
                .url(dto.url)
                .head()
                .apply {
                    dto.referrer?.let { header("Referer", it) }
                    dto.user_agent?.let { header("User-Agent", it) }
                    header("User-Agent", dto.user_agent ?: "ZAP-TV/1.0")
                }
                .build()
            client.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val ct = resp.header("content-type")?.lowercase() ?: ""
                    // HLS often returns application/vnd.apple.mpegurl or text
                    if (resp.code in 200..299) return@withContext true
                }
            }
            // fallback GET small range
            val getReq = Request.Builder()
                .url(dto.url)
                .get()
                .header("Range", "bytes=0-2047")
                .apply {
                    dto.referrer?.let { header("Referer", it) }
                    dto.user_agent?.let { header("User-Agent", it) }
                }
                .build()
            client.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.contains("#EXTM3U") || body.contains("#EXT-X") || body.contains("#EXTINF")) return@withContext true
                    // some servers return binary but 206
                    if (resp.code == 206) return@withContext true
                    if (resp.code in 200..299) return@withContext true
                }
                // Geo-blocked often returns 403 but with label, we treat as alive (user may be geo-blocked but we keep)
                if (resp.code == 403 && dto.label?.contains("Geo", ignoreCase = true) == true) return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    // Check list of streams for one channel, return true if any alive
    suspend fun isChannelAlive(streams: List<ApiStreamDto>): Boolean {
        for (s in streams) {
            if (isStreamAlive(s)) return true
            // small delay between tries
            delay(100)
        }
        return false
    }

    suspend fun filterAlive(
        channels: List<Pair<com.zap.tv.data.local.entity.ChannelNumberEntity, List<ApiStreamDto>>>,
        maxConcurrent: Int = 12,
        onProgress: (currentName: String, checked: Int, total: Int, alive: Int) -> Unit = { _, _, _, _ -> }
    ): Set<Int> {
        val aliveNumbers = mutableSetOf<Int>()
        val sem = Semaphore(maxConcurrent)
        var checked = 0
        var alive = 0
        coroutineScope {
            val jobs = channels.map { (entity, streams) ->
                async {
                    sem.withPermit {
                        val ok = isChannelAlive(streams)
                        synchronized(aliveNumbers) {
                            checked++
                            if (ok) { aliveNumbers.add(entity.number); alive++ }
                            onProgress(entity.channelName, checked, channels.size, alive)
                        }
                        ok
                    }
                }
            }
            jobs.awaitAll()
        }
        return aliveNumbers
    }
}
