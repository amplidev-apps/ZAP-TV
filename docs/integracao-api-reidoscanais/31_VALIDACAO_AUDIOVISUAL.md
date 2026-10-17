# 31 — VALIDAÇÃO AUDIOVISUAL (ETAPA 15)

> Prioridade absoluta da Etapa 15: **reprodução com imagem**. Áudio sem vídeo **não** é
> reprodução audiovisual validada. Este documento separa explicitamente o que foi
> **implementado**, o que foi **aprovado em teste automatizado**, o que dependia de
> **dispositivo físico** e o que permanece **pendente por hardware/fonte externa**.

## 1. Comportamentos implementados (código)

### 1.1 ExoPlayer / fonte direta
- `PlaybackState.kt:fromExo(...)` mantém (Etapa 14) `PLAYING` **exigindo**
  `hasRenderedFirstFrame`; sem quadro após a tolerância → `AUDIO_ONLY` (nunca PLAYING).
- `ExoPlayerManager`: watchdogs em duas janelas:
  - `FIRST_FRAME_TIMEOUT_MS = 3500` → marca `videoFrameTimedOut` e publica `AUDIO_ONLY`
    (indicador "SEM IMAGEM").
  - `VIDEO_FALLBACK_MS = 8000` (mais tolerante, "respeitando conexões lentas") → se a
    trilha de vídeo foi declarada e o quadro nunca renderizou, dispara
    `onVideoUnavailable` para o ViewModel tentar outra fonte.
- `onRenderedFirstFrame()` cancela os dois watchdogs e registra "reprodução confirmada".
- `onPlayerError` classifica a falha para diagnóstico (`codec`, `timeout`, `conexao`,
  `http`, `outro`) **sem** registrar URLs/credenciais.
- `clear()`/`release()` cancelam ambos os watchdogs (evita callback após troca de canal).

### 1.2 Fallback audiovisual (Etapa 15 §03/§04)
- Novo `util/VideoFallbackPolicy.kt` (puro): decide `Next(index)` ou `KeepAudioOnly`.
  Nunca repete a mesma fonte, nunca ultrapassa a lista → **sem loops**.
- `PlayerViewModel.onVideoUnavailable()`:
  - há outra fonte → avança para a próxima tentativa (DIRECT de outra afiliada **ou**
    WEB_EMBED autorizado);
  - plano esgotado → mantém o canal em `DIRECT_STREAM` + `AUDIO_ONLY`
    (`noSignal = false`), ou seja **não** declara "SEM SINAL" nem remove o canal.
- O áudio da fonte anterior é encerrado ao trocar de mecanismo: `PlayerScreen` limpa o
  ExoPlayer quando `mechanism != DIRECT_STREAM` e o `WebPlayer` é descartado (destroy) ao
  sair — nunca há duas reproduções simultâneas.

### 1.3 Android WebView
- Tela cheia preservada (Etapa 14): `FrameLayout` + `onShowCustomView`/`onHideCustomView`.
- Novo `onRenderProcessGone` → falha do renderizador é tratada como **falha real** e escala
  para a próxima fonte (sem contornar restrições do player incorporado).
- **Documento carregado NÃO é confirmação audiovisual**: `setWebLoading(false)` passou a
  registrar "reprodução não verificável", nunca "confirmada". A WebView não expõe evento
  confiável de primeiro quadro sem contornar restrições — não declaramos sucesso.

### 1.4 Diagnóstico para TV física (Etapa 15 §06) — tag `AvDiag` (só em DEBUG)
Eventos distintos e sem dados sensíveis: `conexao host=<host>`, `audio iniciado`,
`primeiro quadro renderizado -> reproducao confirmada`, `video interrompido -> fallback por
falha de video`, `falha tipo=<codec|timeout|conexao|http|outro>`, `falha do renderizador web`,
`fallback iniciado`, `fonte alternativa selecionada`, `reproducao confirmada`,
`reproducao NAO verificavel`.

## 2. Testes automatizados aprovados

`bash ./gradlew :app:testDebugUnitTest` (pacotes `util.*` + `reidoscanais.*`):
**112 testes, 0 falhas, 0 erros** (Etapa 14: 102). Novos na Etapa 15:

| Requisito | Teste |
|---|---|
| ExoPlayer áudio e vídeo | `PlaybackStateTest.ready_com_video_e_playing` |
| ExoPlayer áudio sem quadros | `PlaybackStateTest.ready_video_declarado_sem_frame_apos_timeout_e_audio_somente` |
| Falha de decoder | `ExoPlayerManager.classifyError` (compilação/uso; ver §4) |
| Fallback após falha de vídeo | `VideoFallbackPolicyTest.avanca_para_a_proxima_fonte_quando_existe` |
| Troca ExoPlayer → WebView | `VideoFallbackPolicyTest.troca_de_exoplayer_para_webview_apos_falha_de_video` |
| Encerramento do áudio anterior | via plano/estado (troca limpa ExoPlayer) + §1.2 |
| Recuperação sem loops | `VideoFallbackPolicyTest.nao_gera_loop_indices_estritamente_crescentes` |
| Sem vídeo, plano esgotado | `VideoFallbackPolicyTest.mantem_audio_quando_plano_esgotado` |
| Regionalização Globo | `RegionalSourcePlannerTest.globo_ordem_uf_sp_rj_cabeca_outras` (+2) |
| IPTV-ORG Band/Record/Gazeta | `RegionalSourcePlannerTest.band_record_gazeta_priorizam_iptv_org_antes_do_embed` |
| Canais 8 e 9 | `ChannelNumberingRegionalTest` / `posicoes_8_e_9_nao_sao_regionalizadas` |

Nenhum teste **simula** sucesso de reprodução: os testes de áudio-sem-vídeo afirmam
`AUDIO_ONLY`/fallback, e a confirmação audiovisual só existe por evento real
(`onRenderedFirstFrame`), não reproduzível em teste unitário.

## 3. Reprodução confirmada em dispositivo real

**Nenhuma** nesta execução. Não há `adb`/Android TV física conectada (`adb devices` vazio).
Por isso **não** declaramos reprodução audiovisual confirmada para nenhum canal real. A
validação em TV física continua pendente (aguardando o aparelho).

## 4. Pendências dependentes de hardware ou fontes externas

- **Primeiro quadro real / codec / superfície**: só verificável na TV. Roteiro de logcat
  (`adb logcat -s AvDiag ZapPlayer RegionalDiag TuningDiag`) no doc 27 §5.
- **Falha de decoder**: classificada no código, mas não exercitável em teste unitário.
- **WebView/System WebView**: renderização, tela cheia e composição com Compose dependem do
  WebView do aparelho; validar na TV (inclusive `onRenderProcessGone`).
- **Gazeta**: fonte do IPTV-ORG é IP cru/HTTP — instabilidade da **origem**, não do app.
- **Hipótese não aplicada sem evidência** (Etapa 14): trocar `SurfaceView`→`TextureView`.

## 5. Canal permanece acessível

Quando nenhuma fonte entrega vídeo, o canal **não** é removido da grade e **não** vira "SEM
SINAL": fica em `AUDIO_ONLY` ("SEM IMAGEM"), acessível e com o áudio, sem tela preta
silenciosa nem alternância infinita.

## 6. Artefato

- `:app:assembleDebug` → **BUILD SUCCESSFUL**.
- APK: `app/build/outputs/apk/debug/app-debug.apk`
  - bytes: **17.934.090**
  - sha256: `0fe4a403825ab4abb4c683835a270eff47719350fff6767b0e178d979e81d6a0`
- Nenhum commit/push; nenhuma instalação automática; assinatura e schema Room inalterados.
