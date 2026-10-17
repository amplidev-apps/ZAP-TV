package com.zap.tv.util

/**
 * Decisão de fallback após uma FALHA DE VÍDEO comprovada (Etapa 15 §03/§04).
 *
 * Áudio sem imagem NÃO é reprodução audiovisual validada. Quando o player direto
 * confirma que a trilha de vídeo foi declarada mas nenhum quadro renderizou dentro
 * da janela de tolerância, a política decide se há uma próxima fonte autorizada a
 * tentar ou se o canal deve permanecer acessível no estado "sem imagem".
 *
 * Regras:
 *  - Nunca repete a mesma fonte (avança somente para índice estritamente maior) e
 *    nunca ultrapassa a lista, impedindo alternâncias infinitas.
 *  - Esgotado o plano, o canal NÃO é removido nem declarado "sem sinal": permanece
 *    acessível com o estado [PlaybackState.AUDIO_ONLY] (há sinal, falta imagem).
 */
sealed interface VideoFallbackDecision {
    /** Há outra fonte autorizada: avançar para [index]. */
    data class Next(val index: Int) : VideoFallbackDecision

    /** Nenhuma fonte com vídeo: manter o canal com áudio (sem loop, sem "sem sinal"). */
    data object KeepAudioOnly : VideoFallbackDecision

    /** Não aplicável (índice inválido/plano vazio): nenhuma mudança de estado. */
    data object None : VideoFallbackDecision
}

object VideoFallbackPolicy {

    /**
     * @param attemptIndex índice da tentativa atual (a que falhou no vídeo).
     * @param attempts     plano completo e ordenado de tentativas.
     */
    fun decide(attemptIndex: Int, attempts: List<PlaybackAttempt>): VideoFallbackDecision {
        if (attempts.isEmpty() || attemptIndex !in attempts.indices) return VideoFallbackDecision.None
        val next = attemptIndex + 1
        return if (next < attempts.size) {
            VideoFallbackDecision.Next(next)
        } else {
            VideoFallbackDecision.KeepAudioOnly
        }
    }
}
