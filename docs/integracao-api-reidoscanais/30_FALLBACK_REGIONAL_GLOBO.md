# 30 — FALLBACK REGIONAL DA GLOBO (UF → SP → OUTRA → INDISPONÍVEL)

> Etapa 15 §03. Para a Globo (posição 1), a política de fallback passa a ser
> **1) afiliada da UF configurada → 2) Globo São Paulo → 3) outra afiliada disponível da
> mesma rede → 4) indisponível**. A prioridade regional só é abandonada após uma falha
> **devidamente caracterizada** (conexão confirmada ou falha de vídeo), respeitando
> conexões lentas. A origem regional utilizada é sempre identificada corretamente na UI.

## 1. Mudança aplicada

`RegionalSourcePlanner.planFixedNetwork(...)` ganhou a faixa de referência
(`REFERENCE_SP_RJ = [SP, RJ]`) reordenada **antes** da cabeça nacional para as redes em
`REFERENCE_BEFORE_HEAD_NETWORKS = {Globo}`:

- Antes (Etapa 11): UF → cabeça nacional → SP/RJ → outra afiliada → indisponível.
- Agora (Etapa 15 §03, Globo): **UF → SP → RJ → cabeça nacional → outra afiliada → indisponível**.

A cabeça nacional (`RedeGlobo.br`) deixa de ser prioritária, mas **não é descartada**:
permanece como alternativa da mesma rede. As demais redes (SBT, Record, RedeTV!, Band,
Gazeta, TV Cultura) mantêm a ordem da Etapa 11, preservando os testes já aprovados.

O consumo de embeds por região é feito na mesma ordem de construção das faixas
(UF → cabeça → referências → outras), portanto a reordenação **não** altera a atribuição
de cada embed à sua região — apenas a ordem de tentativa.

## 2. Garantias preservadas

- **IDENTIDADE DO CANAL** ≠ **FONTE EM REPRODUÇÃO**: `stateForAttempt` continua propagando
  `identityLabel`, `sourceRegion`, `sourceLabel`, `sourceKind` e `sourceConfidence`; a UI
  (banner) exibe a fonte real quando há regionalização.
- **Sem inferência por URL**: as associações vêm de `AffiliateRegistry` (API/Affiliate) e
  dos provedores `Globo <Estado>` da Fonte A.
- **Determinismo e ausência de repetições**: `flatten` + `take(MAX_ATTEMPTS)`; cada URL
  aparece uma única vez (`distinctBy` na construção dos embeds).
- **Limite de outras afiliadas**: `MAX_OTHER_AFFILIATES = 3`.
- **Canal permanece acessível**: quando nenhuma fonte entrega vídeo, o canal **não** é
  removido da grade (ver doc 31, §5).

## 3. Testes automatizados (aprovados)

Em `RegionalSourcePlannerTest`:

- `globo_ordem_uf_sp_rj_cabeca_outras` → regiões `[CE, SP, RJ, NACIONAL, BA]`.
- `globo_sem_fonte_da_uf_usa_sp_antes_da_cabeca_nacional` → `REFERENCE_SP_RJ (SP)` antes de
  `NATIONAL_HEAD`.
- `globo_sem_sp_cai_para_outra_afiliada` → sem SP, cai em `OTHER_AFFILIATE (BA)`.
- `afiliada_da_uf_tem_prioridade_sobre_cabeca_nacional` (revalidado), `determinismo_e_sem_repeticoes_de_fonte`
  e `outras_afiliadas_sao_limitadas` permanecem verdes.

## 4. Limites reais

- A escolha de qual afiliada atende cada UF depende do cadastro (`AffiliateRegistry`) e da
  existência de fonte reproduzível; a UI nunca afirma reprodução confirmada sem evento real
  de renderização (doc 31).
