# 36 — DIAGNÓSTICO DOS EMBEDS (ETAPA 17)

> Etapa 17 §07/§08/§09. Registra o estado do motor web após a Etapa 16, separa **fatos
> confirmados** de **hipóteses** e lista os dados de ADB necessários antes de novas
> alterações no player embarcado. **Nada** aqui contorna DRM, TLS, autenticação ou
> restrições de incorporação.

## 1. Fatos confirmados (Etapa 16 — preservados)

1. A Fonte A entrega um wrapper `https://rdcanais.org/<slug>` que embute um
   `<iframe>` de outra origem (`dadinhodetapioca.fun/play|embed/<slug>.html`) com
   **Clappr 0.3.13 + hls.js (MSE) + p2p-media-loader** e HLS **tokenizado**.
2. O `<video>` roda **dentro do iframe cross-origin** → por same-origin policy o app
   **não** lê o estado do elemento de vídeo. Carregar a página **nunca** é tratado como
   confirmação audiovisual.
3. O defeito de **guarda de 15 s** (que derrubava reprodução em andamento ao chamar
   `onError` após `onPageFinished`) foi corrigido por `util/WebSessionPolicy.kt`. **Não**
   reintroduzir timeout pós-load.
4. `mediaPlaybackRequiresUserGesture=false`, tela cheia via `FrameLayout`, política de foco
   e verificação TLS (`handler.cancel()`) estão corretos.

## 2. Sintomas relatados × classificação

| Canal/sintoma | Classificação | Observação |
|---|---|---|
| **Gloob** inicia e interrompe | provável falha de **mídia/segmentos** ou renderizador | já elegível a fallback (`onRenderProcessGone`); confirmar com log `WebMotor` |
| **GNT** não carrega no app (funciona no navegador) | **pendente** — possível exigência não suportada no WebView | verificar DRM/versão de WebView; **não** contornar |
| **SporTV / Premiere** falhas | provável falha de fonte (HLS tokenizado/segmentos) | regra `PreferredProvider = Servidor Premium` já aplicada |
| UI do player aparece no carregamento | **esperado** (UI do próprio site) | remover exigiria manipular scripts de terceiros — proibido |

## 3. O que já está implementado (não alterar)

- Fallback por falha **real**: página (documento principal), SSL, e renderizador
  (`onRenderProcessGone`) → próxima fonte (Etapa 15/16).
- `onReceivedHttpError` registra falhas de **mídia** (host + extensão, sem URL/token) apenas
  para diagnóstico, **sem** disparar fallback precoce (o player do site tem fallback de
  domínio próprio).
- Diagnóstico DEBUG: `WebMotor`, `AvDiag`, `RegionalDiag`, `TuningDiag`; versão do System
  WebView.

## 4. Hipóteses rejeitadas por falta de evidência (Etapa 16 §11 / 17 §08)

- aumentar timeouts cegamente;
- `mixedContentMode = ALWAYS_ALLOW`;
- trocar `SurfaceView` por `TextureView`;
- recriar a WebView após crash do renderizador;
- automatizar cliques/contornar anúncios ou controle de acesso.

## 5. Dados necessários (ADB) antes de mexer no player

`adb` **não está instalado** neste ambiente e não há TV física acessível. Para avançar:

```bash
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys package com.google.android.webview | grep versionName
# com a PoC isolada (build DEBUG):
adb shell am start -n com.zap.tv/.diagnostics.WebViewProbeActivity
adb logcat -s WebProbe
# com um canal Clappr real (ex.: Gloob, GNT, SporTV):
adb logcat -s WebMotor AvDiag RegionalDiag
```

A PoC (`src/debug`) separa **erro interno** (motor/MSE/composição/fullscreen) de **erro
externo** (fonte), sem tocar o player principal.

## 6. Conclusão

Nenhuma alteração especulativa foi feita no motor web nesta etapa. Os sintomas de Gloob/GNT/
SporTV/Premiere permanecem como **diagnóstico pendente** até a coleta de ADB, conforme a
regra de não afirmar reprodução sem evidência real e de não contornar restrições dos
provedores.
