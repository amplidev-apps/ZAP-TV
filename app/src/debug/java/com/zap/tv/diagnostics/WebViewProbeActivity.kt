package com.zap.tv.diagnostics

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Etapa 16 §05 — Prova de conceito ISOLADA do motor web (apenas build DEBUG).
 *
 * Carrega um documento CONTROLADO pelo projeto (`assets/webview_probe.html`) que:
 *  - reporta capacidades do System WebView (versão, MSE, codecs, HLS nativo, hls.js);
 *  - reproduz uma transmissão HLS de TESTE autorizada e pública;
 *  - reporta eventos do elemento `<video>` por uma ponte JS (`ProbeBridge`);
 *  - exercita TELA CHEIA via API de fullscreen do HTML.
 *
 * Objetivo: diferenciar erro INTERNO (WebView do app, composição, fullscreen, ciclo de
 * vida) de erro EXTERNO (fonte de mídia/provedor). Não usa nenhuma página de terceiros.
 *
 * Execução na TV física (ADB autorizado):
 *   adb shell am start -n com.zap.tv/.diagnostics.WebViewProbeActivity
 *   adb logcat -s WebProbe
 *
 * Nenhum dado sensível é coletado (sem cookies/tokens/credenciais).
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewProbeActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var customView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        web = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "pagina carregada")
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        Log.d(TAG, "erro_documento code=${error?.errorCode}")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    val u = request?.url ?: return
                    val p = u.path ?: ""
                    if (p.endsWith(".m3u8") || p.endsWith(".ts") || p.endsWith(".mp4") || p.endsWith(".m4s")) {
                        Log.d(TAG, "erro_midia http=${errorResponse?.statusCode ?: -1} host=${u.host ?: "-"}")
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    Log.d(TAG, "renderizador_encerrado didCrash=${detail?.didCrash()}")
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    view?.let {
                        root.addView(
                            it,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        it.bringToFront()
                    }
                    Log.d(TAG, "tela_cheia ON")
                }

                override fun onHideCustomView() {
                    customView?.let { root.removeView(it) }
                    customView = null
                    Log.d(TAG, "tela_cheia OFF")
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(TAG, "console ${consoleMessage?.message()}")
                    return true
                }
            }

            addJavascriptInterface(ProbeBridge(), "ProbeBridge")
        }

        root.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        try {
            Log.d(TAG, "webview_versao=${WebView.getCurrentWebViewPackage()?.versionName ?: "-"}")
        } catch (_: Exception) {
        }

        web.loadUrl("file:///android_asset/webview_probe.html")
    }

    override fun onDestroy() {
        try {
            web.stopLoading()
            web.removeAllViews()
            web.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    /** Ponte JS de diagnóstico — recebe apenas eventos de estado do player de teste. */
    inner class ProbeBridge {
        @JavascriptInterface
        fun onEvent(json: String) {
            Log.d(TAG, "probe $json")
        }
    }

    private companion object {
        const val TAG = "WebProbe"
    }
}
