package com.zap.tv.util

/**
 * Perfis de qualidade de reprodução (Etapa 10 §10). Implementados na camada técnica;
 * a UI de seleção não faz parte desta etapa.
 *
 * [AUTO] é o padrão: adaptação nativa (inicialização conservadora + aumento
 * progressivo + redução em quedas), sem caps impostos.
 */
enum class PlaybackQualityProfile { AUTO, DATA_SAVER, BEST }

/**
 * Limites aplicados ao seletor de trilhas ADAPTATIVO nativo do Media3. `null` = sem
 * cap (deixa o ABR nativo decidir). Nunca inventa variantes nem transcodifica.
 */
data class VideoQualityCaps(
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val maxBitrate: Int? = null
)

object QualityPolicy {

    /**
     * Caps por perfil. Preserva os mecanismos adaptativos nativos do Media3:
     * apenas limita o teto — a escolha inicial conservadora e o upswitch continuam
     * por conta do ABR oficial.
     */
    fun capsFor(profile: PlaybackQualityProfile): VideoQualityCaps = when (profile) {
        // Padrão: sem caps — adaptação nativa completa.
        PlaybackQualityProfile.AUTO -> VideoQualityCaps()
        // Economia de dados: teto SD (~480p) e bitrate reduzido.
        PlaybackQualityProfile.DATA_SAVER -> VideoQualityCaps(maxWidth = 854, maxHeight = 480, maxBitrate = 800_000)
        // Melhor qualidade: teto Full HD, ainda com adaptação para evitar interrupções.
        PlaybackQualityProfile.BEST -> VideoQualityCaps(maxWidth = 1920, maxHeight = 1080, maxBitrate = 8_000_000)
    }
}
