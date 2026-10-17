package com.zap.tv.util

/**
 * Etapa 16 §06/§07 — decisões de sessão do motor web, puras e testáveis.
 *
 * Separa **falha de página** (documento/HTTP/renderizador) de um simples **timeout de
 * carregamento inicial**. A regra central corrige um defeito real identificado na
 * Etapa 16 §03: o guarda de tempo do documento disparava `onError` mesmo DEPOIS do
 * documento já ter carregado (`onPageFinished`), interrompendo a reprodução web alguns
 * segundos após o início ("reprodução interrompida após alguns segundos").
 *
 * Ou seja: [WebSessionEvent.LOAD_TIMEOUT] só é falha enquanto o documento NÃO carregou.
 * Depois de carregado, o guarda é irrelevante (o player incorporado tem o próprio ciclo).
 *
 * Nenhuma decisão aqui declara reprodução confirmada: carregar a página não é reprodução
 * audiovisual. A distinção "página carregada vs mídia confirmada" é feita em outros pontos
 * (o motor web não observa quadros de iframes de terceiros por restrição de same-origin).
 *
 * Etapa 19 §08 acrescenta o guarda de MÍDIA: o motor web pode observar requisições de
 * playlist/segmento feitas pela própria WebView (mesmo em iframe de outra origem, pois
 * passam pelo `WebViewClient`). A ausência TOTAL de mídia após o carregamento é tratada
 * como falha de FONTE — sem, contudo, declarar quadro renderizado.
 */
enum class WebSessionEvent {
    /** Novo documento iniciou (reinicia o estado de carregamento). */
    PAGE_STARTED,

    /** Documento principal terminou de carregar (NÃO é confirmação de mídia). */
    PAGE_FINISHED,

    /** Falha de carregamento do documento principal. */
    PAGE_ERROR,

    /** Processo de renderização do System WebView encerrou (falha real). */
    RENDERER_GONE,

    /** Guarda de tempo do carregamento inicial expirou. */
    LOAD_TIMEOUT,

    /**
     * Janela de tolerância de MÍDIA expirou após o documento carregar sem que
     * NENHUMA requisição de mídia (playlist/segmento) tenha sido observada
     * (Etapa 19 §08). É falha de FONTE, não de página.
     */
    MEDIA_TIMEOUT
}

sealed interface WebSessionDecision {
    /** Nada a fazer. */
    data object None : WebSessionDecision

    /** O documento carregou: desarmar o guarda de tempo (não escalar como falha). */
    data object CancelLoadGuard : WebSessionDecision

    /** Falha real: escalar para a próxima fonte. */
    data class Fail(val reason: String) : WebSessionDecision
}

object WebSessionPolicy {

    fun decide(event: WebSessionEvent, pageLoaded: Boolean): WebSessionDecision = when (event) {
        WebSessionEvent.PAGE_STARTED -> WebSessionDecision.None
        WebSessionEvent.PAGE_FINISHED -> WebSessionDecision.CancelLoadGuard
        WebSessionEvent.PAGE_ERROR -> WebSessionDecision.Fail("pagina")
        WebSessionEvent.RENDERER_GONE -> WebSessionDecision.Fail("renderizador")
        WebSessionEvent.LOAD_TIMEOUT ->
            if (pageLoaded) WebSessionDecision.None else WebSessionDecision.Fail("timeout")
        // Precisa do sinal observado (ver decideMediaTimeout).
        WebSessionEvent.MEDIA_TIMEOUT -> WebSessionDecision.None
    }

    /**
     * Etapa 19 §08 / Etapa 20 §04: decisão do guarda de MÍDIA do motor web.
     *
     * Um embed pode carregar a página (HTTP 200, `onPageFinished`) e AINDA assim nunca
     * iniciar a mídia — player interno com erro, exigência de gesto, origem sem sinal.
     * Como o app não consegue ler o `<video>` de iframe de outra origem, a evidência
     * legítima disponível é a REQUISIÇÃO DE MÍDIA pela própria WebView (playlist/segmento
     * passam pelo `WebViewClient.shouldInterceptRequest`).
     *
     * Etapa 20: uma requisição que apenas ACONTECE não é evidência suficiente — uma
     * playlist que responde 404/500 mantinha a sessão presa (defeito observado). Agora a
     * evidência precisa ser uma requisição de mídia **não reportada como erro HTTP**
     * ([mediaConfirmed]). Requisições que falham não seguram a tentativa.
     *
     * Regra: só é falha quando o documento CARREGOU e NÃO há evidência de mídia dentro da
     * janela de tolerância. Antes do carregamento, quem decide é o guarda de carregamento
     * ([LOAD_TIMEOUT]). Com evidência de mídia, nunca falha (não declaramos reprodução
     * confirmada — apenas não escalamos falha).
     */
    fun decideMediaTimeout(pageLoaded: Boolean, mediaConfirmed: Boolean): WebSessionDecision =
        if (pageLoaded && !mediaConfirmed) WebSessionDecision.Fail("sem_midia") else WebSessionDecision.None

    /**
     * Evidência de mídia ATIVA: pelo menos uma requisição de mídia que NÃO foi reportada
     * como erro HTTP. Ex.: 0 req / 0 err => falso; 1 req / 1 err (playlist 404) => falso;
     * 3 req / 1 err => verdadeiro. Nunca é prova de quadro renderizado.
     */
    fun mediaConfirmed(mediaRequests: Int, mediaErrors: Int): Boolean =
        mediaRequests > mediaErrors

    /** Extensões de recursos que evidenciam tentativa real de reprodução de mídia. */
    private val MEDIA_EXTENSIONS = setOf(
        "m3u8", "m3u", "mpd", "ts", "m4s", "mp4", "m4v", "aac", "m4a", "webm", "mp3"
    )

    /**
     * `true` quando a URL requisitada é candidata a playlist/segmento de mídia
     * (evidência de que o player embarcado TENTOU reproduzir). Não confirma quadro
     * renderizado; não inspeciona conteúdo nem contorna restrições de origem.
     */
    fun isMediaRequest(url: String?): Boolean {
        val u = url?.trim()?.lowercase() ?: return false
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false
        val afterHost = u.substringAfter("://", u).substringAfter('/', "")
        val path = afterHost.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "")
        return ext in MEDIA_EXTENSIONS
    }
}
