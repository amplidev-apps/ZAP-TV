package com.zap.tv.presentation.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zap.tv.BuildConfig
import com.zap.tv.util.WebSessionDecision
import com.zap.tv.util.WebSessionEvent
import com.zap.tv.util.WebSessionPolicy
import java.util.concurrent.atomic.AtomicInteger

/**
 * Motor web especializado (Etapa 09 §05; revisado nas Etapas 14 §01 e 16 §06).
 *
 * WebView REUTILIZÁVEL que recebe dinamicamente o `embed_url`. Responsabilidades:
 * HTTPS, JavaScript, reprodução de mídia conforme permissões do Android, **tela cheia**,
 * ciclo de vida, tratamento de erros, guarda de tempo do carregamento inicial,
 * recarregamento controlado e liberação de recursos.
 *
 * Correção Etapa 14 §01 (áudio sem imagem): players embarcados frequentemente escondem o
 * elemento de vídeo inline e pedem tela cheia (`requestFullscreen()`). A View entregue por
 * [WebChromeClient.onShowCustomView] é ANEXADA a um contêiner real e removida em
 * [WebChromeClient.onHideCustomView].
 *
 * Correção Etapa 16 §03 (reprodução interrompida): o guarda de tempo do carregamento
 * inicial NÃO pode escalar falha depois que o documento já carregou — antes ele chamava
 * `onError` 15 s após a abertura, derrubando uma reprodução que já estava em andamento.
 * Agora o guarda é desarmado em `onPageFinished` via [WebSessionPolicy].
 *
 * Segurança (não contornar): verificação TLS preservada (erro de certificado CANCELA o
 * carregamento), sem arquivos/conteúdo local, sem extração de URLs internas, sem depuração
 * web, sem interfaces JavaScript nativas. Não contornamos DRM, autenticação, restrições de
 * incorporação nem controles de acesso dos provedores.
 *
 * Limitação real (documentada em doc 33): o player da Fonte A roda em um **iframe de
 * terceiros** (outra origem). Por same-origin policy o app NÃO consegue ler o estado do
 * elemento `<video>` de dentro desse iframe; por isso carregar a página nunca é tratado
 * como confirmação audiovisual.
 *
 * Correção Etapa 19 §08 (embeds presos em página sem mídia): um embed pode carregar a
 * página e nunca iniciar a mídia (player interno com erro/origem sem sinal/gesto exigido),
 * ficando a WebView presa para sempre — pois o guarda de carregamento é desarmado após
 * `onPageFinished`. A evidência LEGÍTIMA disponível é a requisição de playlist/segmento,
 * que passa pelo [WebViewClient.shouldInterceptRequest] mesmo dentro do iframe de outra
 * origem. Se, após o carregamento, NENHUMA mídia for requisitada dentro da janela, a fonte
 * é considerada falha e o player escala para a próxima tentativa autorizada. Observar a
 * requisição NÃO confirma quadro renderizado e não contorna restrições de origem.
 *
 * Refinamento Etapa 20 §04: uma requisição isolada não basta — playlists que respondem
 * 4xx/5xx mantinham a sessão presa. Só conta como evidência a requisição de mídia NÃO
 * reportada como erro HTTP ([WebSessionPolicy.mediaConfirmed]).
 *
 * Controle remoto: a WebView NÃO captura o foco (`descendantFocusability` bloqueado).
 */
private const val WEB_LOAD_TIMEOUT_MS = 15_000L

/**
 * Etapa 19 §08: janela de tolerância APÓS o documento carregar, antes de considerar a
 * FONTE sem mídia. Generosa de propósito ("respeita conexões lentas"); só falha quando
 * NENHUMA requisição de mídia foi observada nesse intervalo.
 */
private const val WEB_MEDIA_GRACE_MS = 15_000L
private const val TAG_WEB = "WebMotor"

/** Portador do Runnable do guarda de tempo (compartilhado com os callbacks da WebView). */
private class LoadGuardHolder { var runnable: Runnable? = null }

