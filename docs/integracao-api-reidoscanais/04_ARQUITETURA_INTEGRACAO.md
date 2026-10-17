# 04 — ARQUITETURA DA INTEGRAÇÃO

> Decisões de projeto da integração da Fonte A (Rei dos Canais) sobre a base preservada da Fonte B (IPTV-ORG + catálogo M3U interno).

## 1. Princípio norteador

A API Rei dos Canais fornece **metadados (BR) + EPG**, mas **nenhum stream reproduzível** (ver `02_CONTRATO_API.md` §4). Portanto:

- **Catálogo e reprodução continuam exatamente como estavam** (M3U interno + iptv-org + streams custom).
- A API é usada de forma **puramente aditiva**: enriquecer logotipos/categorias de canais BR e fornecer EPG real (current/next) no banner.
- Qualquer falha da API degrada silenciosamente para o comportamento anterior — nunca quebra sintonia, numeração ou persistência.

## 2. Diagrama de componentes

```
                        ┌─────────────────────────────┐
                        │  api.reidoscanais.si (/health│
                        │  /channels, /channels/{id}) │
                        └──────────────┬──────────────┘
                                       │ Retrofit + Moshi + OkHttp (reutilizados)
             ┌─────────────────────────▼──────────────────────────┐
             │ data/reidoscanais/                                   │
             │  ReiDosCanaisApi.kt      (DTOs + interface)         │
             │  ReiDosCanaisModels.kt   (ChannelEpg, EpgProgram)   │
             │  ReiDosCanaisMapper.kt   (normalização, categorias, │
             │                          casamento estável, EPG)    │
             │  ReiDosCanaisRepository.kt (cache 5 min, enrich,    │
             │                          health, degradação segura) │
             └───────┬──────────────────────────────┬───────────────┘
                     │ enrichCatalog()             │ getEpgForChannel()
        ┌────────────▼────────────┐    ┌────────────▼─────────────────┐
        │ ChannelRepository       │    │ PlayerViewModel               │
        │ (loadM3uCatalog: logo  │    │ (tune → epg opcional →        │
        │  e categoria, com      │    │  ChannelInfoBanner mostra     │
        │  fallback integral)    │    │  "NO AR"/"A seguir" reais)    │
        └─────────────────────────┘   └───────────────────────────────┘
        DI: di/ReiDosCanaisNetworkModule.kt (Hilt) + AppDependencies (padrão existente)
```

## 3. Camada de API (A)

- Retrofit idêntico ao do iptv-org (`NetworkModule`): Moshi `KotlinJsonAdapterFactory`, OkHttp 15s/20s, logging BASIC.
- DTOs com defaults em todos os campos opcionais — EPG ausente não quebra o parse.
- `embed_url` modelado como metadado (`RdcEmbedDto`), **nunca** usado como fonte de reprodução.

## 4. Modelos e conversão (B)

- `ChannelEpg`/`EpgProgram` (internos, imutáveis) + `progressFraction()` para a barra do banner.
- `ReiDosCanaisMapper`:
  - `normalizeKey()`: ASCII minúsculo alfanumérico — mesma filosofia do `generateChannelId()` do M3U, tornando o casamento equivalente a comparar **identificadores estáveis**, não nomes crus.
  - Ordem de casamento: chave do nome → chave do channelId (sem sufixo de país) → contenção única com guard mínimo de 4 caracteres (evita falsos positivos tipo "E!" contido em tudo).
  - Categorias PT-BR → slugs internos (Canais Abertos→general, Documentários→documentary, Entretenimento→entertainment, Esportes→sports, Filmes e Séries→movies, Infantil→kids, Notícias→news).
  - `refineCategories()`: só refina canais genéricos ("general"/vazio) — maratonar, mundo, news etc. jamais são sobrescritos.

## 5. Agregação do catálogo (C)

`ReiDosCanaisRepository.enrichCatalog(catalog: M3uCatalog): M3uCatalog`:

| Regra | Motivo |
|---|---|
| Só atua em `brChannels` | PT/US/adultos preservados intactos |
| `logoUrl = group.logoUrl ?: match.logo_url` | **Preenche apenas logos ausentes** — nunca substitui logo funcionante |
| Categoria refinada só para "general" | Não muda numeração/blocos (maratonar/mundo) nem agrupamentos específicos |
| Não remove, não renumera, não injeta canais | Grade e números preservados |
| Falha total → catálogo original | Não destrutivo por construção |

**Canais RDC sem stream reproduzível (13 não casados — Caze TV 2/3, TNT Novelas, Tooncast, Discovery ID, Paramount+ 2-4 etc.):** decisão documentada de **não injetá-los** na grade numerada nesta etapa — apareceriam como "Sem sinal" no zapping. Caso desejado no futuro, o caminho é: parâmetro explícito de UI + teste de aceitação de produto (ver `06_PENDENCIAS_E_TESTES.md`).

**Duplicatas:** casadas por identificador estável (nome normalizado/channelId), não por nome exato — "Globo RJ" (M3U) casa com "Globo" (RDC) para EPG nacional sem duplicar canais na grade.

## 6. EPG (E)

- Fonte: `epg.current`/`epg.next` embutidos em `/channels` — 1 endpoint, 1 cache (TTL 5 min = intervalo documentado de atualização do servidor; no máx. 1 requisição/5 min durante uso).
- Campos opcionais: ausência de EPG → banner mantém o texto estático atual ("Transmissão Digital Ao Vivo"), sem erro.
- A UI existente de "guia completo" (grade 7/14 dias) **não é populada** — a API não fornece grade extensa (confirmado em §2 do contrato).
- ProgramGuideOverlay: permanece como está (lista de canais) — enriquecimento futuro listado nas pendências.

## 7. Reprodução (F) e persistência (G)

- `ExoPlayerManager` e `PlayerScreen` (vídeo): intocados. Nenhuma URL de vídeo foi alterada; embeds nunca entram em `allStreams`.
- Room/SharedPreferences: **schema intocado** (zap.db v2, 6 tabelas). Nenhuma migração. Favoritos (por número), last_channel, PIN parental e UF seguem válidos — a numeração não mudou.

## 8. DI — coexistência Hilt + AppDependencies

O projeto usa dois caminhos de construção (Hilt via `NetworkModule`/`DatabaseModule` e o object `AppDependencies` para ViewModels `AndroidViewModel`). A integração respeita ambos: módulo Hilt novo (`ReiDosCanaisNetworkModule`) + getters aditivos em `AppDependencies` seguindo o padrão lazy sincronizado existente.
