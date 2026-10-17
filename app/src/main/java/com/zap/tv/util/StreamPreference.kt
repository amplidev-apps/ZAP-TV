package com.zap.tv.util

import com.zap.tv.data.api.ApiStreamDto

/**
 * Ordenação de fontes de reprodução (Etapa 14 §05).
 *
 * Objetivo: escolher PRIMEIRO a fonte mais estável, sem inventar variantes nem trocar
 * de emissora. Critérios, em ordem:
 *  1. Estabilidade do endereço: HTTPS com nome de domínio > HTTPS com IP literal >
 *     HTTP com nome de domínio > HTTP com IP literal. IPs "crus" e HTTP são, na
 *     prática, os espelhos mais instáveis (ex.: Band e TV Gazeta no iptv-org).
 *  2. Qualidade declarada (mantém a política de início conservador: resolução menor
 *     primeiro), agora com reconhecimento de rótulos numéricos ("480p".."2160p").
 *  3. URL como desempate final, garantindo ordem 100% determinística.
 *
 * Nunca remove fontes nem reordena entre emissoras: atua apenas dentro da lista já
 * autorizada de um único canal.
 */
object StreamPreference {

    fun order(streams: List<ApiStreamDto>): List<ApiStreamDto> =
        streams.sortedWith(
            compareBy(
                { stabilityRank(it.url) },
                { qualityRank(it.quality) },
                { it.url }
            )
        )

    /** 0 = HTTPS+host, 1 = HTTPS+IP, 2 = HTTP+host, 3 = HTTP+IP. */
    fun stabilityRank(url: String?): Int {
        val u = url?.trim()?.lowercase() ?: return 4
        val https = u.startsWith("https://")
        val http = u.startsWith("http://")
        if (!https && !http) return 4
        val host = hostOf(u)
        val isIp = isBareIpv4(host)
        return when {
            https && !isIp -> 0
            https && isIp -> 1
            http && !isIp -> 2
            else -> 3
        }
    }

    /** Política de início conservador (menor resolução primeiro), com rótulos numéricos. */
    fun qualityRank(quality: String?): Int {
        val q = quality?.uppercase()?.trim() ?: return 2
        numericHeight(q)?.let { return when {
            it <= 0 -> 2
            it <= 480 -> 0
            it <= 576 -> 1
            it <= 720 -> 2
            it <= 1080 -> 3
            it <= 1440 -> 4
            else -> 5
        } }
        return when (q) {
            "SD" -> 0
            "SD+" -> 1
            "HD", "FHD" -> 3
            "FHD H265" -> 4
            "4K" -> 5
            else -> 2
        }
    }

    private fun numericHeight(q: String): Int? =
        Regex("(\\d{3,4})\\s*P").find(q)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun hostOf(url: String): String {
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore('?').substringAfter('@')
            .substringBefore(':')
    }

    private fun isBareIpv4(host: String): Boolean =
        Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)
}
