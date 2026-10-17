# 19 — IMPLEMENTAÇÃO DO PLAYER HÍBRIDO UNIVERSAL

> Etapa 09. Objetivo: suporte arquitetural a **todos** os canais/embeds da API Rei dos Canais, com player **híbrido Media3/ExoPlayer (stream direto) + Android WebView (embed web)**, sem implementações individuais por emissora. Catálogo, regionalização, numeração fixa, favoritos/histórico e canais 8/9 preservados. Sem commit/push, sem release, APK não instalado.

## 1. Arquitetura final

```
ChannelNumberEntity (Room, inalterado)
        │  tune()
        ▼
PlayerViewModel
   ├─ ChannelRepository.getStreamsForChannel()   → fontes DIRETAS (Fonte B/exceções)
   ├─ ReiDosCanaisRepository.getEmbedsForChannel() → embeds web (Fonte A)
   └─ PlaybackResolver.resolve(...)               → PlaybackPlan
        ├─ DIRECT_STREAM → ExoPlayerManager (Media3)  → PlayerView
        ├─ WEB_EMBED     → WebPlayer (Android WebView) → WebView
        └─ UNAVAILABLE   → "SEM SINAL" (não modal)
```

Seleção **centralizada** em `PlaybackResolver` (um único ponto). A UI apenas reage a `state.mechanism`.

## 2. Componentes adicionados

- `util/PlaybackResolver.kt` — enum `PlaybackMechanism { DIRECT_STREAM, WEB_EMBED, UNAVAILABLE }`, `PlaybackPlan` e `PlaybackResolver` (puro/testável). Seleciona mecanismo, valida/embeds deduplicados, preserva provedor/qualidade.
- `presentation/player/WebPlayer.kt` — composable AndroidView com **WebView reutilizável** que recebe dinamicamente o `embed_url`.
- `ReiDosCanaisRepository.getEmbedsForChannel(channelId, channelName)` — expõe os embeds autorizados do canal (cache 5 min, sem consultas extras por sintonia).

Nenhuma dependência nova; nenhuma alteração de esquema Room (embeds consultados em runtime pelo cache da Fonte A).

## 3. Arquivos modificados

- `presentation/player/PlayerViewModel.kt` — estado `mechanism/webEmbeds/webIndex/webUrl`; resolução no `playChannel`; fallback ExoPlayer→WebView (`nextStreamOnError`); `setWebLoading`, `onWebError`, `selectWebSource`.
- `presentation/player/PlayerScreen.kt` — renderização condicional (`PlayerView` só em DIRECT_STREAM; `WebPlayer` em WEB_EMBED); ExoPlayer limpo nos demais mecanismos.
- `data/reidoscanais/ReiDosCanaisRepository.kt` — `getEmbedsForChannel`.
- Novos: `util/PlaybackResolver.kt`, `presentation/player/WebPlayer.kt`, teste `util/PlaybackResolverTest.kt`, este doc.

## 4. Política de seleção de mecanismos

Precedência **determinística**: stream direto → embed web → indisponível.

- Streams diretos já chegam filtrados por `CatalogFilters.isPlayableStreamUrl`; são sempre preferidos (menor latência/melhor controle).
- Sem stream direto, mas com embed HTTP/HTTPS → WebView.
- Sem nenhum → `UNAVAILABLE` ("SEM SINAL").
- A ausência de erro HTTP **não** é considerada reprodução bem-sucedida: isso depende do player real.

## 5. Funcionamento da WebView

- HTTPS/HTTP; JavaScript e DOM storage habilitados (necessários aos players); `mediaPlaybackRequiresUserGesture = false` (Android TV sem toque); tela cheia via `WebChromeClient` (`onShowCustomView`/`onHideCustomView`).
- **TLS preservado**: `onReceivedSslError` **cancela** (nunca `proceed()`); `mixedContentMode = NEVER_ALLOW`; `allowFileAccess`/`allowContentAccess = false`; depuração web desativada; sem interfaces JS nativas; sem extração de URLs internas; `setSupportMultipleWindows(false)`.
- Erros: `onReceivedError` apenas do frame principal → `onWebError()`; **timeout de 15 s** por URL.
- Recarregamento controlado: ao mudar a URL, `stopLoading()` antes de `loadUrl()` (evita duas reproduções simultâneas).

