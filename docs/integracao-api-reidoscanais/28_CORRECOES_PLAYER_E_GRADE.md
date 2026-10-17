# 28 — CORREÇÕES DO PLAYER E DA GRADE (ETAPA 14)

> Etapa 14 §04/§06/§07/§09. Correções aplicadas **somente** onde a causa foi
> identificada e está sob controle do aplicativo. Nenhuma alteração aleatória de rede
> ou renderização; nenhum contorno de DRM/TLS/incorporação; schema Room inalterado.

## 1. Correções aplicadas

### 1.1 Áudio sem vídeo — nunca declarar PLAYING sem quadro renderizado
- `PlaybackState.kt:fromExo(...)`: novo parâmetro `videoFrameTimedOut`. `PLAYING` exige
  agora `hasVideoTrack && hasRenderedFirstFrame`. Enquanto o primeiro quadro não chega
  existe uma **janela de tolerância** (`videoFrameTimedOut=false` → ainda `PLAYING`, para
  não piscar); após o tempo limite vira `AUDIO_ONLY` (a UI mostra "SEM IMAGEM").
- `ExoPlayerManager.kt`: `startFirstFrameWatchdog()` agenda `FIRST_FRAME_TIMEOUT_MS=3500`
  ms; `onRenderedFirstFrame()` cancela o watchdog; `clear()`/`release()` cancelam tudo.
- Resultado: um canal que só produz áudio passa a ser **identificado**, em vez de tela
  preta silenciosa.

### 1.2 Áudio sem vídeo — tela cheia do WebView
- `WebPlayer.kt`: o `AndroidView` agora é um `FrameLayout` que contém a WebView. Em
  `onShowCustomView` a View entregue pelo Chromium é **anexada** (MATCH_PARENT,
  `bringToFront`); em `onHideCustomView`/troca de URL ela é removida (`exitFullscreen`,
  preservando a WebView inline). Resolve o caso em que o player escondia o vídeo inline
  esperando a tela cheia — sobrava só o áudio.

### 1.3 Demora na conexão
- `PlayerViewModel.playChannel`: streams e embeds resolvidos **em paralelo**
  (`coroutineScope { async … }`) antes de montar o plano, em vez de sequencialmente.
- `ChannelRepository.seedStreamsCache(...)`: a lista completa de `streams.json` obtida no
  assistente/sintonia passa a **semear o cache** de streams, eliminando o segundo
  download na primeira troca de canal (`STREAMS_TTL_MS` preservado).

### 1.4 Estabilidade da fonte (Band / TV Gazeta) — §05
- Novo `util/StreamPreference.kt` (puro e testável). Ordem dentro do **mesmo** canal:
  1) HTTPS+domínio → HTTPS+IP → HTTP+domínio → HTTP+IP; 2) qualidade (resolução menor
  primeiro, agora com rótulos numéricos `480p…2160p`); 3) URL (determinismo).
- `ChannelRepository.getStreamsForChannel` passou a usar `StreamPreference.order(...)`.
- Efeito no dado real: `Band.br` passa a iniciar por `https://media.cdntvms.com.br/…`
  (CDN por domínio) em vez de `http://170.84.165.204/…`. Emissora, número, nome, logo e
  EPG **inalterados** (fixos 6 e 5 preservados).

### 1.5 Ordem dos canais — §06
- `SourcePreferenceRegistry.nameOrderKey(...)` (chave canônica: minúscula, sem acentos,
  alfanumérica) e `ChannelNumbering.orderGrade` passa a ordenar o bloco destacado por
  essa chave + `channelId` (desempate determinístico).
- Auditoria A→D: a ordem é gerada por `orderGrade`, persistida exatamente como gerada
  (`insertAll`) e lida por número (`getAll` `ORDER BY number`); a UI usa a mesma lista
  (`allChannels`), então **A = B = C = D**.
- Bloco destacado resultante (72 citados, 10–81) validado: ordem canônica
  `adultswim, ae, amc, …, woohoo, xsports`.

## 2. Conflitos/diferenças documentados
- **Numeração anterior × bloco destacado**: como os 72 citados recebem 10–81 contíguos,
  os canais não citados deslocam de posição. Não é possível preservar a numeração
  anterior **e** manter o bloco destacado contíguo. A preservação feita é do **estado do
  usuário** (favoritos/último canal) por `channelId` (§09), não do número cru.
- **Gazeta**: só existem fontes IP-cru/HTTP; o app escolhe de forma determinística, mas
  não pode garantir estabilidade de uma fonte instável (limite externo).

## 3. Testes
- `StreamPreferenceTest` (4): HTTPS-domínio antes de HTTP-IP; ranks; qualidade numérica;
  determinismo.
- `PlaybackStateTest` (+3): tolerância de primeiro quadro; timeout → `AUDIO_ONLY`.
- `ChannelNumberingFeaturedTest` (+2): bloco por chave canônica; ordem gerada = persistida.
- Suíte `util.*` + `reidoscanais.*`: **102 testes aprovados** (0 falhas).
- Não se declara reprodução real de canais de terceiros — os testes cobrem decisões
  determinísticas, não o runtime da TV.

## 4. Compilação
- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, **17.930.357 bytes**,
  sha256 `2bb494da416b1dca72dfad2595685cb92c5a8c987959e1bbf5c6caf91b485251`.
- Sem build Release; sem instalação na TV; sem commit; sem push.

## 5. Preservações
Fixos 1–9 (TV Centro-Sul no 8, TV Litoral RN no 9), catálogo RDC completo, iptv-org,
ordem da Etapa 13, favoritos, histórico, último canal, EPG, assistente, controle remoto,
qualidade adaptativa (ABR/QualityPolicy) e arquitetura híbrida. Listas BR antigas
permanecem desativadas; schema Room intacto.

## 6. Riscos e plano de validação na TV
- Validar em TV física: "SEM IMAGEM" após ~3,5 s; vídeo em tela cheia dos embeds;
  `TuningDiag ttf`; Band/Gazeta; ordem 10–81; troca rápida de canais.
- Se persistir áudio sem imagem em streams diretos, hipótese a testar: superfície
  (avaliar TextureView) — **somente com evidência de runtime**.
- Logs: ver doc 27 §5 (ADB indisponível nesta execução).
