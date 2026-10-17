# 25 — CORREÇÃO DA CONTAGEM DA SINTONIA (ETAPA 13)

> Etapa 13 §08. Corrige o número de "Canais adicionados" exibido na sintonia inicial.

## 1. Sintoma

A tela de sintonia exibia um total **pequeno e variável** (ex.: ~52), embora a grade
persistida tivesse centenas de canais.

## 2. Causa

`SetupScreen` mostrava "$alive", ligado ao valor `alive` de
`StreamHealthChecker.filterAlive`. Esse valor pertence a uma **amostra limitada** de
verificação (fixos + os primeiros `VERIFY_MAX_CHANNELS = 80` canais com stream
reproduzível), e **não** ao tamanho real do catálogo. Logo, o número refletia apenas
quantos canais da amostra responderam à verificação de sinal — nunca o total adicionado.

## 3. Correção

- O callback de progresso passou a carregar um campo `added` (tamanho real e único da
  grade), calculado **antes** da verificação:
  - `mergedNumbered.distinctBy { it.channelId }` → `addedCount = mergedNumbered.size`.
- `runBoundedVerification(...)` recebe `added` e **repassa** esse valor em todo callback
  de progresso; a verificação continua puramente informativa (nunca remove canais).
- `SetupViewModel.VerifyProgress` ganhou `added`; `SetupScreen` exibe
  "Canais adicionados: $added".

Assim, a contagem **independe do sucesso de reprodução** e é estável entre execuções.

## 4. Contagem única e auditável

- A grade final é deduplicada por `channelId` antes de persistir e de exibir.
- Log `TuningAudit` (DEBUG) registra de forma somável/reproduzível:
  - `A_apiRDC` — canais retornados pela Fonte A;
  - `B_iptvOrg` / `B_streams` — canais e streams playable do iptv-org;
  - `F_adicionados` — canais efetivamente adicionados (= persistidos);
  - `H_comFonte` — canais com `streamCount > 0`;
  - `RDC_representados` / `RDC_somenteCatalogo` / `RDC_geral`.
- A amostra verificada (`I`) **não** é usada como total.

## 5. Limites

A verificação de sinal permanece limitada (80 canais, 20 s) e não bloqueia o setup.
Não é possível confirmar reprodução real em testes unitários; a contagem, por outro
lado, é determinística e coberta por lógica pura.
