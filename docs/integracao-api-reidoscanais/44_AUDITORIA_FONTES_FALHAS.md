# 44 — AUDITORIA DE FONTES E FALHAS (ETAPA 20)

> Diagnóstico individual das falhas relatadas no teste físico da Etapa 19, cruzado com
> as fontes reais (Fonte A / Rei dos Canais) e com `docs/novas regras.md`. Não afirma
> reprodução sem evidência em dispositivo.

## 1. Descoberta de tecnologia (embeds)

Consulta direta às páginas de embed (com `Referer: https://rdcanais.org/<slug>`) mostrou
**duas tecnologias distintas**:

| Canal | Página | Player interno | Observação |
|---|---|---|---|
| Globo (todas as 27 regionais) | `rdcanais.org/globoXX` | **JW Player 8.38.3** (`ssl.p.jwpcdn.com/.../8.38.3`) | API oficial `https://playback.video.globo.com/v5/video-session` + `token` embutido |
| HBO, HBO 2, Band, (demais `/play/`) | `rdcanais.org/<slug>` | Clappr 0.3.13 + hls.js + p2p-media-loader | `window.STREAM_URLS` |
| GNT, SporTV, SporTV2/3, Premiere*, Gloob, Cartoon, etc. (`/embed/`) | `rdcanais.org/<slug>` | Clappr 0.3.13 + hls.js + p2p-media-loader | bundle ~77 KB |

O iframe é sempre **outra origem** (`dadinhodetapioca.fun` ou `localhost.tattoo`) →
same-origin impede ler o `<video>`.

## 2. Diagnóstico da Globo (P01/P02)

`rdcanais.org/globosp` embute
`https://localhost.tattoo/globo/player.php?id=…&lat=-23.55&long=-46.63`, que carrega
`window.__PLAYER_CFG = { id, lat, long, token, endpoint: "https://playback.video.globo.com/v5/video-session" }`
e um `player.js` **ofuscado** (anti-tamper) do JW Player.

- É a **API oficial da Globo**, com token e coordenadas — padrão de **DRM/geo-restrição**.
- Não injetamos script, não extraímos token, não contornamos DRM (proibido).
- Se a sessão oficial falhar (geo/DRM/WebView sem CDM), a página mostra um **card de erro**
  e **não requisita mídia** → o guarda de mídia (C2) expira e o app **avança** para a
  próxima fonte (SP → RJ → demais) — comportamento pretendido.
- Remanescente: enquanto a fonte oficial responder playlist (200) sem renderizar, o app
  não consegue distinguir isso de reprodução (limite de iframe de outra origem).

**Ordem corrigida (C3)**: UF → SP → RJ → demais afiliadas → cabeça nacional → indisponível.

## 3. GNT e SporTV (P03/P04) — comparação com os funcionais

- **Premiere 1/2/3** (funcionam): `/embed/premiereXX.html`, Clappr + hls.js, **premium +
  alternativa** (2 embeds).
- **SporTV 1/2/3** (falham): `/embed/sportvX.html`, **mesma tecnologia**, **premium +
  alternativa** (2 embeds).
- **GNT** (falha): `/embed/gnt.html`, **mesma tecnologia**, **somente 1 embed** (premium).
- **Gloob/HBO/Cartoon** (funcionam): mesmo host; Gloob/Cartoon `/embed/`, HBO `/play/`.

Como a **tecnologia e o caminho do app são idênticos** para Premiere (ok) e SporTV/GNT
(falha), a diferença **não é do ZAP-TV**: é a própria fonte/disponibilidade ou o
**codec/perfil** não decodificável pelo System WebView da TV. Correções internas possíveis
já aplicadas: (C2) não tratar playlist que falha como mídia ativa → o app tenta a
**alternativa** do SporTV; (C1) não travar em stream iptv morto.

## 4. Band (P07)

`Band.br` (iptv-org) tem 2 streams — `http://170.84.165.204/BAND_HD/index.m3u8` (HTTP/IP) e
`https://media.cdntvms.com.br/band_sat/index.m3u8`. Band é rede fixa e **mantém IPTV-ORG
como primário** (Etapa 15 §02) — não foi reordenado. O embed `/play/band.html` (Clappr,
igual ao HBO que funciona) é a fonte seguinte. Antes, o watchdog do Exo **reconectava o
stream iptv morto para sempre** (C1); agora escala até o embed. Se a fonte externa estiver
fora do ar, documenta-se o impedimento — não se substitui a Band por outra emissora.

## 5. Matriz `novas regras.md` (status por canal)

`SourcePreferenceRegistry` cobre **81** entradas: **72** `RDC_EMBED_PRIMARY`, 7
`KEEP_EXISTING`, 2 pendentes. Verificação automática: **todos os 72 slugs preferidos existem
como embed** da Fonte A (0 ausentes). Amostra:

| Canal | Pref | Embeds | Diagnóstico |
|---|---|---|---|
| Premiere 1/2/3 | premium | 2 | funcional (teste físico) — preservar |
| Gloob, HBO, Cartoon Network | premium | 1 | funcionais — preservar |
| SporTV 1/2/3 | premium | 2 | mesma tecnologia; fonte/decoder — C1/C2 aplicados |
| GNT | premium | 1 | mesma tecnologia; fonte/decoder — C2 aplicado |
| Globo News, SBT News, Sony, TV Aparecida, TV Brasil, TV Cultura | KEEP_EXISTING | (iptv/atual) | manter link atual |
| Combate | KEEP_EXISTING | — | manter fonte existente (Combate.br) |
| JP News | PENDING | — | sem correspondente reproduzível |

Canais com 2 embeds (premium + alternativa): `cnnbrasil, globoplaynovelas, multishow,
premiere1–8, sportv1–3`.

## 6. Regressões verificadas

- Premiere/Gloob/HBO/Cartoon: caminho (`resolve()` + `preferredEmbed`) **inalterado**.
- `isIptvPrimaryNetwork(Band|Record|Gazeta)` **inalterado**.
- Nenhuma preferência de `novas regras.md` foi modificada silenciosamente.

## 7. Pendências externas (não corrigíveis por código)

1. Globo: API oficial com token/DRM/geo — depende de CDM/geo/permissão da origem.
2. GNT/SporTV: se a fonte não entregar fluxo decodificável no System WebView da TV.
3. Canais “demoram para conectar” (HBO*, Discovery World, Food Network, TLC, Telecine*):
   latência da origem; o app já usa início conservador + ABR do Media3.
