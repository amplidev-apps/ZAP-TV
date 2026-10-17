# 27 — DIAGNÓSTICO DA REPRODUÇÃO NA TV (ETAPA 14)

> Etapa 14 §01/§02/§03/§07. Investigação das causas de "áudio sem vídeo", demora na
> conexão e ordenação incorreta. **Nenhum dispositivo ADB estava conectado** durante
> esta execução (`adb` não instalado; `adb devices` vazio), então as conclusões vêm de
> **auditoria de código + dados reais do iptv-org/Rei dos Canais**. As causas que
> dependem de runtime estão marcadas como *hipótese a validar na TV*.

## 1. Causas confirmadas (por código)

### 1.1 "PLAYING" declarado sem vídeo renderizado — áudio sem imagem sem aviso
`PlaybackStates.fromExo` declarava `PLAYING` sempre que uma **trilha de vídeo era
declarada** (`hasVideoTrack`), **sem exigir o primeiro quadro** (`renderedFirstFrame`).
Quando o codec/variante é anexado mas o vídeo nunca renderiza (decoder ausente, codec
não suportado pelo aparelho, falha de superfície), o áudio toca e a tela fica preta
**sem qualquer indicação** — a UI só mostrava "SEM IMAGEM" quando *não havia* trilha de
vídeo. Essa é a explicação técnica mais provável para "alguns conteúdos reproduzem áudio
sem imagem".

### 1.2 WebView descartava o vídeo em tela cheia
`WebPlayer` implementava `WebChromeClient` mas **ignorava** a View entregue por
`onShowCustomView`. Muitos players embarcados escondem o `<video>` inline e chamam
`requestFullscreen()`: com a View descartada, sobra o áudio e a tela fica preta. Isso é
uma causa direta de áudio sem imagem **nos embeds** (`https://rdcanais.org/<slug>`).

### 1.3 Concatenação de esperas na sintonia (demora)
`PlayerViewModel.playChannel` buscava **sequencialmente** `getStreamsForChannel(...)` e
depois `getEmbedsForChannel(...)`, somando o tempo de rede das duas fontes antes da
primeira tentativa de mídia. Além disso, `getStreamsCached()` baixava `streams.json`
**de novo** na primeira troca de canal, mesmo já tendo baixado a lista completa durante
o assistente.

### 1.4 Ordenação não canônica do bloco destacado
O bloco dos canais citados ordenava por `channelName.lowercase()`, sensível a acento e
caixa; a chave de exibição podia divergir da marca (nome iptv vs. nome RDC).

### 1.5 Fontes instáveis primeiro (Band / TV Gazeta)
`getStreamsForChannel` ordenava por um pequeno mapa de qualidade (`SD`→`HD`→…) que **não
reconhece rótulos numéricos** do iptv-org ("720p", "576p"): todos caíam no mesmo peso e a
ordem da API era preservada. Resultado (dado real auditado):
- `Band.br`: `http://170.84.165.204/BAND_HD` (IP cru HTTP) **antes** de
  `https://media.cdntvms.com.br/band_sat` (CDN oficial por domínio/HTTPS).
- `TVGazeta.br`: 6 fontes, **todas IPs crus em HTTP** (`45.162.64.114`, `168.197.104.22`,
  `170.84.165.204`…), sem alternativa por domínio na própria emissora.

## 2. Hipóteses descartadas

- **Hardware acceleration desligado**: `android:hardwareAccelerated` não é fixado, mas o
  padrão para `targetSdk >= 14` é **ligado**; o `AndroidManifest` não desativa nada.
- **TLS/mixed content**: `MIXED_CONTENT_NEVER_ALLOW` + cancelamento em erro de
  certificado estão corretos; não é a causa.
- **Foco da WebView sequestrando o controle**: `descendantFocusability` e
  `isFocusable=false` estão corretos.
- **Vazamento/duplicidade de players**: `DisposableEffect` destrói a WebView e
  `ExoPlayerManager.release()` libera o player; a troca é serializada por `tuneGeneration`.

## 3. Limitações da fonte externa (não são bugs do app)

- Emissoras **sem** fonte reproduzível no iptv-org para a própria marca regional
  (ex.: várias afiliadas Band/Gazeta regionais têm **0 streams**). O app não deve inventar
  nem trocar de emissora.
- Alguns codecs podem não existir no TV Box; nesses casos é correto declarar
  áudio-sem-imagem **desde que a UI informe** (ver correção §1.1).

## 4. Diferenças entre navegador e WebView

| Aspecto | Navegador desktop | Android WebView |
|---|---|---|
| Tela cheia | Elemento Fullscreen API do browser | Exige `onShowCustomView` + anexar a View |
| Autoplay | Gesture-gated | `mediaPlaybackRequiresUserGesture=false` |
| Codecs | Pacote do SO/navegador | Subconjunto do aparelho (MediaCodec) |
| DRM | Widevine do navegador | Widevine do aparelho (não usado aqui) |

## 5. Coleta de logs na TV física (instruções — ADB indisponível nesta execução)

1. No PC: instalar o platform-tools (`adb`) e habilitar **Depuração USB** na TV.
2. `adb devices` deve listar o aparelho como `device` (autorizado).
3. Capturar (apenas DEBUG; apps em produção podem não expor os logs):
   ```
   adb logcat -c
   adb logcat -v time | grep -Ei "ZapPlayer|ZapDiag|RegionalDiag|TuningDiag|CatalogSync|chromium|MediaCodec|Media3|AndroidRuntime|SurfaceView|TextureView|libEGL|Gralloc"
   ```
4. Reproduzir o defeito (canal com áudio sem imagem; Band; Gazeta; trocar de canal).
5. Salvar o recorte e enviar — **sem credenciais/cookies/tokens**; hosts são registrados,
   URLs/credenciais completas não.
6. Não alterar configurações de segurança do aparelho.

Sinais a procurar: `MediaCodec` `c2.*` falhando/`omx`; `decoder init failed`;
`onPlayerError` com `ERROR_CODE_DECODER_*`; `first-frame timeout -> AUDIO_ONLY`;
`state=... video=... frame=... timedOut=...`.

## 6. Plano de validação na TV física

1. Confirmar que canais com áudio sem imagem agora exibem "SEM IMAGEM" (após ~3,5 s).
2. Confirmar que embeds que entram em tela cheia exibem vídeo (não só áudio).
3. Medir tempo até o primeiro quadro (log `TuningDiag ttf=`) antes/depois em 10 canais.
4. Verificar Band (deve escolher `media.cdntvms.com.br`) e Gazeta (ordem determinística).
5. Validar ordem 10–81 do bloco destacado (alfabética canônica) e fixos 1–9.
6. Trocar de canal rapidamente (10×) sem áudio duplicado/callback obsoleto.

## 7. Riscos ainda existentes

- Superfície de vídeo do ExoPlayer em alguns aparelhos (SurfaceView) pode exigir
  TextureView — *não alterado sem evidência de runtime*.
- Fontes IP-cru só-HTTP da Gazeta continuam instáveis por natureza (limite da fonte).
- Sem device, não foi possível confirmar renderização real (não se declara sucesso).
