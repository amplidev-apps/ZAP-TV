# 16 — CATÁLOGO PRINCIPAL REI DOS CANAIS (FONTE A)

> Etapa 08. Objetivo: tornar a **API Rei dos Canais (Fonte A)** a origem **principal do catálogo brasileiro**, importada de forma **independente**, mantendo o **iptv-org (Fonte B)** como fonte **complementar de reprodução** — apenas quando houver fonte autorizada da **mesma emissora**. TV Centro-Sul (posição 8) e TV Litoral RN (posição 9) preservados. Sem WebView, sem commit/push, sem release, APK não instalado.

## 1. Prioridade das fontes

| Papel | Fonte | Uso |
|---|---|---|
| Catálogo / identidade | **A — Rei dos Canais** (`api.reidoscanais.si`) | nomes, logotipos, categorias, EPG, associações regionais; **importação independente** |
| Reprodução | **B — iptv-org** (`iptv-org.github.io/api`) | streams reproduzíveis (HLS/TS) |
| Reprodução (exceções) | **C — custom** | TV Centro-Sul (StreamLock), TV Litoral RN |
| Legado | `assets/m3u_catalog.json` | desativado (§02 Etapa 07); apenas **adulto** preservado |

Regra de reprodução: a Fonte A **não** fornece streams — 100% dos embeds são páginas web `rdcanais.org`. Nenhuma página HTML é entregue ao ExoPlayer e nenhum link é inventado.

## 2. Importação independente (Etapa 08 §02)

A composição **não** depende de o canal existir no iptv-org: **todos os canais válidos da API entram no catálogo**. Para cada canal RDC:

1. Calcula-se um **id de reprodução** (Fonte B) da **mesma emissora** (§4).
2. Se existe fonte reproduzível correspondente → canal **disponível** (stream do iptv-org), com metadados da Fonte A.
3. Se **não** existe → canal registrado **somente no catálogo**, com `isAvailable = false`, `streamCount = 0` e sem reprodução.

Implementação: `ChannelRepository.assembleRdcPrimaryCatalog` + `RdcCatalogMapper`.

## 3. Posições fixas 1–9

| Pos. | Canal | id de reprodução |
|---|---|---|
| 1 | Globo | `RedeGlobo.br` |
| 2 | SBT | `SBTNacional.br` |
| 3 | Record | `Record.br` |
| 4 | RedeTV! | `RedeTV.br` |
| 5 | Gazeta | `TVGazeta.br` |
| 6 | Band | `Band.br` |
| 7 | TV Cultura | `TVCultura.br` |
| 8 | **TV Centro-Sul** | custom StreamLock (preservação absoluta) |
| 9 | **TV Litoral RN** | `TVLitoralRN.br` |

Ordem de seleção da emissora (política única preservada): afiliada reproduzível da UF → cabeça nacional → outra fonte nacional autorizada → canal de catálogo. Canais **somente-catálogo nunca ocupam 1–9** (guard em `ChannelNumbering.buildNumberedChannels`).

## 4. Associação de reprodução — mesma emissora (Etapa 08 §06/§07)

`RdcCatalogMapper.playbackIdFor`:

- **Cabeças de rede aberta** → id iptv-org nacional verificado (Globo/SBT/Record/RedeTV!/Band/TV Cultura).
- **Demais canais** → somente se o **nome normalizado for idêntico** e o canal iptv-org for do **mesmo país (BR)**.
- **Sem correspondência comprovada** → `null` (canal só-catálogo).

Rejeições deliberadas (não são "semelhança de nome"):
- Marcas iguais de **outros países** (ex.: `Globo.pt`, `TNT.ru`, `DiscoveryChannel.cz`) ficam fora — são **outro feed/emissora**. O índice BR é construído filtrando `country == "BR"`.
- Não se associa uma afiliada local a uma transmissão nacional/outra região.
- Não se converte `embed_url` em `.m3u8`, não se extrai token/DRM.

## 5. Regionalização (Etapa 08 §04)

- A região do usuário continua a definir a emissora de cada rede; sem afiliada local reproduzível, usa-se a **cabeça nacional**.
- A própria API identifica **afiliadas regionais Globo** (27 rótulos "Globo <Estado>", slugs `globoce`, `globosp`, …). Essas associações são extraídas por `RdcCatalogMapper.globoRegionalSlugByUf` e registradas como **identidade de catalogação / PENDENTE**, pois **não há fonte reproduzível** equivalente (verificado: as afiliadas Globo regionais no iptv-org praticamente não possuem stream).
- Não se inventam associações: só é usada a informação verificável fornecida pela API/rede/UF.

