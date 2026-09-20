package com.zap.tv.data.api

import com.zap.tv.data.model.Stream

data class M3uEntry(
    val tvgId: String,
    val title: String,
    val url: String,
    val headers: Map<String,String> = emptyMap()
)

object M3uParser {
    fun parse(content: String): List<M3uEntry> {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<M3uEntry>()
        var pendingId = ""
        var pendingTitle = ""
        var pendingHeaders = mutableMapOf<String,String>()
        for (line in lines) {
            when {
                line.startsWith("#EXTM3U") -> {}
                line.startsWith("#EXTVLCOPT:") -> {
                    val kv = line.removePrefix("#EXTVLCOPT:").split("=", limit=2)
                    if (kv.size==2) pendingHeaders[kv[0].trim().removePrefix("http-")] = kv[1].trim()
                }
                line.startsWith("#EXTINF:") -> {
                    // #EXTINF:-1 tvg-id="Globo.br@SD" tvg-name="Globo" ... ,Globo (1080p)
                    val id = Regex("tvg-id=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
                    val comma = line.lastIndexOf(',')
                    val title = if (comma>=0) line.substring(comma+1).trim() else ""
                    pendingId = id
                    pendingTitle = title
                }
                line.startsWith("http") -> {
                    out.add(M3uEntry(pendingId, pendingTitle.ifEmpty { pendingId }, line, pendingHeaders.toMap()))
                    pendingId=""; pendingTitle=""; pendingHeaders = mutableMapOf()
                }
            }
        }
        return out
    }

    fun toStreams(entries: List<M3uEntry>): List<Stream> = entries.map {
        Stream(it.url, it.title, null, it.headers["referrer"], it.headers["user-agent"], null)
    }
}
