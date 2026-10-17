# 24 — REGRAS MANUAIS DE FONTES (ETAPA 13)

> Etapa 13 — incorporação das regras manuais de `docs/novas regras.md`, fornecidas pelo
> usuário após testes reais no navegador. Nenhuma URL oculta/token/DRM foi extraído e
> nenhuma restrição de incorporação foi contornada. As regras são **preferências de
> fonte**, não garantias de reprodução.

## 1. Natureza da evidência

- "Funciona no navegador" **não** equivale a "compatível no Android WebView" nem a
  "reprodução confirmada na TV física". Todas as decisões abaixo são de **seleção de
  fonte**; a confirmação audiovisual continua dependendo do player real.
- O conjunto "Servidor Premium" **não** é servidor/endpoint/categoria (ver doc 23): é o
  rótulo `provider` do **mesmo** canal. Nenhum canal/duplicata novo é criado.

## 2. Componente

- `util/SourcePreferenceRegistry.kt`: lista 1:1 das regras do documento.
  - `Preference.RDC_EMBED_PRIMARY`: usar o embed da Fonte A (`rdcanais.org/<slug>`,
    tipicamente provedor "Servidor Premium") como **fonte primária**; streams iptv-org
    da mesma emissora permanecem como **recuperação**.
  - `Preference.KEEP_EXISTING`: manter a fonte já existente (iptv-org / link atual do
    app); **não** promover embed.
  - `Preference.PENDING`: correspondência a confirmar (permanece indisponível).
- `preferredEmbed(...)` seleciona o embed pelo `slug`; havendo mais de uma fonte,
  prioriza `preferredProvider` ("Servidor Premium"). `noteFor(...)` registra observações
  de lentidão/travamento para diagnóstico.

## 3. Aplicação na seleção de fontes (`PlaybackResolver.resolve`)

Para canais **não fixos**, a ordem das tentativas passa a ser:

1. embed preferido da Fonte A (quando a regra é `RDC_EMBED_PRIMARY`);
2. streams diretos (iptv-org / link atual) — recuperação;
3. demais embeds válidos — recuperação.

Canais **fora** do documento e canais `KEEP_EXISTING`/`PENDING` preservam exatamente o
comportamento da Etapa 09 (streams diretos primeiro, embeds depois). Canais fixos 1–9
continuam regidos pela Etapa 11 (regionalização).

## 4. Canais mantidos (não promovidos)

Globo News (iptv-org), Record (iptv-org, fixo 3), SBT News (link atual), Sony Channel,
Sony Movies, TV Aparecida (link atual), TV Brasil (iptv-org), TV Cultura (fixo 7) e
Globo (Etapa 11).

## 5. Pendências registradas

- **Combate**: existe `Combate.br` no iptv-org → mantido como fonte existente
  (`KEEP_EXISTING`).
- **JP News**: não conecta no navegador e não há correspondente reproduzível no
  iptv-org → permanece `PENDING`/indisponível.

## 6. Normalização de nomes (correção local)

`ReiDosCanaisMapper.normalizeKey` filtra `a..z` **antes** de minusculizar e descarta
letras maiúsculas — inofensivo quando os dois lados passam pelo mesmo caminho, mas
inútil para comparar um nome Title Case com uma chave de regra. O registro usa uma
normalização **local** (`key()`, minúsculas + sem acentos + `[a-z0-9]`), sem tocar no
mapper compartilhado (preservação da Etapa 09).

## 7. Reversibilidade

Basta remover/alterar `SourcePreferenceRegistry.rules` (ou reverter a chamada em
`PlaybackResolver.resolve`) para voltar ao comportamento da Etapa 09. O bloco de
ordenação tem flag própria (ver doc 26).
