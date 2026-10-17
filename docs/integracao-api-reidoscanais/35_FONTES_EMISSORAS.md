# 35 — FONTES DAS EMISSORAS (ETAPA 17)

> Etapa 17 §04/§05/§06. Correções pontuais na identificação da **emissora correta** das
> posições fixas (TV Cultura e Gazeta) e na otimização do fallback regional da Globo.
> Nenhum bypass de DRM/TLS/incorporação e nenhuma fonte inventada.

## 1. TV Cultura (posição 7) — nunca a Cultura Fast (§04)

### 1.1 Problema
A TV Cultura podia reproduzir o sinal da **Cultura Fast**. A Cultura Fast é um **canal
distinto** no iptv-org (`CulturaFast.br`), enquanto a emissora principal é `TVCultura.br`
(10 streams, incluindo HTTPS de domínio oficial).

### 1.2 Correção (defensiva e determinística)
`ChannelNumbering` ganhou a lista `forbiddenFixedIds`:

```kotlin
private val forbiddenFixedIds = mapOf("TV Cultura" to setOf("culturafast.br"))
```

`selectFixedEntity` exclui esses identificadores de **todas** as etapas (ID exato, nome exato,
palavra-chave). Assim:
- com a cabeça presente, a posição 7 usa **`TVCultura.br`** (mesmo se uma variante casar
  por palavra-chave);
- sem a cabeça, a posição 7 fica **ausente** — nunca cai silenciosamente na Cultura Fast;
- a Cultura Fast continua existindo como canal próprio em outro número da grade.

Testes: `tv_cultura_nunca_usa_a_cultura_fast`, `tv_cultura_principal_tem_precedencia_sobre_a_cultura_fast`.

## 2. Gazeta (posição 5) — cabeça real do IPTV-ORG (§05)

### 2.1 Problema
O registro de cabeça da Gazeta apontava para o ID legado do M3U (`tv_gazeta.br`,
`Source.M3U`). Com as listas BR antigas desativadas (`CatalogFilters.LEGACY_BR_CATALOG_ENABLED
= false`), esse ID não existe no catálogo ativo → a posição 5 só era preenchida por
**palavra-chave** (frágil) e a resolução podia divergir do canal realmente reproduzível.

### 2.2 Correção
- `AffiliateRegistry` (cabeça Gazeta): `tv_gazeta.br` (M3U) → **`TVGazeta.br`** (IPTV_ORG,
  6 streams), alinhado ao `exactId` de `fixedTop9Config`.
- `SourcePreferenceRegistry.IPTV_PRIMARY_NETWORKS` já inclui Gazeta (Etapa 15 §02): o stream
  direto do IPTV-ORG é a **primeira** tentativa; não há embed da Fonte A (a Gazeta não está
  no `rdc_channels.json`).

### 2.3 Limite externo (documentado)
Todos os 6 streams da Gazeta são **IP cru/HTTP** (`http://45.162.64.114/...`,
`http://186.219.52.187/...`, `https://tv.nuvemplay.live/...`). O app escolhe de forma
determinística via `StreamPreference.stabilityRank` (prefere HTTPS+domínio), mas a
estabilidade depende do provedor — **não é contornável pelo app**.

Testes: `gazeta_casa_pela_cabeca_real_do_iptv_org`, `gazeta_usa_somente_stream_do_iptv_org`.

## 3. Globo — fallback regional (§06)

A ordem da Etapa 15 §03 foi **preservada**: `UF → SP → RJ → cabeça nacional → outra afiliada
→ indisponível` (`RegionalSourcePlanner.REFERENCE_BEFORE_HEAD_NETWORKS = {"Globo"}`).

- O plano é **determinístico** e cada fonte é tentada **uma única vez** (sem alternâncias
  infinitas). `flatten` já garante stream antes de embed por candidato e `MAX_ATTEMPTS = 8`.
- A demora observada é o tempo de rede/falha das fontes IP cru, **não** um loop do plano.
- **Não** foram ampliados timeouts (`FIRST_FRAME_TIMEOUT_MS = 3500` e
  `VIDEO_FALLBACK_MS = 8000` permanecem) — a Etapa 16 mostrou que aumentar timeouts cegamente
  degrada a experiência.
- Candidatos com streams repetidos são filtrados (`distinctBy { url }`), evitando repetir a
  mesma URL.

**Pendência de medição:** confirmar o tempo real por fonte exige dados de ADB (log `TuningDiag`
/ `AvDiag`), ainda indisponíveis.

## 4. Status de validação

| Item | Estado |
|---|---|
| TV Cultura não usa Cultura Fast | ✅ código + testes |
| Gazeta pela cabeça `TVGazeta.br` | ✅ código + testes |
| Ordem de fallback Globo | ✅ preservada (testes Etapa 15) |
| Estabilidade real da Gazeta (IP cru) | ⚠ limite externo |
| Tempo real de fallback da Globo | ⚠ requer ADB |
