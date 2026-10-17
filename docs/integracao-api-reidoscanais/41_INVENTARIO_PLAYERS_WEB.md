# 41 — INVENTÁRIO DE PLAYERS WEB (ETAPA 19)

> Levantamento das tecnologias de player nos embeds da Fonte A (Rei dos Canais) e por que
> o app **observa requisições** em vez de controlar o player interno.

## 1. Cadeia de incorporação da Fonte A

```
rdcanais.org/<slug>
   └─ <iframe src="https://dadinhodetapioca.fun/play|<embed>/<slug>.html" allow="encrypted-media" allowfullscreen>
        └─ Clappr 0.3.13 + hls.js (p2p-media-loader)
             └─ window.STREAM_URLS (HLS tokenizado) → fetch de playlist + segmentos
```

- O iframe é **outra origem** → same-origin policy impede ler o `<video>`.
- O app carrega a página do iframe em `WebView` (IETF TLS preservado, sem conteúdo local,
  sem interfaces JS nativas, sem depuração web).
- A reprodução real é responsabilidade do Clappr/hls.js dentro do iframe.

## 2. O que o app consegue observar (sem contornar)

| Sinal | Origem | Confiável? |
|---|---|---|
| `onPageStarted/Finished` | documento | sim (não é mídia) |
| `onReceivedError` (main frame) | documento | sim |
| `onReceivedSslError` | documento | sim |
| `onRenderProcessGone` | processo WebView | sim |
| `shouldInterceptRequest` (`.m3u8/.ts/.m4s/.mpd/...`) | **rede da WebView** (inclui iframe) | sim para XHR/fetch normais |
| conteúdo do `<video>` do iframe | — | **não** (same-origin) |

## 3. Consequência de projeto

Não há código por player (`if clappr ... else ...`). A decisão "tem mídia?" é genérica via
requisição de rede, funcionando para hls.js, dash.js, `<video src=...>` ou Clappr, sem
acoplar o app a nenhum deles. Esse é o "adaptador universal" adotado (doc 42).

## 4. Provedores observados (amostra da auditoria)

- `Servidor Premium` (genérico/nacional) — maioria.
- `Globo <Estado>` (ex.: `Globo Ceará`) — regional, mapeado por `RdcCatalogMapper`.
- `Servidor Alternativo` — secundário.

## 5. Limites conhecidos

- Players que carregam mídia via **Service Worker** podem não passar por
  `shouldInterceptRequest` → falso "sem mídia". Caso de borda a monitorar em dispositivo.
- Não inspecionamos blobs, tokens nem conteúdo; não há extração de URL interna.
