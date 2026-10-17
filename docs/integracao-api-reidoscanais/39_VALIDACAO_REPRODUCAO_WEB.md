# 39 — VALIDAÇÃO DA REPRODUÇÃO WEB (ETAPA 19)

> Nota técnica do motor web após a Etapa 19. Separa **o que foi implementado**, **o que
> foi testado de forma controlada** (testes puros) e **o que depende de validação em
> dispositivo** (não observado — sem ADB/TV física).

## 1. Eventos de sessão do motor web (`WebSessionPolicy`)

| Evento | Quando | Decisão |
|---|---|---|
| `PAGE_STARTED` | novo documento | `None` |
| `PAGE_FINISHED` | documento principal carregou | `CancelLoadGuard` |
| `PAGE_ERROR` | erro do documento principal | `Fail("pagina")` |
| `RENDERER_GONE` | processo do System WebView morreu | `Fail("renderizador")` |
| `LOAD_TIMEOUT` | 15 s **sem** documento carregado | `Fail("timeout")` (ou `None` se já carregou) |
| `MEDIA_TIMEOUT` | 15 s após o load **sem** requisição de mídia | `Fail("sem_midia")` |

`LOAD_TIMEOUT` depois de `PAGE_FINISHED` é ignorado (correção Etapa 16 §03 — não derrubar
reprodução já iniciada).

## 2. Guarda de mídia (novo, Etapa 19)

```
onPageFinished ──► arma guarda de mídia (15 s)
shouldInterceptRequest(.m3u8/.mpd/.ts/.m4s/...) ──► cancela guarda (mídia existe)
guarda expira sem mídia ──► Fail("sem_midia") ──► próxima fonte
```

- Classificador puro `WebSessionPolicy.isMediaRequest(url)` — só HTTP/HTTPS, por extensão.
- `shouldInterceptRequest` **retorna `null`** (a WebView carrega normalmente). Não
  intercepta, não reescreve, não extrai tokens.
- Sinal por URL (`AtomicBoolean`), reiniciado a cada troca de fonte.

## 3. Testado de forma controlada (unitário)

`WebSessionPolicyTest` (147 testes no total, 0 falhas):
- `sem_midia_apos_carregar_e_falha_de_fonte` → `Fail("sem_midia")`
- `midia_observada_nunca_e_falha` → `None`
- `sem_midia_antes_de_carregar_nao_decide` → `None`
- `classificador_de_midia_reconhece_playlists_e_segmentos` (m3u8/ts/m4s/mpd)
- `classificador_de_midia_ignora_pagina_e_nao_http` (html/js/data:/null)

## 4. NÃO validado em dispositivo

- Se os embeds da Fonte A realmente requisitam `.m3u8/.ts` via `shouldInterceptRequest`
  (esperado com Clappr + hls.js + p2p-media-loader; ver doc 41).
- Autoplay real e `requestFullscreen()` sem gesto.
- Players que busquem mídia por **Service Worker** (podem escapar de `shouldInterceptRequest`
  → falso "sem mídia"; caso de borda conhecido).

## 5. Coleta para validar em TV/aparelho

```
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
# versão do System WebView (ou confira em Configurações → Apps)
adb logcat -s WebMotor WebProbe AvDiag
```

Esperado no log (DEBUG): `midia detectada host=...` quando a fonte funciona; e
`sem midia apos carregamento -> fallback motivo=sem_midia` quando a fonte está morta.