## 6. Política de fontes alternativas (§07)

- Todos os embeds do canal são preservados com **provedor** e **qualidade** (`RdcEmbedDto`).
- `onWebError()` avança para o próximo embed; esgotados, encerra em indisponível — **sem tentativas infinitas**.
- `selectWebSource(index)` disponível para seleção manual; `webEmbeds` exposto no estado.
- Fallback entre mecanismos: se todas as fontes diretas falharem, o ViewModel muda para `WEB_EMBED` (uma única transição).

## 7. Integração do controle remoto (§08)

- A WebView é **não focável** (`isFocusable=false`, `isFocusableInTouchMode=false`, `descendantFocusability=FOCUS_BLOCK_DESCENDANTS`). O **ZAP-TV mantém a prioridade** do D-Pad/OK/BACK, digitação numérica, troca de canais, favoritos e overlays (tratados no root `Box`).
- A WebView **não sequestra** o controle remoto; áudio é controlado por `AudioManager` (como antes).

## 8. Gerenciamento de memória (§09)

- **Uma única sessão ativa**: `mechanism` exclusivo — quando não é DIRECT_STREAM, o ExoPlayer é limpo (`mgr.clear()`); quando não é WEB_EMBED, a WebView é destruída.
- A WebView é reutilizada por canal/URL (sem pré-carregar canais); no `onDispose` faz `stopLoading` → `about:blank` → `removeAllViews` → `destroy`.
- Nenhum pré-carregamento de múltiplos canais; sem builds simultâneos.

## 9. Testes executados

```
bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=384m"
```

| Classe | Testes | Aprovados |
|---|---|---|
| `PlaybackResolverTest` (novo) | 7 | 7 |
| `CatalogFiltersTest` | 11 | 11 |
| `ChannelNumberingRegionalTest` | 17 | 17 |
| `ChannelNumberingCatalogOnlyTest` | 3 | 3 |
| `AffiliateRegistryTest` | 9 | 9 |
| `RdcCatalogMapperTest` | 9 | 9 |
| `M3uCatalogGeneratorTest` | 1 | 0 (falha pré-existente — OOM) |
| **Total** | **57** | **56** |

Cobertura Etapa 09 (§12, itens testáveis): seleção ExoPlayer×WebView; precedência de stream direto; embeds válidos/inválidos; deduplicação com provedor/qualidade; ordem para fallback; ausência de fontes → indisponível. Itens 6, 10, 11 (troca/áudio/overlays/controle remoto) e reprodução real exigem runtime (§12: não declarar reprodução validada só por unit tests).

## 10. Resultado da compilação

`bash ./gradlew :app:assembleDebug --no-daemon --max-workers=2` → **BUILD SUCCESSFUL** (1m10s).
APK: `app/build/outputs/apk/debug/app-debug.apk` — **17.897.703 bytes** — SHA-256 `f09a1c3f079bcccc7231634257faea89d89f73320acd9562159fe9c8d2abea57`.

## 11. Limitações encontradas

- `embed_url` só existe na resposta runtime da Fonte A; **não** é persistido no Room (esquema inalterado). A disponibilidade do embed depende do cache de 5 min da API.
- Muitos provedores web impõem restrições de incorporação/região/DRM que **não** são contornadas — nesses casos o canal segue no catálogo, mas pode não reproduzir (indisponível explícito).
- Sem stream direto, a qualidade/órgão de controle do embed é do provedor (não há integração de EPG/legendas).
- `M3uCatalogGeneratorTest`: OOM pré-existente, não modificado.

## 12. Pendências para testes reais no emulador

- Validar em Android TV (x86) a reprodução web de embeds autorizados, o encerramento de áudio na troca e a ausência de múltiplas WebViews.
- Confirmar foco/controle remoto com WebView ativa (D-Pad, OK, BACK, digitação numérica, overlays).
- Confirmar transições DIRECT_STREAM↔WEB_EMBED e fallback ExoPlayer→WebView com falha real.
- Revalidar favoritos/histórico e canais 8 (TV Centro-Sul) e 9 (TV Litoral RN).

> Testes, build e números acima são resultado de execução, não de inspeção. Reprodução real **não** foi validada. Nada foi commitado/pushado.
