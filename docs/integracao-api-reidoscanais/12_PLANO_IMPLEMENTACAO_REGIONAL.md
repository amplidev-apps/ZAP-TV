# 12 — PLANO DE IMPLEMENTAÇÃO REGIONAL

> Plano derivado da auditoria (docs 07–11). **Nada aqui foi implementado.** Requer autorização explícita para execução. Toda mudança é incremental, reversível e testável no emulador `ZAP_TV_Test` (Android TV 11 / API 30).

## 1. Objetivo

Fazer com que cada número fixo (1–9) apresente, para a UF escolhida no assistente, a **afiliada regional real e reproduzível** quando ela existir; caso contrário, o **sinal principal da rede** (cabeça nacional) — **sem** usar afiliada de outro estado — preservando a TV Centro-Sul (8) e a TV Litoral RN (9).

## 2. Entregável principal: tabela verificada de afiliadas

Novo arquivo (proposto): `app/src/main/java/com/zap/tv/util/AffiliateRegistry.kt`

- Conteúdo: mapa `rede → UF → lista ordenada de IDs canônicos reais` (de doc 07), com `confidence`.
- Fontes dos IDs: `m3u_catalog.json` (embarcado) e iptv-org (baixado/validado). **Nenhum ID inventado.**
- Métodos: `resolve(network, uf): String?` (ID real da afiliada) e `nationalHead(network): String` (cabeça).
- Deve ser puro/testável (sem dependência de Android) para cobrir 27 UFs × redes em teste unitário.

### Amostra de conteúdo (Globo e Record, IDs reais)

```
Globo: AC=globo_rede_amazonica_rio_branco.br, CE=globo_tv_verdes_mares.br,
       SP=globo_sp.br, RJ=globo_rj.br, MG=globo_minas.br, PR=globo_rpc_tv_curitiba.br ... (27)
SBT:   RJ=sbt_rj.br, SP=sbt_sp.br, RS=sbt_rs.br, PR=sbt_rede_massa_pr.br,
       MG=sbt_tv_alterosa.br, MT=SBTCuiaba.br (iptv)
Record:RJ=recordtv_rj.br, BA=record_tvitapoan.br, AM=recordtv_manaus.br,
       PE=recordtv_guararapes.br, PI=recordtv_piaui_a10.br, GO=record_tvgoias.br ... (15)
Band:  SP=band_sp.br
```

## 3. Alterações por arquivo

### 3.1 `util/ChannelNumbering.kt` (principal)
1. Substituir o corpo do `affiliateMap` por dados da `AffiliateRegistry` (IDs reais), **mantendo a assinatura pública** para não quebrar referências.
2. Em `resolveFixedEntities` (linhas 419–495), reordenar a resolução para:
   - (0) **afiliada da UF** via `AffiliateRegistry.resolve(rede, uf)` (match por ID real);
   - (1) **cabeça nacional** (exactId atual);
   - (2) exactName; (3) keyword — apenas como último recurso (compatibilidade).
   - **Remover `stateFallbacks` da prioridade de reprodução** (nenhuma afiliada de outro estado). Manter a constante apenas se usada para outro fim; caso contrário, marcar como não utilizada.
3. `reverseAffiliateMap`: reconstruir a partir dos IDs reais da `AffiliateRegistry` (mapa `channelId → UF`), tornando `getAffiliateState` eficaz para os canais de afiliada reais.
4. `getAffiliateState`: manter o passo de token como complemento, mas agora a tabela cobre as afiliadas sem sigla (ex.: `globo_tv_verdes_mares.br → CE`).
5. Ajustar o filtro por UF (linhas 335–342) para que afiliadas mapeadas sejam corretamente retidas/removidas por UF.
6. Preservar **byte a byte** os branches de posição 8 (`tv_centro_sul_br`/`"Centro Sul"`) e 9 (`tv_litoral_rn.br`/`"Litoral RN"`).
7. Avaliar remoção do caminho morto `buildNumberedChannels` **somente após confirmar** que não é usado em produção (D6 do doc 09). Se removido, atualizar `ChannelRepository` linhas 78 e 172.

