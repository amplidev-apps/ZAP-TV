# 20 — CORREÇÕES DO TESTE EM TV FÍSICA

> Etapa 10 (v2). Correções dos problemas observados no **teste em Android TV física**
> (Etapa 10 original): sintonia incompleta, numeração incorreta, demora para conectar,
> áudio sem imagem, falso "sem sinal" e instabilidade em redes fracas.
> Sem commit/push, sem release, APK não instalado.

## 1. Causas identificadas (auditoria)

| # | Sintoma | Causa raiz |
|---|---------|-----------|
| P01 | "poucos canais" na TV | o indicador de sintonia exibia apenas a amostra de verificação (fixos + até 80 canais BR), não o total persistido |
| P02 | numeração fora de ordem | `buildNumberedChannels` misturava Fonte A (RDC) e iptv-org num único bloco, sem blocos definidos |
| P03 | demora a cada troca de canal | `getStreamsForChannel` baixava `streams.json` (iptv-org) a **cada** sintonia, sem cache |
| P04 | estado de reprodução opaco | não havia modelo observável (conectando/buffering/áudio-só/erro) |
| P05 | "SEM SINAL" com player ainda tentando | o esgotamento de streams marcava indisponibilidade mesmo com a recuperação automática ativa |
| P07 | sem adaptação de qualidade | `ExoPlayerManager` sem `TrackSelector` configurável por perfil |

## 2. P01/P04 — contagem real e métricas por fonte (`ChannelRepository`)

- `syncChannelsWithVerification` agora reporta, ao **final**, a contagem efetiva de canais
  adicionados (`onProgress("Sintonia concluída", total, total, total)`), e não apenas a
  amostra verificada.
- `assembleRdcPrimaryCatalog` passou a retornar `AssembledCatalog(entities, rdcDiscovered,
  rdcRepresented, rdcCatalogOnly)`.
- `logCatalogMetrics` (somente `BuildConfig.DEBUG`) registra `uf`, `total`, `fixos`, `adulto`,
  `rdcDescobertos`, `rdcRepresentados` e `rdcSomenteCatalogo` — permite explicar a diferença
  entre "canais descobertos" e "canais com fonte reproduzível".
- `SetupScreen`: rótulo alterado para **"Canais adicionados: N"**, refletindo o total real.

A verificação limitada (≤80 canais / 20 s) **permanece apenas informativa** e nunca remove
canais da grade nem bloqueia o setup.

## 3. P02 — ordem definitiva da grade (`ChannelNumbering.orderGrade`)

Blocos determinísticos:

1. **Fixos 1-9** — preservados exatamente como resolvidos (8 = TV Centro Sul, 9 = TV Litoral RN).
2. **Fonte A (RDC)** — canais representados pela API (`rdcIds = metadataById.keys`).
3. **Demais (iptv-org / internacionais / complementares)**.

Renumera o restante a partir de 10, sem lacunas e sem tocar as posições fixas. Aplicado em
`assembleRdcPrimaryCatalog` antes de `appendAdultChannels`. Testes: `ChannelNumberingOrderTest`.

## 4. P03 — cache de streams (`ChannelRepository`)

- `getStreamsCached()` mantém `streams.json` em memória (`STREAMS_TTL_MS = 10 min`).
- `getStreamsForChannel` usa o cache; `syncChannelsWithVerification` popula o cache após a
  sincronização, de modo que a **primeira troca de canal já é instantânea**.
- M3U e `customStreams` continuam anexados por canal (sem alteração).

## 5. P05/P06 — modelo de estado e fim do falso "SEM SINAL"

- Novo `util/PlaybackState.kt` (`PlaybackState` + `PlaybackStates.fromExo`): `IDLE`,
  `CONNECTING`, `BUFFERING`, `PLAYING`, `AUDIO_ONLY`, `RETRYING`, `UNAVAILABLE`, `ERROR`,
  `UNKNOWN_PLAYBACK_STATE`.
- `ExoPlayerManager` expõe `onStateChanged` e deriva o estado de `onPlaybackStateChanged`,
  `onIsPlayingChanged`, `onVideoSizeChanged`, `onRenderedFirstFrame` e `onTracksChanged`.
  Estado transitório usa `RETRYING`/`CONNECTING` em vez de indisponibilidade.
- `PlayerViewModel.nextStreamOnError`: quando as fontes falham **mas** a recuperação
  automática continua, reporta `isReconnecting = true` (`RETRYING`) — **nunca** `noSignal`.
- `noSignal` fica reservado a `PlaybackMechanism.UNAVAILABLE` (nenhuma fonte).
- **Áudio sem imagem** (`AUDIO_ONLY`): `PlayerScreen` mostra o aviso "SEM IMAGEM — apenas
  áudio", distinto de "SEM SINAL".

## 6. P07 — configuração do player (resumo)

Detalhado no doc **21**. Em síntese: `DefaultTrackSelector` com ABR nativo preservado e teto
por perfil de qualidade (`PlaybackQualityProfile`), aplicável em tempo real via
`setQualityProfile`.

## 7. Arquivos alterados

| Arquivo | Alteração |
|---------|-----------|
| `data/repository/ChannelRepository.kt` | cache de streams, `orderGrade` na montagem, `AssembledCatalog`, `logCatalogMetrics`, progresso final real |
| `util/ChannelNumbering.kt` | `orderGrade(entities, rdcIds)` |
| `util/PlaybackState.kt` | **novo** — estados de reprodução |
| `util/PlaybackQualityProfile.kt` | **novo** — perfis e `QualityPolicy` (doc 21) |
| `presentation/player/ExoPlayerManager.kt` | TrackSelector, perfil de qualidade, `onStateChanged` |
| `presentation/player/PlayerViewModel.kt` | `playbackState`, `onPlaybackState`, correção do falso "sem sinal" |
| `presentation/player/PlayerScreen.kt` | overlay de áudio-só; ligação de `onStateChanged` |
| `presentation/setup/SetupScreen.kt` | rótulo "Canais adicionados" |
| Testes | `ChannelNumberingOrderTest`, `QualityPolicyTest`, `PlaybackStateTest` |

## 8. Verificação

- `:app:testDebugUnitTest` (JDK 17) — **verde** (util + reidoscanais; `M3uCatalogGeneratorTest`
  permanece OOM, pré-existente e não modificado).
- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- APK: `app/build/outputs/apk/debug/app-debug.apk` — 17.907.196 bytes,
  sha256 `3afc92d37bd4d4fa976b5a1ac76c3c5dc68e34c683fa30b9b568da2d6de18fea`.

## 9. Limitações / não validado

- Reprodução real (áudio/vídeo/áudio-só) exige **Android TV/emulador** — não validada aqui.
- Controle remoto, overlays e troca de canal só testáveis em runtime.
- Tempos de conexão dependem de rede/CDN da emissora (o watchdog reconecta após 5 s de
  buffering ou 3 s de posição parada).
