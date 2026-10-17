# 05 — ALTERAÇÕES REALIZADAS

> Etapa de integração da API Rei dos Canais — 2026-10-16.
> Branch: `main` | Base: commit `5b7d44e`. **Nenhum commit realizado** (aguardando autorização).

## 1. Arquivos criados (6)

| Arquivo | Papel | Linhas |
|---|---|---|
| `app/src/main/java/com/zap/tv/data/reidoscanais/ReiDosCanaisApi.kt` | DTOs (todos com defaults tolerantes) + interface Retrofit (`/health`, `/channels`, `/channels/{id}`, `/channels/categories`) | 78 |
| `app/src/main/java/com/zap/tv/data/reidoscanais/ReiDosCanaisModels.kt` | `ChannelEpg` + `EpgProgram` + `progressFraction()` | 32 |
| `app/src/main/java/com/zap/tv/data/reidoscanais/ReiDosCanaisMapper.kt` | Normalização estável, mapa de categorias, conversão de EPG, casamento por identificador, refinamento seguro de categorias | 105 |
| `app/src/main/java/com/zap/tv/data/reidoscanais/ReiDosCanaisRepository.kt` | Cache 5 min, health, `enrichCatalog`, `getEpgForChannel` — falha sempre degrada a null/sem alteração | 131 |
| `app/src/main/java/com/zap/tv/di/ReiDosCanaisNetworkModule.kt` | Módulo Hilt reutilizando Moshi + OkHttp existentes | 34 |
| `docs/integracao-api-reidoscanais/` (6 docs) | Documentação da integração | — |

## 2. Arquivos modificados (5 — todos aditivos)

| Arquivo | Mudança | Natureza |
|---|---|---|
| `data/AppDependencies.kt` | Novos getters lazy `getReiDosCanaisApi()`/`getReiDosCanaisRepository()`; `ChannelRepository` agora recebe `rdcRepository` | Aditivo (+27 linhas) |
| `data/repository/ChannelRepository.kt` | Param `rdcRepository` no construtor; `loadM3uCatalog()` enriquece o catálogo com fallback integral em exceção | Aditivo (+13/−2) |
| `presentation/player/PlayerViewModel.kt` | Campo `epg` no `PlayerUiState`; busca de EPG em `playChannel` com try/catch e guarda anti-race (só aplica se ainda no mesmo canal); limpa `epg` a cada sintonia | Aditivo (+21/−2) |
| `presentation/overlay/ChannelInfoBanner.kt` | Param opcional `epg: ChannelEpg? = null`; "NO AR"/"A seguir" exibem EPG real com fallback ao texto estático; barra de progresso real (`progressFraction`) com fallback a 55% | Aditivo (+23/−4) |
| `presentation/player/PlayerScreen.kt` | Passa `epg = ui.epg` ao banner | 1 linha |

**Total: +76/−9 linhas em arquivos versionados existentes.**

## 3. O que NÃO foi alterado (garantias)

- `util/ChannelNumbering.kt` — numeração, fixos 1–9, affiliateMap: **intocado** (TV Centro-Sul posição 8 preservada).
- `customStreams` (URL streamlock da TV Centro-Sul): **intocado**.
- `ExoPlayerManager.kt` / player: **intocado** — nenhuma URL de vídeo alterada; embeds não entram em `allStreams`.
- `assets/m3u_catalog.json`, `CanaisBR01/02.m3u8`, `IptvApiService`, `NetworkModule`: **intocados**.
- Room/SharedPreferences: **schema intocado** — sem migração, favoritos/last-channel/PIN/UF preservados.
- `app/build.gradle.kts` / `gradle/`: **intactos** — nenhuma dependência nova (apenas Retrofit/Moshi/OkHttp/Hilt já existentes).
- Setup 5 passos, navegação por controle, overlays, parental: comportamento idêntico na ausência de EPG.

## 4. Justificativa por prioridade da Seção 09

| Letra | Entregue | Como |
|---|---|---|
| A. Camada de API | Sim | Retrofit reutilizando stack existente; falhas → null/sem-op |
| B. Modelos | Sim | DTOs + modelos internos + mapper; modelos existentes preservados |
| C. Agregação | Sim (conservadora) | Enriquecimento de BR casado por identificador estável; internacionais intactos; 13 canais RDC sem stream ficam fora da grade (decisão documentada) |
| D. Numeração | Sim | Zero mudança numérica; fixos 1–9 intocados |
| E. EPG | Sim | current/next + progresso no banner; ProgramGuideOverlay permanece estático (API não tem grade extensa) |
| F. Reprodução | Sim | Player preservado; embeds jamais usados como stream |
| G. Persistência | Sim | Nenhuma alteração de schema/dados |
