# 01 — AUDITORIA DAS FONTES ATUAIS

> Data: 2026-10-16 | Commit auditado: `5b7d44e` (branch `main`)
> Método: leitura direta do código-fonte. Informações do README foram usadas apenas como índice — tudo abaixo foi confirmado nos arquivos reais.

## 1. Fontes de canais identificadas

| # | Fonte | Tipo | Arquivos responsáveis | Papel |
|---|-------|------|------------------------|-------|
| 1 | **Catálogo M3U embutido** (`assets/m3u_catalog.json`, 1,06 MB) | JSON local compilado | `data/m3u/M3uCatalog.kt`, `data/repository/ChannelRepository.kt` (loadM3uCatalog) | Principal fonte BR/PT/US/Adulto com streams reproduzíveis |
| 2 | **Listas M3U8 brutas** (`CanaisBR01.m3u8` 28,6 MB, `CanaisBR02.m3u8` 30,3 MB) | M3U na raiz do repo | `data/m3u/M3uParser.kt`, `data/m3u/M3uChannelMapper.kt`, `data/m3u/M3uCatalogGenerator.kt` | Origem do catálogo compilado (NÃO são lidas pelo app em runtime) |
| 3 | **API iptv-org** (`https://iptv-org.github.io/api/`) | HTTP/Retrofit | `data/api/IptvApiService.kt`, `di/NetworkModule.kt` | Canais/streams/logos internacionais, merge no sync |
| 4 | **Streams custom hardcodeados** | Código | `ChannelRepository.customStreams` (1 canal), `ChannelNumbering.tvCentroSulStream` (1 canal) | TV Centro Sul (posição 8) e TV Litoral RN (posição 9) |

## 2. Fluxo real de carregamento (runtime)

1. Setup: `SetupViewModel` → `completeSetupWithVerification()` → `ChannelRepository.syncChannelsWithVerification()`.
2. Sync: baixa iptv-org (channels/streams/logos) → `loadM3uCatalog()` lê `assets/m3u_catalog.json` com Gson → merge `mergeChannelsWithM3u()` (BR do M3U tem prioridade; iptv-org adiciona os demais) → `StreamHealthChecker` (apenas progresso, não remove) → `ChannelNumbering.resolveFixedEntities()` fixa 1–9 → numeração 10–20000 por blocos (BR 10-1999, maratonar 2000-2999, mundo/INTL 3000-9999, PT/US/adultos 10000+).
3. Player: `PlayerViewModel.tune()` → `ChannelRepository.getStreamsForChannel()` (iptv-org + catálogo M3U + custom, dedup por URL, ordenado por qualidade) → `ExoPlayerManager`.

## 3. Componentes auditados (inventário da Seção 08)

| Requisito | Componente real | Observação |
|---|---|---|
| Carregamento M3U | `data/m3u/M3uParser.kt` (streaming, EXTINF), `data/api/M3uParser.kt` (tvg-id/EXTVLCOPT, não usado no fluxo runtime) | Dois parsers distintos — só o primeiro alimenta o gerador do catálogo |
| Comunicação HTTP | `di/NetworkModule.kt` (Retrofit+Moshi+OkHttp, iptv-org) e `data/AppDependencies.kt` (espelho manual para ViewModels não-Hilt) | Dupla via de DI coexiste (Hilt + AppDependencies) |
| Repositório de canais | `data/repository/ChannelRepository.kt` (358 linhas) | Merge, sync, numeração, last-channel |
| Conversão de modelos | `data/m3u/M3uCatalog.kt` (`toApiChannel`/`toApiStream`) | Catálogo M3U → DTOs iptv-org |
| Persistência local | Room `zap.db` v2, 6 tabelas (`AppDatabase.kt`, `ChannelDao.kt`, `ChannelNumberEntity.kt`) + SharedPreferences `zap_tv_prefs` | `fallbackToDestructiveMigration()` ativo (atenção: migrações destrutivas em mudança de schema) |
| Identificadores | `M3uChannelMapper.generateChannelId()` (`nome_normalizado.pais`); iptv-org usa ids próprios (`globo_rj.br`) | Chave estável = nome normalizado + sufixo de país |
| Numeração | `util/ChannelNumbering.kt` | Fixos 1–9 com affiliateMap 27 UFs + stateFallbacks |
| Favoritos | `FavoriteRepository` + `favorites` (chave = número do canal) | Por número, não por channelId |
| Último canal | `last_channel` (Room) via `ChannelRepository.saveLastChannel/getLastChannelNumber` | — |
| Reprodução | `presentation/player/ExoPlayerManager.kt` (Media3 1.2.1, keep-alive, watchdog, fallback multi-stream) | Inalterado nesta etapa |
| Tratamento de erros | try/catch em `loadM3uCatalog`, banners de erro no PlayerViewModel ("Sem sinal para X") | — |
| **EPG** | **Nenhum mecanismo real** — `ProgramGuideOverlay` e `ChannelInfoBanner` exibem texto estático ("Transmissão Digital Ao Vivo", barra 55% fixa) | EPG real da API Rei dos Canais será o primeiro do app |
| Preferências de região | `state_config.uf` (Room) + `affiliateMap`/`stateFallbacks` | — |
| Fallback de fontes | `getStreamsForChannel()` concatena iptv-org + M3U + custom com dedup | Sem prioridade de origem além da ordenação por qualidade |

## 4. Bases IPTV-ORG preservadas (Fonte B)

- **Não alteradas**: `m3u_catalog.json` (ptChannels, usChannels, adultChannels, brChannels), `CanaisBR01/02.m3u8`, endpoint iptv-org (`IptvApiService` + `NetworkModule`), merge completo em `mergeChannelsWithM3u`.
- A integração desta etapa é **puramente aditiva** ao catálogo existente: nenhum canal internacional, PT/US ou adulto foi removido ou substituído.
- Atualização indiscriminada das listas: NÃO realizada (conforme instrução).

## 5. Riscos identificados na base atual (registrados, não corrigidos)

- `fallbackToDestructiveMigration()` descarta o banco em qualquer bump de schema.
- `AppDependencies` e Hilt duplicam construções (mantido o padrão existente por compatibilidade).
- `temp_repo.txt` e `SplashScreen.kt.bak` são resíduos sem efeito no build.
