package com.zap.tv.data.m3u

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

object M3uParser {

    private val EXTINF_PATTERN = Pattern.compile("#EXTINF:([^,]+),(.+)")
    private val URL_PATTERN = Pattern.compile("^(https?://.+)$")
    
    // Keywords para detectar conteúdo adulto
    private val ADULT_KEYWORDS = setOf("XXX", "PORN", "SEXO", "ADULT", "EROTIC", "HOT", "PLAYBOY", "SEXY", "PRIVE", "XXX")

    // Keywords para remover sufixos de qualidade
    private val QUALITY_SUFFIXES = listOf(
        " 4K", " UHD", " FHD H265", " FHD", " HD²", " HD³", " HD", " SD²", " SD³", " SD", " H265", " ALT", " ALT 01", " ALT 02"
    )

    data class ParsedEntry(
        val name: String,
        val url: String,
        val rawExtinf: String
    )

    // Parse streaming - não carrega tudo na memória
    suspend fun parse(inputStream: InputStream): List<ParsedEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ParsedEntry>()
        var currentName: String? = null
        var currentExtinf: String? = null
        
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    val matcher = EXTINF_PATTERN.matcher(trimmed)
                    if (matcher.matches()) {
                        currentExtinf = matcher.group(1)
                        currentName = matcher.group(2).trim()
                    }
                } else if (trimmed.startsWith("http") && currentName != null) {
                    val urlMatcher = URL_PATTERN.matcher(trimmed)
                    if (urlMatcher.matches()) {
                        entries.add(ParsedEntry(currentName!!, trimmed, currentExtinf!!))
                        currentName = null
                        currentExtinf = null
                    }
                }
            }
        }
        entries
    }

    // Detecta se é canal adulto
    fun isAdultChannel(name: String): Boolean {
        val upper = name.uppercase()
        return ADULT_KEYWORDS.any { upper.contains(it) }
    }

    // Remove sufixos de qualidade do nome
    fun normalizeName(name: String): String {
        var normalized = name
        for (suffix in QUALITY_SUFFIXES) {
            normalized = normalized.replace(suffix, "", ignoreCase = true)
        }
        return normalized.trim()
    }

    // Extrai país do prefixo [PT], [US], etc.
    fun extractCountry(name: String): String {
        return when {
            name.startsWith("[PT]") -> "PT"
            name.startsWith("[US]") -> "US"
            name.startsWith("[REC]") -> "BR" // VOD, ignorar
            else -> "BR"
        }
    }

    // Remove prefixo de país do nome
    fun removeCountryPrefix(name: String): String {
        return name
            .replace("[PT] ", "")
            .replace("[US] ", "")
            .replace("[REC] ", "")
            .trim()
    }

    // Determina qualidade baseada no nome
    fun extractQuality(name: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains("4K") || upper.contains("UHD") -> "4K"
            upper.contains("FHD H265") || upper.contains("FHD") -> "FHD"
            upper.contains("HD²") || upper.contains("HD³") -> "HD+"
            upper.contains("HD") -> "HD"
            upper.contains("SD²") || upper.contains("SD³") -> "SD+"
            upper.contains("SD") -> "SD"
            else -> "HD"
        }
    }
}