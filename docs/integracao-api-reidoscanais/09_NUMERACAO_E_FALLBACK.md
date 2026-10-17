# 09 — NUMERAÇÃO E FALLBACK

> Auditoria do comportamento ATUAL (código em `util/ChannelNumbering.kt`, HEAD `5b7d44e`). Nenhuma alteração feita. Este documento descreve o que existe hoje e o **comportamento desejado** (política) a ser implementado na etapa futura.

## 1. Posições fixas (1–9) — `fixedTop9Config`

| Pos | Nome | exactId | exactName | keywords |
|---|---|---|---|---|
| 1 | Globo | `RedeGlobo.br` | Globo | redeglobo, tvglobo |
| 2 | SBT | `SBTNacional.br` | SBT | sbt nacional, sbtv |
| 3 | Record | `Record.br` | Record | recordtv, record tv |
| 4 | RedeTV! | `RedeTV.br` | RedeTV! | redetv, rede tv |
| 5 | Gazeta | `tv_gazeta.br` | Gazeta | tvgazeta, tv gazeta |
| 6 | Band | `Band.br` | Band | bandtv, band tv, bandnews |
| 7 | TV Cultura | `TVCultura.br` | TV Cultura | tvcultura, tv cultura |
| 8 | TV Centro Sul | — (custom) | TV Centro Sul | centro sul, centrosul |
| 9 | TV Litoral RN | `TVLitoralRN.br` | TV Litoral RN | tv litoral, tvlitoral |

Verificação de fonte dos `exactId` (2026-10-16):

| exactId | m3u | iptv-org | Reproduzível? |
|---|---|---|---|
| `RedeGlobo.br` | ✘ | ✔ (2) | sim (nacional) |
| `SBTNacional.br` | ✘ | ✔ (3) | sim (nacional) |
| `Record.br` | ✘ | ✔ (3) | sim (nacional) |
| `RedeTV.br` | ✘ | ✔ (6) | sim (nacional) |
| `tv_gazeta.br` | ✔ (1) | ✘ | sim |
| `Band.br` | ✘ | ✔ (2) | sim (nacional) |
| `TVCultura.br` | ✘ | ✔ (10) | sim (nacional) |
| `TVLitoralRN.br` | ✘ | ✔ (1) | sim |

Conclusão: **todas as 9 posições têm fonte reproduzível de última instância**, portanto nenhuma posição fica vazia por falta total de fonte (desde que a lista unificada contenha o canal).

## 2. Ordem de resolução ATUAL (`resolveFixedEntities`, linhas 419–495)

Para as posições 1–7 (redes com `affiliateMap`):

1. **Prioridade 0 — afiliada regional:** monta `checkUfs = [selectedUf] + stateFallbacks[selectedUf]` e, para cada UF na ordem, tenta casar `expectedId` (id da afiliada mapeada) por: `channelId.equals(expectedId, ignoreCase)` **ou** nome contém `"<Rede> <UF>"` **ou** id contém `"<rede>_<uf>"`. O **primeiro** que casar vence.
2. **Fallback 1 — ID exato** (`channelId == config.exactId`, comparação **case-sensitive**).
3. **Fallback 2 — nome exato** (`channelName.equals(exactName, ignoreCase)`).
4. **Fallback 3 — palavra-chave** (contains em id ou nome).
5. Se nada casar → **a posição simplesmente não é criada** (grade fica com um buraco).

Posições 8 e 9 têm branch dedicado (linhas 428–449): 8 casa por `channelId == "tv_centro_sul_br"` ou nome contém `"Centro Sul"`; 9 casa por `channelId == "tv_litoral_rn.br"` ou nome contém `"Litoral RN"`.

## 3. Defeitos confirmados na resolução atual

