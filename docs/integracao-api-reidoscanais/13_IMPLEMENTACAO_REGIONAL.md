# 13 — IMPLEMENTAÇÃO DA REGIONALIZAÇÃO

> Etapa 05 — implementação autorizada. Build e testes **não** foram executados nesta etapa (conforme a missão). Nada foi commitado.

## 1. Arquitetura implementada

Fluxo de regionalização (uma política única):

```
Setup/UF (state_config) ──► syncChannelsWithVerification(uf)
        │
        ├─ iptv-org + m3u_catalog.json + customStreams(TV Centro-Sul) ─► mergeChannelsWithM3u
        │
        ├─ ChannelNumbering.resolveFixedEntities(allEntities, uf)
        │        │
        │        └─ selectFixedEntity(config, uf, ...)   ◄── POLÍTICA ÚNICA
        │                 │
        │                 └─ AffiliateRegistry.candidateIds(rede, uf)
        │                       1) afiliada regional reproduzível da UF
        │                       2) cabeça de rede nacional reproduzível
        │                       (3) nome exato  (4) palavra-chave  [compatibilidade]
        │
        └─ getAffiliateState(channelId) ◄── AffiliateRegistry (metadado verificado)
```

Componentes novos:
- `util/AffiliateRegistry.kt` — registro centralizado (fonte única de verdade).
- `selectFixedEntity(...)` — política única usada por `buildNumberedChannels` e `resolveFixedEntities`.

## 2. Mapeamento de afiliadas utilizado

`AffiliateRegistry` (verificado contra `m3u_catalog.json` e iptv-org — doc 07):

| Rede | Cabeça nacional | Cobertura regional |
|---|---|---|
| Globo | `RedeGlobo.br` | **27/27 UFs** |
| SBT | `SBTNacional.br` | 6 (RJ, SP, RS, PR, MG, MT) |
| Record | `Record.br` | 15 (RJ, SP, RS, BA, AM, PE, PI, RR, RO, PA, DF, GO, ES, MG, PB) |
| Band | `Band.br` | 1 (SP) |
| RedeTV! | `RedeTV.br` | 0 (rede sem afiliação) |
| Gazeta | `tv_gazeta.br` | 0 (rede sem afiliação) |
| TV Cultura | `TVCultura.br` | 0 (rede sem afiliação) |
| TV Brasil | `TVBrasil.br` | 0 (rede sem afiliação) |

Campos por afiliada: rede, UF, nome, `channelId` real (identificador estável), fonte (M3U/IPTV_ORG/CUSTOM), `playable` verificado, `epgId` nacional conhecido (globo/sbt/record/redetv/band/tvcultura/tvbrasil), `isHead` e grau de confiança (MEDIA para ES/MG/PB em Record). Nenhum identificador inventado.

## 3. Política de fallback

Ordem determinística, por posição fixa:
1. **Afiliada regional reproduzível da UF escolhida** (ID real do `AffiliateRegistry`).
2. **Cabeça de rede nacional reproduzível** (ex.: `RedeGlobo.br`, `SBTNacional.br`, `Record.br`).
3. **Outra fonte nacional já prevista na arquitetura** (nome exato / palavra-chave).
4. **Posição indisponível**: a posição não é criada; a numeração geral não é deslocada.

Regra absoluta: **nunca** é usada afiliada de outro estado como fallback regional. O mapa `stateFallbacks` (vizinhos) foi **removido**. A seleção é determinística: mesmo catálogo + mesma UF ⇒ mesma grade.

## 4. Correções realizadas

| # | Correção | Arquivo |
|---|---|---|
| C1 | `affiliateMap` reconstruído a partir do `AffiliateRegistry`; IDs fictícios eliminados | `ChannelNumbering.kt` |
| C2 | `stateFallbacks` (afiliada de outro estado) **removido** da seleção | `ChannelNumbering.kt` |
| C3 | `reverseAffiliateMap` reconstruído com IDs reais (cabeças excluídas) | `ChannelNumbering.kt` |
| C4 | `getAffiliateState` prioriza metadado verificado; inferência de UF restrita ao token de ID; sem heurística por nome; retorna `null` (desconhecido) quando insuficiente | `ChannelNumbering.kt` |
| C5 | Política única `selectFixedEntity` compartilhada por `buildNumberedChannels` e `resolveFixedEntities` | `ChannelNumbering.kt` |
| C6 | `resolveFixedEntities` reescrito: afiliada da UF → cabeça nacional → indisponível | `ChannelNumbering.kt` |
| C7 | Troca de região pós-setup (API) sem reiniciar o assistente | `ChannelRepository.kt` |

Exemplo (CE): posição 1 passa a ser `globo_tv_verdes_mares.br` (TV Verdes Mares); posição 2 vira `SBTNacional.br` (antes podia cair em `sbt_sp.br`); posição 3 vira `Record.br`.

## 5. Compatibilidade com o assistente