/** Portador do Runnable do guarda de MÍDIA (Etapa 19 §08). */
private class MediaGuardHolder { var runnable: Runnable? = null }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayer(
    url: String,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnLoading by rememberUpdatedState(onLoadingChanged)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnFullscreen by rememberUpdatedState(onFullscreenChanged)

    val handler = remember { Handler(Looper.getMainLooper()) }
    val loadGuard = remember { LoadGuardHolder() }
    var pageLoaded by remember { mutableStateOf(false) }
    // URL efetivamente SOLICITADA à WebView (não a URL final após redirecionamentos).
    // Evita loop de recarga quando a origem redireciona (Etapa 20 §11).
    var loadedUrl by remember { mutableStateOf<String?>(null) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var container by remember { mutableStateOf<FrameLayout?>(null) }

    // Desarma o guarda de tempo do carregamento (não escalar falha após o load).
    val cancelLoadGuard: () -> Unit = {
        loadGuard.runnable?.let { handler.removeCallbacks(it) }
        loadGuard.runnable = null
    }

    // Etapa 19/20: guarda de MÍDIA (playlist/segmento observado pela própria WebView).
    // Etapa 20 §04: só uma requisição NÃO reportada como erro HTTP conta como evidência.
    val mediaGuard = remember { MediaGuardHolder() }
    val mediaRequests = remember(url) { AtomicInteger(0) }
    val mediaErrors = remember(url) { AtomicInteger(0) }

    val cancelMediaGuard: () -> Unit = {
        mediaGuard.runnable?.let { handler.removeCallbacks(it) }
        mediaGuard.runnable = null
    }

    // Só arma DEPOIS do documento carregar: sem EVIDÊNCIA de mídia => fonte falha.
    val armMediaGuard: () -> Unit = {
        cancelMediaGuard()
        val d = Runnable {
            val requests = mediaRequests.get()
            val errors = mediaErrors.get()
            val confirmed = WebSessionPolicy.mediaConfirmed(requests, errors)
            val decision = WebSessionPolicy.decideMediaTimeout(pageLoaded = true, mediaConfirmed = confirmed)
            if (decision is WebSessionDecision.Fail) {
                if (BuildConfig.DEBUG) Log.d(TAG_WEB, "sem midia req=$requests err=$errors -> fallback motivo=${decision.reason}")
                currentOnLoading(false)
                currentOnError("Sem mídia no player web")
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG_WEB, "midia ativa req=$requests err=$errors")
            }
        }
        mediaGuard.runnable = d
        handler.postDelayed(d, WEB_MEDIA_GRACE_MS)
    }

    // Liberação de recursos: ao sair do player web (troca de canal/mecanismo), a WebView é
    // parada e destruída para não manter áudio/sessão ativa nem vazar memória.
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { destroyWebView(it) }
            webView = null
            container?.let { exitFullscreen(it) }
            container = null
            cancelLoadGuard()
            cancelMediaGuard()
        }
    }

    // Guarda de tempo do CARREGAMENTO INICIAL (reinicia a cada URL). Só escalona falha
    // enquanto o documento não carregou — nunca durante uma reprodução já iniciada.
    DisposableEffect(url) {
        pageLoaded = false
        mediaRequests.set(0)
        mediaErrors.set(0)
        cancelMediaGuard()
        val runnable = Runnable {
            when (val d = WebSessionPolicy.decide(WebSessionEvent.LOAD_TIMEOUT, pageLoaded)) {
                is WebSessionDecision.Fail -> {
                    if (BuildConfig.DEBUG) Log.d(TAG_WEB, "timeout de carregamento motivo=${d.reason}")
                    currentOnLoading(false)
                    currentOnError("Tempo esgotado ao carregar o canal")
                }
                else -> Unit
            }
        }
        loadGuard.runnable = runnable
        handler.postDelayed(runnable, WEB_LOAD_TIMEOUT_MS)
        onDispose {
            handler.removeCallbacks(runnable)
            loadGuard.runnable = null
            cancelMediaGuard()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val root = FrameLayout(ctx).apply { setBackgroundColor(AndroidColor.BLACK) }
            val web = WebView(ctx).apply {
                // Política de foco: nunca captura o controle remoto do ZAP-TV.
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setBackgroundColor(AndroidColor.BLACK)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Players web iniciam sem gesto do usuário (Android TV não tem toque).
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = false
                    allowContentAccess = false
                    // Nunca permitir conteúdo misto/inseguro.
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportMultipleWindows(false)
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                WebView.setWebContentsDebuggingEnabled(false)

                if (BuildConfig.DEBUG) {
                    try {
                        val v = WebView.getCurrentWebViewPackage()?.versionName ?: "-"
                        Log.d(TAG_WEB, "System WebView versao=$v")
                    } catch (_: Exception) { }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                        pageLoaded = false
                        mediaRequests.set(0)
                        mediaErrors.set(0)
                        currentOnLoading(true)
                    }

                    override fun onPageFinished(view: WebView?, u: String?) {
                        pageLoaded = true
                        cancelLoadGuard()
                        currentOnLoading(false)
                        // Documento carregado, mas mídia ainda não confirmada: arma o
                        // guarda que escala falha se nenhuma playlist/segmento chegar.
                        armMediaGuard()
                    }

                    // Etapa 19/20: observa requisições de mídia da própria WebView
                    // (inclusive do iframe de outra origem). Nunca intercepta/contorna:
                    // retorna null para a WebView carregar normalmente. O contador é
                    // confrontado com os erros HTTP em `onReceivedHttpError`.
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (WebSessionPolicy.isMediaRequest(request?.url?.toString())) {
                            val n = mediaRequests.incrementAndGet()
                            if (BuildConfig.DEBUG && n == 1) {
                                Log.d(TAG_WEB, "midia requisitada host=${request?.url?.host ?: "-"}")
                            }
                        }
                        return null
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        // Somente o documento principal interrompe a reprodução;
                        // erros de sub-recursos (ads/analytics) são ignorados.
                        if (request?.isForMainFrame == true) {
                            cancelLoadGuard()
                            cancelMediaGuard()
                            currentOnLoading(false)
                            currentOnError("Falha ao carregar (${error?.errorCode ?: -1})")
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        // Preserva a verificação TLS: nunca prossegue em erro de certificado.
                        handler?.cancel()
                        cancelLoadGuard()
                        cancelMediaGuard()
                        currentOnLoading(false)
                        currentOnError("Certificado TLS inválido")
                    }

                    // Etapa 16 §06 / 20 §04: distingue FALHA DE MÍDIA (HLS/segmentos).
                    // Uma requisição de mídia que responde 4xx/5xx NÃO é evidência de
                    // reprodução — é contabilizada para o guarda de mídia. Sem expor
                    // URL/token; não dispara fallback aqui (o player incorporado pode
                    // tentar outro domínio antes).
                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        val u = request?.url ?: return
                        if (WebSessionPolicy.isMediaRequest(u.toString())) {
                            val e = mediaErrors.incrementAndGet()
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG_WEB, "midia http=${errorResponse?.statusCode ?: -1} host=${u.host ?: "-"} err=$e")
                            }
                        }
                    }

                    // Etapa 15 §02: falha do processo de renderização do System WebView
                    // (composição/held-back) é uma falha REAL — escala para a próxima
                    // fonte. Não tentamos contornar restrições do player incorporado.
                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?
                    ): Boolean {
                        when (WebSessionPolicy.decide(WebSessionEvent.RENDERER_GONE, pageLoaded)) {
                            is WebSessionDecision.Fail -> {
                                if (BuildConfig.DEBUG) Log.d(TAG_WEB, "renderizador web falhou -> fallback")
                                cancelLoadGuard()
                                cancelMediaGuard()
                                currentOnLoading(false)
                                currentOnError("Renderizador web falhou")
                            }
                            else -> Unit
                        }
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    // Etapa 14 §01: anexa o vídeo em tela cheia ao contêiner real. Sem isso,
                    // a View fornecida era descartada e só o áudio permanecia.
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        val containerRoot = container ?: return
                        currentOnFullscreen(true)
                        if (view == null) return
                        exitFullscreen(containerRoot)
                        containerRoot.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        view.bringToFront()
                    }

                    override fun onHideCustomView() {
                        container?.let { exitFullscreen(it) }
                        currentOnFullscreen(false)
                    }
                }

                webView = this
            }
            // WebView inline preenche o contêiner; a View de tela cheia é sobreposta depois.
            root.addView(web, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            container = root
            loadedUrl = url
            web.loadUrl(url)
            root
        },
        update = { rootView ->
            // Recarregamento controlado apenas quando a URL SOLICITADA muda (troca de
            // fonte). Não usar wv.url: um redirecionamento da origem faria a condição
            // valer sempre e geraria loop de recarga (Etapa 20 §11).
            val wv = webView ?: return@AndroidView
            if (loadedUrl != url) {
                cancelLoadGuard()
                exitFullscreen(rootView)
                wv.stopLoading()
                loadedUrl = url
                wv.loadUrl(url)
            }
        }
    )
}

/** Remove a View de tela cheia do contêiner, preservando a WebView inline (índice 0). */
private fun exitFullscreen(root: FrameLayout) {
    var i = root.childCount - 1
    while (i >= 1) {
        root.removeViewAt(i)
        i--
    }
}

private fun destroyWebView(wv: WebView) {
    try {
        wv.stopLoading()
        wv.loadUrl("about:blank")
        wv.removeAllViews()
        wv.destroy()
    } catch (_: Exception) {
        // Liberação best-effort: nunca propaga falha de teardown.
    }
}
