# 33 — MOTOR WEB ESPECIALIZADO (ETAPA 16)

> Etapa 16 §05/§06/§07/§09. Correção **demonstravelmente necessária** no motor web,
> reutilizando o `WebPlayer` existente (sem criar um player por canal) e uma **Prova de
> Conceito isolada** para coleta de evidências na TV física.

## 1. O que foi corrigido (com justificativa)

### 1.1 Guarda de tempo do carregamento — defeito real (doc 32 §2.1)
- **Antes:** o `postDelayed` de 15 s chamava `onError` mesmo após `onPageFinished`,
  derrubando reproduções em andamento → "reprodução interrompida após alguns segundos".
- **Agora:** a decisão passou por `util/WebSessionPolicy.kt` (puro e testável).
  `LOAD_TIMEOUT` só é falha quando `pageLoaded == false`; `PAGE_FINISHED` desarma o guarda
  (`CancelLoadGuard`). Erros de página/SSL/renderizador permanecem falhas reais.

### 1.2 Distinção falha de PÁGINA × falha de MÍDIA (diagnóstico)
- `onReceivedHttpError` passa a registrar (`host` + extensão, sem URL/token) falhas de
  recursos de mídia (`.m3u8/.ts/.mp4/.m4s`) — evidência para TV física, **sem** disparar
  fallback (o player incorporado tem fallback de domínio próprio; não interferimos).
- `onRenderProcessGone` → falha real → próxima fonte (Etapa 15), sem recriação cega.

### 1.3 Versão do System WebView (evidência §04)
- Em DEBUG, o motor registra `System WebView versao=<...>` (tag `WebMotor`), permitindo
  correlacionar falhas com versões antigas de Chromium.

### 1.4 O que NÃO foi alterado (deliberadamente)
- `mediaPlaybackRequiresUserGesture=false`, tela cheia via `FrameLayout`, política de foco,
  `mixedContentMode = NEVER_ALLOW`, verificação TLS, ciclo de vida e `destroy` ao trocar de
  canal — já corretos e cobertos pelas Etapas 14/15.
- `Media3/ExoPlayer` permanece **intocado**: as correções são exclusivas do caminho web.
- Nenhuma fonte que já funciona foi mudada; nenhum canal removido; nenhum timeout ampliado;
  nenhuma camada extra de fallback adicionada.

## 2. Prova de Conceito isolada (Etapa 16 §05)

Componente **separado do player principal**, apenas no build DEBUG (`src/debug`):

- `app/src/debug/java/com/zap/tv/diagnostics/WebViewProbeActivity.kt`
- `app/src/debug/assets/webview_probe.html`
- `app/src/debug/AndroidManifest.xml` (activity `exported`, removida do Release)

A página reporta capacidades (MSE, `avc1`/`hevc`, HLS nativo, hls.js, cores), reproduz uma
**transmissão HLS de teste pública e autorizada** e reporta eventos do `<video>` e de
**tela cheia** via ponte JS (`ProbeBridge`). Permite separar erro interno (motor/composição/
fullscreen) de erro externo (fonte). Nenhum conteúdo de terceiros é usado; nada sensível é
coletado.

Execução:
```bash
adb shell am start -n com.zap.tv/.diagnostics.WebViewProbeActivity
adb logcat -s WebProbe
```
Checagem da PoC (Etapa 16 §05): 1 vídeo visível, 2 áudio audível, 3 contínuo, 4 tela cheia,
5 navegação, 6 encerramento, 7 sem tela preta da interface, 8 recursos liberados, 9 recupera
após interrupção, 10 conexão limitada — validáveis no aparelho.

## 3. Componentes alterados

| Arquivo | Alteração |
|---|---|
| `util/WebSessionPolicy.kt` | **novo** — decisões puras de sessão web |
| `presentation/player/WebPlayer.kt` | guarda de tempo corrigido; `onReceivedHttpError` (mídia); log de versão do WebView |
| `src/debug/.../diagnostics/WebViewProbeActivity.kt` | **novo** — PoC isolada |
| `src/debug/assets/webview_probe.html` | **novo** — página de teste controlada |
| `src/debug/AndroidManifest.xml` | **novo** — registra a PoC só em DEBUG |
| `test/.../util/WebSessionPolicyTest.kt` | **novo** — regressão |

## 4. Testes

- `:app:testDebugUnitTest` (`util.*` + `reidoscanais.*`): **118 testes, 0 falhas**
  (Etapa 15: 112). Novos: `WebSessionPolicyTest` (6) — cobre timeout após load (não falha),
  timeout antes do load (falha), desarme por `PAGE_FINISHED`, erro de página, renderizador,
  início de página.
- **Limitação honesta:** renderização real, tela cheia, áudio/vídeo, controle remoto,
  conexões lentas e ausência de loops em WebView exigem **aparelho** (testes de
  instrumentação/`WebView`); não são exercitáveis em teste JVM. A PoC foi criada justamente
  para essa validação.

## 5. Desempenho

- Sem alteração de timeouts ou de política de mídia: nenhum impacto de latência esperado.
- Correção do guarda elimina a **queda desnecessária** de sessões válidas (antes: fim
  garantido em ~15 s), o que **melhora** o tempo de exibição útil.
- A PoC é removida do Release (não afeta o app de produção).

## 6. Limitações externas (fora do controle do app)

- Embeds são **iframes de terceiros** → o app não observa o `<video>` (same-origin), não
  confirmando áudio/vídeo desses canais.
- HLS **tokenizado/expirável** (`tiktokcdn.live/.../secure/...`): validade e
  disponibilidade são do provedor.
- Suporte a **MSE/Codecs** depende do System WebView da TV.
- Não contornamos DRM, autenticação, incorporação nem meios de proteção.

## 7. Pendências para dispositivo real

1. Rodar a PoC na TV e anexar `logcat -s WebProbe` (capacidades + eventos de vídeo).
2. Confirmar imagem (não só áudio) em canais Clappr reais; confirmar tela cheia.
3. Confirmar ausência de queda após 15 s (regressão do guarda de tempo).
4. Verificar versão do System WebView e, se antiga, avaliar atualização do WebView do app.
5. Só então considerar qualquer ajuste adicional (§06 exige necessidade demonstrada).

## 8. Artefato

- `:app:assembleDebug` → **BUILD SUCCESSFUL**.
- APK: `app/build/outputs/apk/debug/app-debug.apk`
  - bytes: **17.941.106**
  - sha256: `3f84cec3b4343c9742b38fdeeafdceef9bcb69ea39f85bc985add7f06919c2ff`
  - `assets/webview_probe.html` embarcado; `WebViewProbeActivity` no manifest **debug**.
- Nenhum commit/push; nenhuma instalação automática; schema Room e assinatura inalterados;
  build Release não executado.
