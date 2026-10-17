# 32 — DIAGNÓSTICO DO MOTOR WEB NA TV (ETAPA 16)

> Etapa 16 §03/§04/§05. Identifica a causa real das falhas de reprodução dos canais da
> Fonte A (Rei dos Canais) no ZAP-TV, separando o que está sob controle do aplicativo do
> que depende do provedor ou do System WebView do aparelho.
>
> **Ambiente desta execução:** sem `adb`/TV física conectada (`adb devices` vazio). As
> conclusões abaixo vêm de análise de código + inspeção das páginas de embed (HTTPS) +
> evidências de dados já coletadas. Os itens que exigem aparelho estão marcados como
> **PENDENTE (TV física)**.

## 1. Como a Fonte A realmente reproduz (evidência)

`https://rdcanais.org/<slug>` **não** contém mídia: é uma página-wrapper que embute um
`<iframe>`:

```html
<iframe src="https://dadinhodetapioca.fun/play/<slug>.html"
        allow="encrypted-media" allowfullscreen frameborder="0"></iframe>
```

O documento do wrapper ainda executa um `sbChecker()` que, se detectar `sandbox` no
`frameElement` ou falha em `document.domain`, substitui o corpo por "Acesso Bloqueado".
O ZAP-TV **não** usa `sandbox`, logo não cai nessa condição.

Inspeção do iframe interno (`/play/<slug>.html`, acessível com `Referer` correto):
`Clappr 0.3.13` + `hls.js` (via `p2p-media-loader`) + plugin de qualidade, com:

```js
window.STREAM_URLS = ["https://tiktokcdn.live/live/secure/<token>/<expira>/<hash>/<slug>/index.m3u8"];
new Clappr.Player({ source: m3u8Url, autoPlay: true, ... });
player.play();
```

Conclusões técnicas:

1. A mídia é **HLS por MSE** (hls.js), não HLS nativo do Chromium.
2. A URL é **tokenizada e expira** (`/secure/<token>/<expira>/...`); o player escolhe o
   domínio alcançável (`pickBestUrl`) e tem fallback próprio para domínio alternativo.
3. Há interceptação de `fetch`/`XMLHttpRequest` (referrerPolicy) e uso de P2P (não
   essencial — há caminho HTTP).
4. O iframe é **de outra origem** (`dadinhodetapioca.fun`), o que é decisivo para o que o
   app consegue ou não observar (§3).

Há dois padrões de wrapper: `/play/<slug>.html` (leve, Clappr direto) e `/embed/<slug>.html`
(HTML grande e ofuscado, com anúncios). Ambos dependem de MSE no Chromium.

## 2. Causas sob controle do ZAP-TV (evidência de código)

### 2.1 BUG CONFIRMADO — guarda de tempo derrubava reprodução já iniciada
`WebPlayer.kt` (Etapa 14/15) armava, em `DisposableEffect(url)`, um `postDelayed` de
`WEB_LOAD_TIMEOUT_MS = 15_000` que chamava `onLoading(false)` **e** `onError(...)` **sem
verificar se o documento já havia carregado**. Esse Runnable só era cancelado na troca de
URL. Portanto, **toda** reprodução web que estivesse funcionando era abortada 15 s após a
abertura, disparando `advanceAttempt("web_error")`.

Isso explica diretamente o sintoma relatado *"reprodução interrompida após alguns
segundos"* e falsos fallbacks. **Corrigido** na Etapa 16 (doc 33): o timeout só escala
falha enquanto `pageLoaded == false`.

### 2.2 Confirmação audiovisual não observável em iframe de terceiros
Por same-origin policy, o documento do ZAP-TV (ou de `rdcanais.org`) **não** pode ler o
`<video>` dentro de `dadinhodetapioca.fun`. Portanto o app **não pode**, de forma legítima,
confirmar "áudio e vídeo" nesses embeds. A Etapa 15 já trata "página carregada" como
**não verificação** (nunca como sucesso). Não há como "melhorar" isso sem contornar
restrições do provedor — o que é proibido.

### 2.3 Guarda de foco/controle remoto e ciclo de vida
Já corretos (Etapa 14/15): WebView sem foco (`FOCUS_BLOCK_DESCENDANTS`), destroy ao trocar
de mecanismo, evita reprodução duplicada.