### 3.2 `data/repository/ChannelRepository.kt`
- Nenhuma mudança obrigatória: `mergeChannelsWithM3u` (linha 101+/270) já passa a lista unificada para `resolveFixedEntities` e `getStreamsForChannel` resolve por `channelId` real.
- Opcional (somente se a UF precisar ser reaplicada fora do setup): expor método de re-sync reutilizando `syncChannelsWithVerification`.

### 3.3 Integração com a API Rei dos Canais
- **Sem reprodução** dos embeds (são páginas web). Uso opcional futuro: metadado de "sinal regional disponível" para a Globo — **não** neste plano, para não introduzir dependência de rede na grade.
- `ReiDosCanaisRepository` permanece como na Etapa 03 (enriquecimento de logo/categoria, degradação segura).

### 3.4 NÃO alterar
- Esquema Room (`ChannelNumberEntity`, `StateConfigEntity`), `AppDatabase`, DAOs.
- `SetupViewModel`/`SetupScreen` (a UF já chega corretamente).
- Player, M3U parser, trilha de EPG.

## 4. Testes / verificação

1. **Unitário — `AffiliateRegistry`**: para cada uma das 27 UFs e redes Globo/SBT/Record/Band, assertar ID esperado ou fallback cabeça. Sem tocar em Android.
2. **Unitário — `resolveFixedEntities`**: cenário CE → posições 1–9:
   - 1 = `globo_tv_verdes_mares.br` (Verdes Mares), **não** Globo SP;
   - 2 = `SBTNacional.br` (sem SBT CE reproduzível);
   - 3 = `Record.br` (sem Record CE);
   - 6 = `band.br`/`Band.br`;
   - 8 = TV Centro-Sul; 9 = TV Litoral RN.
3. **Regressão fixa**: garantir posições 1–9 sempre preenchidas com fonte reproduzível e que **nenhuma** afiliada de outro estado seja usada.
4. **Instrumentado/emulador**: rodar setup escolhendo CE e SP; verificar grade (pos 1), reprodutibilidade de stream (ExoPlayer) e persistência em `state_config`.
5. **Empacotamento**: `sh gradlew :app:assembleDebug` (NTFS exige `sh`), instalar no emulador, fluxo completo de setup.

## 5. Sequência sugerida
1. Criar `AffiliateRegistry` + testes unitários (docs 07 como fonte).
2. Refatorar `resolveFixedEntities`/`affiliateMap`/`reverseAffiliateMap`/`getAffiliateState`.
3. Testes unitários de regressão (CE, SP, RJ, MT, AL).
4. Build debug + teste no emulador (setup CE e SP).
5. Documentar resultado; **não** commitar sem autorização.

## 6. Riscos e mitigações
| Risco | Mitigação |
|---|---|
| ID real ausente em runtime (fonte mudou) | fallback cabeça nacional; nunca vazio, nunca outro estado |
| Nome de canal difere da fonte | match por **ID**, não por nome |
| Quebra da TV Centro-Sul (posição 8) | branch preservado + teste de regressão obrigatório |
| Caminho de numeração morto usado em algum lugar | confirmar chamadas antes de remover |
| NTFS / build | usar `sh gradlew`; caches em `~/.gradle` |

## 7. Critérios de aceite
- CE (exemplo do desenvolvedor): posição 1 = TV Verdes Mares reproduzível.
- Todas as 27 UFs com posição 1 = afiliada Globo local reproduzível.
- SBT/Record/Band: afiliada local quando existir; caso contrário cabeça nacional.
- Zero uso de afiliada de outro estado.
- TV Centro-Sul na 8 e TV Litoral RN na 9 preservadas.
- Nenhuma regressão de build/teste.
