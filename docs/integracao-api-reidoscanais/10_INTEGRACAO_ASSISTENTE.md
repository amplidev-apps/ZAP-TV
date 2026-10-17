# 10 — INTEGRAÇÃO COM O ASSISTENTE

> Mapeamento do fluxo de configuração atual e dos pontos de integração para a regionalização. Somente leitura — nada alterado.

## 1. Fluxo atual do assistente (`presentation/setup`)

`SetupViewModel` controla 4 passos (`_step`):

| step | Tela | Ação |
|---|---|---|
| 0 | Modo do dispositivo | `selectDeviceMode` → `nextFromDeviceMode()` salva `device_mode` em `SharedPreferences` (`zap_tv_prefs`) |
| 1 | Estado (UF) | `selectUf(v)` → `nextFromState()` (exige UF não vazia) |
| 2 | Nome do usuário | `setUserName` → `nextFromName()` salva `user_name` |
| 3 | PIN parental | `setPin`/`setConfirmPin` → `confirm()` ou `skipPin()` |

Conclusão do setup (passos 3): `repo.completeSetupWithVerification(_uf.value, pin)`.

## 2. Persistência da UF

- `ChannelRepository.completeSetupWithVerification(uf, pin, onProgress)` (linha 66):
  1. `stateConfigDao.save(StateConfigEntity(uf = uf))` — **linha única** na tabela `state_config`;
  2. `syncChannelsWithVerification(uf, onProgress)` — baixa/mescla canais e numera.
- Leitura: `ChannelRepository.getSelectedUf() = stateConfigDao.get()?.uf ?: "NACIONAL"` (linha 366).
- `StateConfigEntity` (em `ChannelNumberEntity.kt:28`) guarda apenas a UF. É a **única** fonte de região do app.

## 3. Ausência de fluxo pós-setup

- `selectUf` só é chamado em `SetupScreen.kt:436` (tela de seleção da UF, passo 1).
- `getSelectedUf()` é lido no carregamento da grade, mas **não há nenhuma tela** que permita **trocar a UF** depois do setup (sem "Configurações → Região").
- Consequência: a região é imutável na prática após a primeira execução. Alterar a regionalização em runtime só seria possível reintroduzindo o setup ou adicionando uma tela de troca de região.

## 4. Ponto de integração para a regionalização

A regionalização **já** é disparada no fim do setup através de `syncChannelsWithVerification(uf)`, que chama `mergeChannelsWithM3u(...)` → `resolveFixedEntities(mergedEntities, uf)`. Portanto:

- **Não é necessário** alterar `SetupViewModel` nem `SetupScreen` para regionalizar: basta a UF escolhida alimentar corretamente a **tabela de afiliadas** usada por `resolveFixedEntities`.
- A cadeia de dados é: `SetupScreen` → `selectUf` → `completeSetupWithVerification` → `stateConfigDao.save` → `syncChannelsWithVerification` → `mergeChannelsWithM3u` → `resolveFixedEntities(allEntities, uf)` → `ChannelNumberEntity` gravados na grade.
- `getSelectedUf()` já pode ser consumido por qualquer tela de grade para reaplicar a região.

## 5. Observações de escopo (não implementar agora)
- A "troca de região" pelo usuário **não faz parte** do escopo solicitado; se um dia for pedida, o caminho natural é uma ação que chame novamente `syncChannelsWithVerification(novaUf)` (o `stateConfigDao.save` é upsert de linha única, hoje via `@Insert(onConflict = REPLACE)` — confirmar no DAO antes de reusar).
- A seleção de UF usa `res/raw/brazil_states.json` (grade das 27 UFs); o valor selecionado é exatamente a sigla usada como chave em `affiliateMap` — compatibilidade garantida com a tabela verificada proposta.
