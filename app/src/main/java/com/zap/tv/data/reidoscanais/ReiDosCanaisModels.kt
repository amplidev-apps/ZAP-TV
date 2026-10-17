package com.zap.tv.data.reidoscanais

// Modelos internos da integração Rei dos Canais (independentes do formato da API).

data class EpgProgram(
    val title: String,
    val description: String? = null,
    val formattedTime: String? = null,
    val startTimeSec: Long? = null,
    val endTimeSec: Long? = null,
    val imageUrl: String? = null
)

data class ChannelEpg(
    val rdcChannelId: String,
    val current: EpgProgram? = null,
    val next: EpgProgram? = null
) {
    /**
     * Fração [0,1] de progresso do programa atual, calculada a partir dos
     * timestamps Unix (segundos). Retorna null quando não há janela temporal válida.
     */
    fun progressFraction(nowSec: Long = System.currentTimeMillis() / 1000L): Float? {
        val start = current?.startTimeSec ?: return null
        val end = current?.endTimeSec ?: return null
        if (end <= start) return null
        val f = (nowSec - start).toFloat() / (end - start).toFloat()
        return f.coerceIn(0f, 1f)
    }
}
