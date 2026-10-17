# 26 — REFINAMENTO DE REPRODUÇÃO (ETAPA 13)

> Etapa 13 §07/§09/§11. Organização da grade, preservação do estado do usuário e
> diagnóstico de lentidão. Nada aqui contorna DRM/TLS/incorporação nem altera o schema
> Room.

## 1. Organização da grade (§09)

`ChannelNumbering.orderGrade` passa a montar quatro blocos determinísticos:

1. **Fixos 1–9** — exatamente como resolvidos pela Etapa 11 (nunca renumerados).
2. **Citados em `novas regras.md`** — bloco **alfabético** (`SourcePreferenceRegistry.isFeatured`),
   apenas canais brasileiros (ou registros `rdc:`), evitando homônimos estrangeiros.
3. Demais canais da Fonte A (`rdcIds`), por categoria + nome.
4. Demais canais (iptv-org/internacionais), por país + categoria + nome.

- Reversível por `CatalogFilters.FEATURED_ALPHA_BLOCK` (`true` = bloco ativo).
- A renumeração começa em 10, é contígua, sem lacunas e sem duplicar `channelId`.

## 2. Preservação de favoritos e último canal (§09)

Favoritos e último canal são chaveados por **número** no schema atual (imutável sem
migração). Como o novo bloco pode mudar a numeração, o repositório traduz
`número antigo → channelId → número novo` (`preserveUserState`) **antes** de regravar a
grade, garantindo que o mesmo canal continue favorito/último. No primeiro setup
(estado anterior vazio) a operação é um no-op.

## 3. Diagnóstico de lentidão/travamento (§07/§11) — somente DEBUG

- `TuningDiag` registra, no início da sintonia, a observação do usuário quando existir
  (`SourcePreferenceRegistry.noteFor`) — sem URLs/credenciais.
- **Tempo até o primeiro quadro** (`ttf=<ms>`) é medido uma vez por sintonia
  (ExoPlayer em `PLAYING/AUDIO_ONLY`; WebView quando `setWebLoading(false)`).
- Diagnóstico de regionalização (`RegionalDiag`) da Etapa 11 permanece.

## 4. Preservações

- Player híbrido, fallback só em falha confirmada, qualidade adaptativa (Etapa 10) e
  regionalização real (Etapa 11) intactos.
- Canais fixos 1–9, TV Centro-Sul (8), TV Litoral RN (9) e demais canais preservados.
- Canais válidos sem reprodução continuam no ar (indisponíveis, não removidos).

## 5. Testes

- `SourcePreferenceRegistryTest` — preferência Premium, manter/pendente, casamento por
  nome, `isFeatured`, observações.
- `PlaybackResolverTest` — embed primário + stream como recuperação; manter/pendente
  inalterados; fora das regras = Etapa 09.
- `ChannelNumberingFeaturedTest` — bloco alfabético após fixos, citados fora da Fonte A,
  renumeração sem lacunas/duplicatas.

## 6. Limites

Reprodução real, controle remoto, overlays, áudio-só e o comportamento do WebView só
são verificáveis em runtime/TV física. Os testes unitários cobrem apenas decisões
determinísticas.
