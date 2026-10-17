# 43 — CORREÇÕES DA ETAPA 20

> Etapa 20 — correção integrada das falhas remanescentes de reprodução. Somente
> correções **internas demonstráveis**; nenhuma fonte externa foi contornada
> (sem DRM, sem autenticação, sem cliques sintéticos). Catálogo, ledger (Etapa 18),
> numeração e fixos 1–9 **inalterados**. Sem commit/push, sem release, APK não instalado.

## 1. O que foi corrigido (e por quê)

### C1 — Watchdog do ExoPlayer prendia a fonte para sempre (P: “canais presos”, Band, longas esperas)
**Antes**: em `ExoPlayerManager.startWatchdog`, buffering > 5 s (ou travamento de posição)
chamava `playCurrentStream(reconnecting = true)` **na mesma fonte**, sem incrementar
`transientRetries`. Um stream que **conectava mas nunca enviava dados** (ex.: `Band.br`
iptv-org, HTTP/IP) entrava em laço infinito de reconexão e **nunca avançava** para o embed
da Fonte A nem declarava indisponibilidade.

**Depois** (`ExoPlayerManager.kt`):
- novo contador `watchdogReconnects` e constante `MAX_WATCHDOG_RECONNECTS = 2`;
- após 2 reconexões sem progresso → `tryNextStream()` (escala stream/fonte, dispara
  fallback → `advanceAttempt` no ViewModel);
- zera em `playMulti`/`clear`/`release`/`tryNextStream` e em `STATE_READY`.

Efeito: Band e demais canais com stream iptv morto **passam** a chegar à próxima fonte
(incluindo o embed Clappr) em vez de travar. Não repete fonte indefinidamente.

### C2 — Evidência de mídia exigia apenas “requisição”, não sucesso (P: embed preso)
**Antes** (Etapa 19): qualquer requisição `.m3u8/.ts/...` cancelava o guarda de mídia — uma
playlist que responde **404/500** mantinha a sessão presa.

**Depois** (`WebSessionPolicy.kt` + `WebPlayer.kt`):
- novos contadores `mediaRequests` / `mediaErrors` (erros HTTP de mídia via
  `onReceivedHttpError`, agora contabilizados também fora do DEBUG);
- `WebSessionPolicy.mediaConfirmed(req, err) = req > err`;
- `decideMediaTimeout(pageLoaded, mediaConfirmed)` — falha só sem evidência ativa.
  Ex.: 1 req / 1 err (playlist morta) → **sem** evidência → fallback; 3 req / 1 err → ativo.

### C4 — Loop de recarga por redirecionamento (P06 “cai após iniciar”)
**Antes** (`WebPlayer.kt`): a reavaliação usava `wv.url != url`. Se a origem
**redireciona** (ex.: `localhost.tattoo`, redirects de CDN), `wv.url` nunca volta a ser a URL
solicitada → a cada recomposição o app **recarregava** a página, reiniciando a mídia
(queda aparente).

**Depois**: rastreia `loadedUrl` (URL **solicitada**) e só recarrega quando o parâmetro
`url` muda de fato; redirecionamentos não disparam recarga.

### C3 — Ordem do fallback da Globo (Etapa 20 §05)
**Antes**: `UF → SP → RJ → cabeça nacional → demais afiliadas`.
**Depois** (`RegionalSourcePlanner.kt`, rede Globo):
`UF → SP → RJ → demais afiliadas → cabeça nacional (fonte genérica) → sobras`.
A fonte nacional genérica **não** precede mais as demais afiliadas. As demais redes
mantêm `UF → cabeça → SP/RJ → outras`.

## 2. Diagnóstico central (ver doc 44)

- **Globo** (todas as 27 regionais) → `rdcanais.org/globoXX` → iframe
  `https://localhost.tattoo/globo/player.php` → **JW Player 8.38.3** + API oficial
  `https://playback.video.globo.com/v5/video-session` com `token` embutido (provável DRM/
  geo). É tecnologia **diferente** dos canais que funcionam → limite externo.
- **GNT, SporTV, Premiere, Gloob, HBO, Cartoon** → mesmo host Clappr 0.3.13 + hls.js +
  p2p-media-loader (alguns em `/embed/`, outros em `/play/`). Premiere/Gloob/HBO/Cartoon
  funcionam; GNT/SporTV não → a diferença **não** está no app (mesmo caminho), e sim na
  fonte/disponibilidade/codec.
- **Band** → iptv-org (`http://170.84.165.204/BAND_HD/…`, `media.cdntvms.com.br/…`) antes
  do embed Clappr `/play/band.html`. C1 garante que o embed seja alcançado.

## 3. Autoplay (Etapa 20 §10)

`WebPlayer` já define `mediaPlaybackRequiresUserGesture = false`. Os embeds Clappr usam
`autoPlay:true` e iniciam sozinhos (confirmado por Premiere/Gloob/HBO). Para o JW Player
da Globo e players que exijam gesto/DRM, **não** injetamos scripts nem cliques sintéticos
(proibido). Limitação documentada.

## 4. Preservação (Etapa 20 §03)

Nenhuma alteração em: ledger `approved_catalog_ledger_v1`, `approved_catalog_seed.json`,
`ChannelNumbering`, `ChannelRepository`, numeração, fixos 1–9, favoritos, histórico, EPG,
Telecine, TV Cultura/Cultura Fast. Premiere/Gloob/HBO/Cartoon não tiveram caminho alterado.

## 5. Arquivos modificados

- `app/src/main/java/com/zap/tv/presentation/player/ExoPlayerManager.kt` (C1)
- `app/src/main/java/com/zap/tv/presentation/player/WebPlayer.kt` (C2, C4)
- `app/src/main/java/com/zap/tv/util/WebSessionPolicy.kt` (C2)
- `app/src/main/java/com/zap/tv/util/RegionalSourcePlanner.kt` (C3)
- `app/src/test/java/com/zap/tv/util/WebSessionPolicyTest.kt` (C2, + testes)
- `app/src/test/java/com/zap/tv/util/RegionalSourcePlannerTest.kt` (C3, + testes)

## 6. Testes e build

- Suíte `util.*` + `reidoscanais.*`: **149 testes, 0 falhas** (147 → 149).
- Novos: `evidencia_de_midia_requer_requisicao_sem_erro`,
  `globo_cabeca_nacional_nao_precede_demais_afiliadas`.
- APK Debug: `app/build/outputs/apk/debug/app-debug.apk`
  - **17.947.134 bytes**
  - sha256 `e5c0b4885dc90d30417e1247ea96d46f9e1b66ed64daeba0d9a56c51d4268443`

## 7. Limitações (não validado em dispositivo)

Sem ADB/TV física. Não se afirma reprodução: só a ordem/recuperação interna. Casos de borda:
continuidade de mídia (mídia requisitada e depois parada) e DRM/geo da Globo permanecem não
observáveis. Roteiro de validação em doc 45.