## 6. Preservação e não-duplicação

- **TV Centro-Sul** (pos. 8) e **TV Litoral RN** (pos. 9): preservadas.
- Canais **internacionais** do iptv-org: preservados.
- **Catálogo adulto** legado: preservado (recurso protegido por PIN), anexado após a numeração por bloco, sem duplicar ids.
- Um mesmo canal do iptv-org **não** é numerado duas vezes (`playableIds`/`dedup` por `channelId`).
- Temáticos `[24h]`/`maratonar`: continuam excluídos (`CatalogFilters.isTematic24h`, defesa em profundidade).

## 7. Reprodução e estados (§06)

- Somente URLs reproduzíveis (`CatalogFilters.isPlayableStreamUrl`) chegam a `getStreamsForChannel`; páginas de embed são descartadas.
- Estados distintos: catálogo/EPG (Fonte A) ≠ reproduzível (Fonte B) ≠ verificado (sintonia).
- `isAvailable` diferencia canais reproduzíveis dos que são apenas registro de catálogo (A/`indisponível`).
- Player: sem HTML, sem WebView; canal sem fonte → "SEM SINAL" não modal (Etapa 07).

## 8. Falha parcial

- Fonte B indisponível → catálogo anterior mantido (não esvazia a grade).
- Fonte A indisponível → o catálogo do iptv-org permanece integral; o overlay de metadados é ignorado.

## 9. Arquivos

Novos:
- `app/src/main/java/com/zap/tv/data/reidoscanais/RdcCatalogMapper.kt` (aliases de rede, matching mesma-emissora, identidades regionais)
- `app/src/test/java/com/zap/tv/data/reidoscanais/RdcCatalogMapperTest.kt` (9 testes)
- `app/src/test/java/com/zap/tv/util/ChannelNumberingCatalogOnlyTest.kt` (3 testes)
- `docs/integracao-api-reidoscanais/16_CATALOGO_PRINCIPAL_RDC.md`

Editados (Etapa 08):
- `app/src/main/java/com/zap/tv/data/repository/ChannelRepository.kt` (`assembleRdcPrimaryCatalog`, `applyRdcMetadata`, `appendAdultChannels`, switch por flag)
- `app/src/main/java/com/zap/tv/util/ChannelNumbering.kt` (`catalogOnlyIds`, `isAvailable`, exclusão dos só-catálogo das posições fixas)

## 10. Testes executados

```
bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=384m"
```

| Classe | Testes | Aprovados |
|---|---|---|
| `CatalogFiltersTest` | 11 | 11 |
| `ChannelNumberingRegionalTest` | 17 | 17 |
| `ChannelNumberingCatalogOnlyTest` (novo) | 3 | 3 |
| `AffiliateRegistryTest` | 9 | 9 |
| `RdcCatalogMapperTest` (novo) | 9 | 9 |
| **Total (pacotes `util` + `reidoscanais`)** | **49** | **49** |
| `M3uCatalogGeneratorTest` | 1 | 0 (falha pré-existente) |

Build: `bash ./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (1m10s).
APK: `app/build/outputs/apk/debug/app-debug.apk` — 17.887.229 bytes — SHA-256 `31e8e51d155dbb6411d10266a2c032669a10040796dde98c7f93407f6ca55e1b`.

## 11. Problemas ainda pendentes

- **Reprodução regional por praça**: indisponível (afiliadas regionais sem stream no iptv-org). Fica como identidade/EPG **PENDENTE**; posições resolvem pela cabeça nacional.
- **~56 dos 104 canais RDC** ficam só-catálogo (sem fonte autorizada correspondente) — a confirmar/ampliar apenas com fonte autorizada (nunca por semelhança de nome).
- EPG por praça indisponível (a API fornece EPG nacional por rede).
- `M3uCatalogGeneratorTest`: `OutOfMemoryError` pré-existente (utilitário manual) — não modificado.
- Favoritos devem ser revalidados no emulador (podem referenciar números agora ausentes — sem migração destrutiva).
- Não houve teste em emulador nesta etapa (sem instalação automática); reprodução real a confirmar manualmente.

> Testes, build e números acima são resultado de execução, não de inspeção. Nada foi commitado/pushado.