- O assistente (`SetupScreen`/`SetupViewModel`) **não foi alterado**. A UF escolhida continua persistida em `state_config` e dispara `syncChannelsWithVerification(uf)`, que já aciona a nova política.
- Adicionado `ChannelRepository.updateRegion(uf)`: persiste a nova UF e re-sincroniza a grade reutilizando os mecanismos existentes; não reinicia o setup, não toca em PIN/favoritos/último canal; idempotente se a UF não mudar.
- A **interface** de troca de região pós-setup **não** foi criada (fora do escopo; a lógica já está pronta para quando a tela existir).

## 6. Tratamento de favoritos e persistência

- `FavoriteEntity` e `LastChannelEntity` são indexados por **número de canal**, não por `channelId`. Como as posições fixas 1–9 e a numeração geral foram preservadas, **não há migração** necessária e nenhum registro é substituído/reindexado.
- As afiliadas regionais já existiam no catálogo (apenas passam a ocupar a posição fixa correta). Favoritos por número continuam apontando para o mesmo número.
- Ambiguidade "favorito de rede × favorito de afiliada" mantida como está (comportamento atual preservado); decisão sobre eventual semântica por ID fica **pendente** — registrada aqui, sem migração destrutiva.
- Esquema Room **não** foi alterado.

## 7. Garantias de preservação dos canais 8 e 9

- **TV Centro-Sul (canal 8):** branch dedicado em `buildNumberedChannels` e `resolveFixedEntities`, casando por `channelId == tv_centro_sul_br` (ou nome contendo "Centro Sul"). Mantidos ID, nome, número 8, URL StreamLock, logo e metadados. Nunca substituída em nenhuma UF.
- **TV Litoral RN (canal 9):** resolvida via `AffiliateRegistry` (cabeça `TVLitoralRN.br`) + `exactId`. Mantida no número 9.

## 8. Arquivos modificados / criados

Novos:
- `app/src/main/java/com/zap/tv/util/AffiliateRegistry.kt`
- `app/src/test/java/com/zap/tv/util/AffiliateRegistryTest.kt`
- `app/src/test/java/com/zap/tv/util/ChannelNumberingRegionalTest.kt`
- `docs/integracao-api-reidoscanais/13_IMPLEMENTACAO_REGIONAL.md` (este)

Modificados:
- `app/src/main/java/com/zap/tv/util/ChannelNumbering.kt`
- `app/src/main/java/com/zap/tv/data/repository/ChannelRepository.kt`
- `gradle/libs.versions.toml` (dependência de teste JUnit 4.13.2)
- `app/build.gradle.kts` (`testImplementation(libs.junit)`)

Preservadas as alterações da Etapa 03 (não sobrescritas).

## 9. Testes preparados (NÃO executados)

`AffiliateRegistryTest`:
- Globo cobre as 27 UFs; SBT 6; Record 15; Band 1.
- Cabeças nacionais existem e são reproduzíveis; EPG nacional por rede.
- `candidateIds` prioriza afiliada da UF e depois a cabeça; `NACIONAL` só cabeça.
- `ufForChannelId` só para afiliadas regionais; desconhecido → null.

`ChannelNumberingRegionalTest`:
- Seleção da afiliada da UF correta (CE → Verdes Mares).
- Fallback nacional quando não há afiliada (SBT/Record em CE).
- **Proibição** de afiliada de outro estado (CE/BА não usam SP/SP).
- Preservação da TV Centro-Sul (8) e TV Litoral RN (9).
- Estabilidade da numeração fixa 1–9 entre UFs.
- Ausência de canais duplicados entre posições fixas.
- Determinismo da resolução.
- Troca de estado altera só o canal da rede.
- Identificação de UF por metadado verificado e **não** por palavras do nome.
- Posição ausente quando nem afiliada nem cabeça existem.
- Não mutação das entidades de entrada.

> Não há resultados de execução a reportar: compilação e testes foram adiados por instrução.

## 10. Pendências ainda existentes

1. **Compilação e execução dos testes** (`sh gradlew testDebugUnitTest`) — adiadas.
2. **UI de troca de região** pós-setup não criada (API `updateRegion` pronta).
3. **Semântica de favoritos por ID** (rede × afiliada) — decisão pendente (ver §6).
4. **Cobertura parcial** das redes SBT (21 UFs), Record (12 UFs) e Band (26 UFs) para as quais **não existe** afiliada reproduzível — operam pela cabeça nacional (ver doc 11).
5. **Possível duplicação de mesma rede** (afiliada na posição fixa + cabeça nacional `iptv`/`m3u` na grade dinâmica) — comportamento pré-existente do merge de fontes; não introduzido nesta etapa; avaliar filtro de aliases no futuro.
6. **EPG por praça** indisponível (usa EPG nacional da rede).
7. **IDs de iptv-org** com grafia divergente (ex.: `SBTNacional.br` vs `sbtnacional.br`) podem coexistir como entradas distintas — pré-existente.
8. Build release continua dependente do keystore em caminho Windows (`app/build.gradle.kts`), não tratado nesta etapa.

## 11. Resultado

- Regionalização por UF implementada de forma consistente (política única e determinística).
- Posições fixas 1–9 estáveis; afiliadas de outros estados **não** ocupam mais automaticamente a posição da rede.
- TV Centro-Sul permanece inalterada no canal 8; TV Litoral RN no canal 9.

> A implementação **não** está declarada como validada em execução: compilação e testes foram adiados conforme a missão.
