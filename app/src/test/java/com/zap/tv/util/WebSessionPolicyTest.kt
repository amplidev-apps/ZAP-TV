package com.zap.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 16 §03/§09: sessão do motor web.
 * Foco principal: o guarda de tempo do carregamento NÃO pode derrubar uma página já
 * carregada (defeito real que interrompia a reprodução web após alguns segundos).
 */
class WebSessionPolicyTest {

    @Test
    fun timeout_apos_pagina_carregada_nao_e_falha() {
        // Documento já carregou: o timeout de carregamento é irrelevante.
        assertEquals(
            WebSessionDecision.None,
            WebSessionPolicy.decide(WebSessionEvent.LOAD_TIMEOUT, pageLoaded = true)
        )
    }

    @Test
    fun timeout_antes_do_carregamento_e_falha() {
        val d = WebSessionPolicy.decide(WebSessionEvent.LOAD_TIMEOUT, pageLoaded = false)
        assertTrue(d is WebSessionDecision.Fail)
        assertEquals("timeout", (d as WebSessionDecision.Fail).reason)
    }

    @Test
    fun pagina_carregada_desarma_o_guarda() {
        assertEquals(
            WebSessionDecision.CancelLoadGuard,
            WebSessionPolicy.decide(WebSessionEvent.PAGE_FINISHED, pageLoaded = false)
        )
    }

    @Test
    fun erro_de_pagina_e_falha_de_pagina() {
        val d = WebSessionPolicy.decide(WebSessionEvent.PAGE_ERROR, pageLoaded = true)
        assertEquals("pagina", (d as WebSessionDecision.Fail).reason)
    }

    @Test
    fun renderizador_encerrado_e_falha_real() {
        val d = WebSessionPolicy.decide(WebSessionEvent.RENDERER_GONE, pageLoaded = true)
        assertEquals("renderizador", (d as WebSessionDecision.Fail).reason)
    }

    @Test
    fun inicio_de_pagina_nao_decide_nada() {
        assertEquals(
            WebSessionDecision.None,
            WebSessionPolicy.decide(WebSessionEvent.PAGE_STARTED, pageLoaded = false)
        )
    }

    // ---- Etapa 19 §08 / 20 §04: guarda de MÍDIA (embed preso sem reproduzir) ----

    @Test
    fun sem_midia_apos_carregar_e_falha_de_fonte() {
        val d = WebSessionPolicy.decideMediaTimeout(pageLoaded = true, mediaConfirmed = false)
        assertTrue(d is WebSessionDecision.Fail)
        assertEquals("sem_midia", (d as WebSessionDecision.Fail).reason)
    }

    @Test
    fun midia_confirmada_nunca_e_falha() {
        assertEquals(
            WebSessionDecision.None,
            WebSessionPolicy.decideMediaTimeout(pageLoaded = true, mediaConfirmed = true)
        )
    }

    @Test
    fun sem_midia_antes_de_carregar_nao_decide() {
        // Antes do load, quem decide é o guarda de carregamento, não o de mídia.
        assertEquals(
            WebSessionDecision.None,
            WebSessionPolicy.decideMediaTimeout(pageLoaded = false, mediaConfirmed = false)
        )
    }

    @Test
    fun evidencia_de_midia_requer_requisicao_sem_erro() {
        // Nenhuma requisição: sem evidência.
        assertTrue(!WebSessionPolicy.mediaConfirmed(0, 0))
        // Playlist que responde 404: requisição existe, mas TODAS falharam => sem evidência.
        assertTrue(!WebSessionPolicy.mediaConfirmed(1, 1))
        assertTrue(!WebSessionPolicy.mediaConfirmed(5, 5))
        // Ao menos uma requisição sem erro HTTP: evidência observável.
        assertTrue(WebSessionPolicy.mediaConfirmed(1, 0))
        assertTrue(WebSessionPolicy.mediaConfirmed(3, 1))
    }

    @Test
    fun classificador_de_midia_reconhece_playlists_e_segmentos() {
        assertTrue(WebSessionPolicy.isMediaRequest("https://cdn.example.com/live/index.m3u8"))
        assertTrue(WebSessionPolicy.isMediaRequest("https://cdn.example.com/live/seg.ts?token=abc"))
        assertTrue(WebSessionPolicy.isMediaRequest("https://cdn.example.com/v/init.m4s"))
        assertTrue(WebSessionPolicy.isMediaRequest("https://cdn.example.com/a/index.mpd"))
    }

    @Test
    fun classificador_de_midia_ignora_pagina_e_nao_http() {
        assertTrue(!WebSessionPolicy.isMediaRequest("https://rdcanais.org/globo"))
        assertTrue(!WebSessionPolicy.isMediaRequest("https://cdn.example.com/app.js"))
        assertTrue(!WebSessionPolicy.isMediaRequest("data:text/html,<html></html>"))
        assertTrue(!WebSessionPolicy.isMediaRequest(null))
    }
}
