# 03 — PRESERVAÇÃO DA TV CENTRO-SUL

> Canal regional do Ceará, preservação obrigatória. Auditoria completa em 2026-10-16.

## 1. Localização do registro (única origem, sem dependência de listas)

A TV Centro-Sul **não existe** em nenhuma lista externa — verificadas: `CanaisBR01.m3u8`, `CanaisBR02.m3u8`, `CANAIS_LISTA.md`, `assets/m3u_catalog.json` (0 ocorrências de "centro sul"/"tvcentrosul"). Ela existe **apenas como canal custom hardcodeado** em dois pontos do código:

### Registro 1 — `util/ChannelNumbering.kt`
- Canal fixo **posição 8** (`fixedTop9Config`: `FixedChannel(8, "TV Centro Sul", null, "TV Centro Sul", ...)`)
- `tvCentroSulChannel`: id `tv_centro_sul_br`, país BR, categoria `general`, não-NSFW
- `tvCentroSulStream`: **URL `https://5c483b9d1019c.streamlock.net/tvcentrosul/tvcentrosul/playlist.m3u8`** (HLS, qualidade HD)
- `tvCentroSulLogo`: `https://yt3.googleusercontent.com/Sb4wyUWeUMDz05hExE3-VdQ0AWIVnb9qvwsI86XZlXtDndRnikmyub6dRoKsyN7XiJBC-hSfvOM=s900-c-k-c0x00ffffff-no-rj` (900×900)
- Injetada em `buildNumberedChannels()` (fluxo iptv-org puro) e resolvida por id/nome em `resolveFixedEntities()` (fluxo merge M3U)

### Registro 2 — `data/repository/ChannelRepository.kt`
- `customStreams["tv_centro_sul_br"]` com a mesma URL streamlock — entra como stream adicional em `getStreamsForChannel()` e no `syncChannelsWithVerification()`

## 2. Resumo dos metadados

| Campo | Valor |
|---|---|
| Número | **8** (fixo, nunca renumerado) |
| channelId | `tv_centro_sul_br` |
| Nome exibido | TV Centro Sul |
| URL de transmissão | `https://5c483b9d1019c.streamlock.net/tvcentrosul/tvcentrosul/playlist.m3u8` |
| Logotipo | Googleusercontent (YouTube channel art) 900×900 |
| Categoria | general (Entretenimento) |
| Dependências | Nenhuma lista externa; depende apenas do hardcode acima + disponibilidade do streamlock |

## 3. Verificação na nova API

- **TV Centro-Sul NÃO faz parte do catálogo da API Rei dos Canais** (as únicas ocorrências de "centro" no JSON da API são palavras em descrições de EPG de outros canais).
- Logo, não há risco de duplicata ou de substituição pela integração. O casamento por nome (`ReiDosCanaisMapper`) nunca encontrará correspondente para "TV Centro Sul" — EPG será null (comportamento normal do app).

## 4. Garantias aplicadas nesta etapa

1. `ChannelNumbering.kt` — **intocado** (nenhuma linha alterada; posição 8 e injeções preservadas).
2. `customStreams` no `ChannelRepository` — **intocado**.
3. O enriquecimento de metadados (`ReiDosCanaisRepository.enrichCatalog`) atua apenas sobre `m3uCatalog` carregado dos assets; TV Centro-Sul não faz parte dele; e mesmo que houvesse casamento, o enriquecimento **preenche logo apenas quando ausente** e **nunca remove streams**.
4. A numeração fixa 1–9 é resolvida ANTES da numeração dinâmica — inalterada.

## 5. Conclusão

A TV Centro-Sul permanece no número 8 com sua URL original, sem qualquer alteração nesta integração. Nenhuma ação é necessária para preservá-la em futuras mudanças de fontes brasileiras, EXCETO se o hardcode for removido — caso em que este documento é a referência canônica para restaurá-la.
