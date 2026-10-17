# 29 — FONTES FIXAS BAND / RECORD / GAZETA (IPTV-ORG PRIMEIRO)

> Etapa 15 §02. Objetivo: garantir que **Band (posição 6), Record (posição 3) e Gazeta
> (posição 5)** utilizem as fontes do **IPTV-ORG como PRIMEIRA tentativa**, sem abrir
> desnecessariamente a WebView do Rei dos Canais. Nenhuma numeração foi alterada; nenhum
> canal foi removido; schema Room inalterado.

## 1. Estado anterior (verificado)

As três posições são canais de **rede fixa** e, portanto, passam por
`RegionalSourcePlanner.planFixedNetwork(...)` (não pelo caminho genérico
`PlaybackResolver.resolve(...)`). Nesse planejador, cada candidato adiciona **streams
diretos antes dos embeds** (`RegionalSourcePlanner.flatten`). Consequências reais:

| Canal (fixo) | Id real | Fonte A (RDC) casada | Ordem efetiva |
|---|---|---|---|
| Record (3) | `Record.br` | `rdcanais.org/record` (cabeça nacional) | DIRECT (IPTV-ORG) → WEB (embed) |
| Gazeta (5) | `TVGazeta.br` | **nenhuma** (Gazeta não existe no RDC) | DIRECT (IPTV-ORG) |
| Band (6) | `Band.br` | `rdcanais.org/band` (cabeça nacional) | DIRECT (IPTV-ORG) → WEB (embed) |

Ou seja, o requisito já era satisfeito estruturalmente; a Etapa 15 o torna **explícito,
documentado e coberto por teste**, impedindo regressões.

## 2. Garantia implementada

- `SourcePreferenceRegistry.IPTV_PRIMARY_NETWORKS = {Band, Record, Gazeta}` e
  `isIptvPrimaryNetwork(...)`: registram a intenção da Etapa 15 §02 (documentação + testes).
- A garantia de execução permanece em `RegionalSourcePlanner.flatten`: para todo candidato,
  os streams diretos do IPTV-ORG são adicionados **antes** dos embeds da Fonte A.
- `ChannelRepository.getStreamsForChannel(...)` continua ordenando os streams por
  `StreamPreference` (Etapa 14 §05): HTTPS/domínio antes de HTTP/IP — corrige a
  instabilidade de Band/Gazeta sem trocar de emissora.

## 3. Testes automatizados (aprovados)

Em `RegionalSourcePlannerTest`:

- `band_record_gazeta_priorizam_iptv_org_antes_do_embed`: para Band e Record, a primeira
  tentativa é `DIRECT_STREAM` e o embed aparece **depois** (índice > 0); confirma
  `isIptvPrimaryNetwork` para as três redes.
- `gazeta_usa_somente_stream_do_iptv_org`: Gazeta gera **uma única** tentativa direta
  (sem RDC).

## 4. Limites reais (não resolvidos pelo app)

- `TVGazeta.br` só oferece fontes por IP cru/HTTP no IPTV-ORG: o app escolhe a mais estável
  por `StreamPreference`, mas **não** garante estabilidade da origem.
- Para Band/Record, o embed da Fonte A permanece como **recuperação** caso o stream direto
  falhe (por conexão **ou** por falha de vídeo — ver doc 31).