## 3. Diferenças entre A (navegador), B (WebView isolada), C (WebView no ZAP-TV)

| Fator | Efeito | Status |
|---|---|---|
| **Versão do System WebView** | TVs antigas trazem Chromium desatualizado; hls.js/MSE pode falhar (falta de `SourceBuffer`/codecs) | **PENDENTE (TV física)** — versão é registrada em DEBUG (`WebMotor`) e no probe |
| **MSE / codecs (avc1, hevc)** | se o WebView não anunciar os codecs, o vídeo não monta (pode sobrar áudio) | **PENDENTE** — reportado pelo probe `assets/webview_probe.html` |
| **Aceleração gráfica / superfície** | MSE em WebView precisa de composição por GPU; overlay/hardware pode dar tela preta | **PENDENTE** |
| **Política de autoplay** | `mediaPlaybackRequiresUserGesture=false` já aplicada | OK no código |
| **Tela cheia (`onShowCustomView`)** | corrigida na Etapa 14 (FrameLayout + anexo) | OK no código |
| **Ciclo de vida** | destroy ao trocar de canal; reload controlado | OK no código |
| **Composição com Compose** | `AndroidView`/`FrameLayout` | **PENDENTE (validação visual)** |
| **Recursos de mídia/mixed content** | `MIXED_CONTENT_NEVER_ALLOW` (segurança) — streams `https` atuais não são afetados | OK |
| **P2P (p2p-media-loader)** | pode logar erro e cair para HTTP; não bloqueia | **PENDENTE** |
| **Token/expiracao do HLS** | fonte externa: URL expira; fora do controle do app | limitação externa |

## 4. Coleta de evidências na TV física (ADB autorizado)

```bash
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys package com.google.android.webview | grep versionName   # versão do WebView
adb shell am start -n com.zap.tv/.diagnostics.WebViewProbeActivity
adb logcat -c && adb logcat -s WebProbe WebMotor AvDiag ZapPlayer RegionalDiag TuningDiag
```

O activity **isolado** (`WebViewProbeActivity`, apenas build DEBUG) carrega
`assets/webview_probe.html`, que:
- reporta `webview_versao`, `mediaSource`, `mse_avc1`, `mse_hevc`, `nativeHls`, `hlsJs`,
  `cores`;
- reproduz uma **transmissão HLS de teste pública e autorizada** (Big Buck Bunny / Mux);
- reporta eventos do `<video>` (`loadedmetadata`, `playing`, `waiting`, `stalled`, `error`)
  com `videoWidth`/`videoHeight`/`currentTime`;
- exercita **tela cheia** e reporta `tela_cheia ON/OFF`.

Nenhum dado sensível é coletado (sem cookies/tokens/credenciais; URLs de mídia não são
registradas, apenas host).

Para capturar falhas de mídia de terceiros sem violar privacidade, o motor web registra
apenas `host` + extensão em `onReceivedHttpError` (`.m3u8/.ts/.mp4/.m4s`).

## 5. Hipóteses NÃO confirmadas (não alteradas por falta de evidência)

- **"Aumentar timeouts"**: rejeitado — o problema do timeout era o inverso (disparo
  indevido), não a duração.
- **Trocar `mixedContentMode` para ALWAYS_ALLOW**: rejeitado — reduz segurança; streams
  atuais são HTTPS.
- **Trocar `SurfaceView`→`TextureView`**: rejeitado sem evidência de vídeo preto por
  superfície; aguarda dados de TV.
- **Recriar WebView automaticamente após crash do renderizador**: mantido o fallback
  controlado (sem loop); recriação cega não demonstrada como necessária.

## 6. Pendências (dependentes de dispositivo/fonte externa)

1. Versão do System WebView da TV e suporte a MSE/codecs.
2. Comportamento visual = imagem (não apenas áudio) nos embeds Clappr.
3. Comportamento do P2P e do fallback de domínio do provedor.
4. Expiração real dos tokens HLS na TV.
5. Validar tela cheia e controle remoto no aparelho.

**Se esses dados não puderem ser coletados, este diagnóstico é o ponto de parada antes de
qualquer alteração adicional no player** (conforme §11).