| # | Defeito | Evidência | Efeito prático |
|---|---|---|---|
| D1 | **IDs do `affiliateMap` estão órfãos.** Usam esquema com sigla UF e acentos removidos (`globo_ce_tv_verdes_mares.br`), mas os IDs reais do M3U não têm a sigla (`globo_tv_verdes_mares.br`). | Globo 24/27, SBT ~21/27, Record ~23/27, Band ~17/27 entradas do mapa não existem em nenhuma fonte. | Prioridade 0 nunca casa com a afiliada correta. |
| D2 | **`stateFallbacks` substitui por afiliada de OUTRO estado.** A prioridade 0 percorre estados vizinhos ANTES de tentar o sinal nacional. | Para CE, pos 1 cai em `globo_sp.br` (SP está na cadeia de CE); para a maioria das UFs, pos 2 cai em `sbt_sp.br`. | Viola a política desejada ("não usar afiliada de outro estado como substituta automática"). Usuário do CE vê Globo SP, não a cabeça nacional nem Verdes Mares. |
| D3 | **`reverseAffiliateMap` é inócuo.** É construído a partir dos mesmos IDs órfãos (exclui "nacional"/"news"/"tv_gazeta"). | Nenhum `channelId` real casa com esse mapa. | `getAffiliateState` cai sempre no passo 2 (token de sigla). |
| D4 | **Detecção de UF por token falha para afiliadas sem sigla no nome.** | `"Globo TV Verdes Mares"` → nenhum token é UF → retorna `null`. | No filtro por UF (`linhas 335–342`), `state == null` **mantém** o canal → afiliadas de outros estados **vazam** para a grade do usuário (ex.: RJ vê Verdes Mares). |
| D5 | **exactId case-sensitive no fallback 1** (`==`, linha 473) vs. afiliada `ignoreCase` (linha 462). | `Band.br` (iptv) casa; um id `band.br` (m3u) não casaria. | Inconsistência menor; funciona porque iptv tem a grafia exata. |
| D6 | **Dois caminhos de numeração divergentes:** `buildNumberedChannels` (linhas 207–388, boosts + filtro embutido) e `resolveFixedEntities` (usado em produção com lista unificada). | — | Código morto/ambíguo; risco de manutenção duplicada. Confirmar qual é chamado antes de alterar. |
| D7 | **Posição 9 depende de nome.** | `TVLitoralRN.br` tem id sem `tv_litoral_rn.br`; resolve por nome contém "Litoral RN". | OK hoje, mas frágil se o nome da fonte mudar. |

## 4. Comportamento desejado (política para a implementação)

Ordem de resolução proposta para cada posição fixa de rede:

1. **Afiliada da UF selecionada** (tabela verificada do doc 07/12, por **ID real**).
2. **Sinal principal da rede** (cabeça nacional: `RedeGlobo.br`, `SBTNacional.br`, `Record.br`, `RedeTV.br`, `Band.br`, `TVCultura.br`, `TVBrasil.br`) — **sem** substituição por afiliada de outro estado.
3. **Posição mantida indisponível** (não criar) apenas se nem a cabeça existir — não preencher com canal de outra rede/estado.

Regras:
- **Nunca** usar afiliada de outro estado (remover `stateFallbacks` da prioridade de afiliada; no máximo usar para ordenação de sugestões, jamais como reprodução).
- Cada número fixo deve resolver por **ID real** (não por heurística de nome/sigla).
- **TV Centro-Sul permanece fixa no 8** e TV Litoral RN no 9, preservadas por ID e por nome (regressão obrigatória).
- Ordem de EPG: EPG nacional da rede (única opção; a RDC não fornece EPG por afiliada — doc 11).

## 5. Impacto / compatibilidade
- Renumerar as posições 1–9 **não** deve ocorrer; apenas o **canal associado** a cada posição muda (de nacional/vizinho para a afiliada da UF).
- Nenhuma alteração de esquema de banco (`ChannelNumberEntity` já carrega `affiliateState`, `channelId`, `channelName`, `logoUrl`, `streamCount`).
- `getStreamsForChannel` opera por `channelId`; como a afiliada regional é um `channelId` real do M3U/iptv, **os streams já resolvem sem tocar no repositório**.
