# 15 — MIGRAÇÃO DEFINITIVA DAS FONTES E CORREÇÃO DA SINTONIA

> Etapa 07. Objetivo: catálogo ativo baseado em **Fonte A (Rei dos Canais)**, **Fonte B (iptv-org)** e exceções regionais; listas brasileiras antigas desativadas (reversível); temáticos `[24h]` removidos; sintonia mais rápida e não bloqueante; correção de troca de canal/sem sinal. Nenhum commit/push; release não compilado; APK não instalado.

## 1. Fontes ativas — antes e depois

**Antes (em execução):**
- Fonte B — iptv-org (`iptv-org.github.io/api`): canais/streams/logos internacionais e BR.
- Catálogo legado `assets/m3u_catalog.json` (gerado de `CanaisBR01/02/03.m3u8`): `brChannels` + `adultChannels`.
- Exceções: TV Centro-Sul (stream StreamLock próprio) e TV Litoral RN (via iptv-org).

**Depois (catálogo ativo):**
- Fonte A — Rei dos Canais (`api.reidoscanais.si`): **metadados** (nome, logotipo, categoria) e **EPG**. Não fornece stream reproduzível (embeds web) — nunca vai ao ExoPlayer.
- Fonte B — iptv-org: inalterada, reproduzível (internacional + BR).
- Exceções preservadas: TV Centro-Sul (posição 8) e TV Litoral RN (posição 9).
- Catálogo **adulto** legado preservado (recurso do app, protegido por PIN) — ver §5.

## 2. Quantidade de canais por fonte

| Fonte | Canais | Com stream (reproduzíveis) | Situação |
|---|---|---|---|
| iptv-org (global) | 31.630 | **10.284** | ativa (Fonte B) |
| iptv-org (BR) | 825 | **277** | ativa |
| Rei dos Canais (BR) | **104** | 0 (embeds web) | metadados/EPG |
| Legado BR (`brChannels`) | 1.670 | 2.800 links | **desativado** |
| Legado adulto | 600 | 604 links | mantido (adulto) |
| TV Centro-Sul (custom) | 1 | 1 | preservado (pos. 8) |

> O catálogo ativo exibe apenas canais com pelo menos uma fonte reproduzível. Canais que só possuem metadados/embed (Fonte A) **não** aparecem como canais funcionais.

## 3. Canais temáticos 24h removidos
- **1.006 canais** temáticos contínuos removidos (todos com prefixo `[24h]`/`[24H]` e/ou categoria `maratonar` no catálogo legado).
- Regra explícita (`CatalogFilters.isTematic24h`): exclui `[24h]/[24H]`, identificadores `24h_*` e categoria `maratonar`.
- **Guardas**: canais de **notícia/TV ao vivo** nunca são excluídos por transmitirem 24h (ex.: CNN, GloboNews, BandNews). Coberto por testes.

## 4. Listas desativadas
- Ingestão de `CanaisBR01/02/03.m3u8` (via `m3u_catalog.json.brChannels`) **desativada**.
- Arquivos **não excluídos** fisicamente; mecanismo **reversível**:
  `com.zap.tv.util.CatalogFilters.LEGACY_BR_CATALOG_ENABLED` (padrão `false`).
- Com o flag ativo, o comportamento anterior é integralmente recuperado.
- Nenhum dos 59 MB de listas brutas foi regenerado.

## 5. Canais preservados
- **TV Centro-Sul** — canal 8, stream StreamLock original (`5c483b9d1019c.streamlock.net/...`), independente da API e das listas removidas.
- **TV Litoral RN** — canal 9 (`TVLitoralRN.br`).
- **Bases internacionais** (iptv-org) — inalteradas.
- **Catálogo adulto** (600) — mantido, pois a Fonte B tem apenas 1 canal NSFW reproduzível no mundo; removê-lo quebraria o recurso de PIN parental. Passa pelos mesmos filtros (24h / URL reproduzível).
- **Regionalização (Etapa 05)** — política preservada (afiliada da UF → cabeça nacional → outra fonte nacional → indisponível; nunca afiliada de outro estado). Canais fixos 1–9 mantidos.

## 6. Correções no processo de sintonia
- O assistente (`syncChannelsWithVerification`) agora compõe o catálogo **somente** com fontes ativas.
- Verificação de sinal **limitada e não bloqueante**: fixos 1–9 + até 80 canais BR, com **timeout global de 20 s**; serve apenas de indicador de progresso e **nunca** esvazia a grade nem trava o setup.
- **Degradação graciosa**: se a Fonte B falhar, o catálogo existente é mantido (setup não bloqueia).
- **Cache curto** de respostas do verificador (TTL 60 s) para evitar sondagens repetitivas.
- Independência A/B/C/D: só entram canais com **fonte reproduzível autorizada** (A/`metadados` e `embed` não contam como funcionais).

## 7. Compatibilidade da API (Fonte A) com o player
- Reavaliada a integração da Etapa 03: obtenção, DTOs, categorias, logotipos, EPG, identificadores e cache (TTL 5 min) conferem.
- **Embeds web (`rdcanais.org`) nunca são entregues ao ExoPlayer** — novo guard `CatalogFilters.isPlayableStreamUrl` descarta páginas de embed ao montar streams.
- Quando não há fonte reproduzível, o estado é **indisponibilidade explícita** ("SEM SINAL"), sem inventar links, sem extrair streams protegidos, sem contornar DRM/autenticação.
- Metadados da Fonte A (logotipo ausente + categoria genérica) enriquecem o catálogo ativo via `ReiDosCanaisRepository.enrichEntities`, sem remover/renumerar canais.

## 8. Correções na interface "Sem Sinal" / troca de canal
- `PlayerView.setKeepContentOnPlayerReset(false)` — o frame do canal anterior deixa de ser mantido.
- `ExoPlayerManager.clear()` — interrompe e limpa a mídia; chamado ao trocar para canal sem fonte.
- `PlayerViewModel` recebe **geração de sintonia** (`tuneGeneration`): carregamentos antigos não sobrescrevem o canal atual (EPG e streams).
- Estado `noSignal` **não modal** (não exige OK, sem diálogos repetitivos); mostra "SINTONIZANDO" durante a tentativa e "SEM SINAL" ao esgotar as fontes.
- Recuperação automática existente **preservada** (`autoRetry`/watchdog do `ExoPlayerManager`); ao reconectar, o estado "Sem sinal" é limpo.

## 9. Arquivos modificados
Novos:
- `app/src/main/java/com/zap/tv/util/CatalogFilters.kt` (políticas + flag reversível)
- `app/src/test/java/com/zap/tv/util/CatalogFiltersTest.kt` (11 testes)
- `docs/integracao-api-reidoscanais/15_MIGRACAO_FONTES_E_SINTONIA.md`

Editados (Etapa 07):
- `app/src/main/java/com/zap/tv/data/repository/ChannelRepository.kt`
- `app/src/main/java/com/zap/tv/data/reidoscanais/ReiDosCanaisRepository.kt` (`enrichEntities`)
- `app/src/main/java/com/zap/tv/util/ChannelNumbering.kt` (exclusão 24h)
- `app/src/main/java/com/zap/tv/util/StreamHealthChecker.kt` (cache curto)
- `app/src/main/java/com/zap/tv/presentation/player/PlayerViewModel.kt`
- `app/src/main/java/com/zap/tv/presentation/player/ExoPlayerManager.kt`
- `app/src/main/java/com/zap/tv/presentation/player/PlayerScreen.kt`

Mantidas das etapas anteriores: `AffiliateRegistry.kt`, integração RDC (`data/reidoscanais/`, `di/ReiDosCanaisNetworkModule.kt`), `AppDependencies.kt`, `ChannelInfoBanner.kt`, `libs.versions.toml`, `app/build.gradle.kts`.

## 10. Testes executados
Comando:
```
bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=384m"
```
| Classe | Testes | Aprovados |
|---|---|---|
| `CatalogFiltersTest` (novo) | 11 | **11** |
| `ChannelNumberingRegionalTest` | 17 | **17** |
| `AffiliateRegistryTest` | 9 | **9** |
| `M3uCatalogGeneratorTest` | 1 | 0 (falha pré-existente) |
| **Total** | **38** | **37** |

Cobertura Etapa 07: exclusão de temáticos `[24h]`; **não** exclusão de notícia/live 24h; rejeição de embeds/esquemas inválidos; preservação dos canais 8 e 9; regionalização por UF; ausência de duplicação; flag reversível; catálogo sem canais só-metadados.

Build: `bash ./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (1m14s).
APK: `app/build/outputs/apk/debug/app-debug.apk` — 17.882.802 bytes — SHA-256 `03bc6e5abfa3e6841f12de917eceb6d5a55f4e2e50d8971f64aad8748d84567c`.

## 11. Problemas ainda pendentes
- `M3uCatalogGeneratorTest`: `OutOfMemoryError` pré-existente (utilitário manual sem asserções) — não modificado.
- **Afiliadas regionais**: os IDs regionais da Etapa 05 vinham do catálogo legado. Com a desativação, as posições fixas resolvem pela **cabeça nacional** do iptv-org (RedeGlobo.br, SBTNacional.br, Record.br, RedeTV.br, Band.br, TVCultura.br) e a posição 5 pela `TVGazeta.br`. Remapear as afiliadas para os IDs regionais do iptv-org (ex.: `TVVerdesMares.br`, `InterTVCabugi.br`, `RBSTVPortoAlegre.br`) **não fez parte desta etapa** e é a próxima melhoria para restaurar a regionalização por praça.
- Favoritos devem ser revalidados no emulador (podem referenciar números de canais agora ausentes — sem migração destrutiva).
- EPG por praça indisponível (a API fornece EPG nacional por rede).
- Não houve teste em emulador nesta etapa (sem instalação automática); a reprodução real deve ser confirmada manualmente.

> Testes, build e números acima são resultado de execução, não de inspeção. Nada foi commitado/pushado.
