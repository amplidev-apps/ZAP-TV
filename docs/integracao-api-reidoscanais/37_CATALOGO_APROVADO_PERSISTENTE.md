# 37 — CATÁLOGO APROVADO PERSISTENTE (ETAPA 18)

> Etapa 18 — Cenário B1 (ledger persistido). O pertencimento ao catálogo ativo passa a
> ser uma decisão **de identidade** (registro persistente de `channelId`s aprovados),
> **nunca** da existência de stream. Um canal aprovado **nunca** é removido por falha de
> reprodução ou de rede; a indisponibilidade afeta apenas o estado de reprodução e a
> escolha de fontes. Sem WebView, sem commit/push, sem release, APK não instalado.

## 1. Problema corrigido

`ChannelNumbering.buildNumberedChannels` admitia um canal iptv-org **somente** se
`streamsByChannel[ch.id]` não vazio (ou se fosse registro catalog-only da Fonte A):

```kotlin
// ANTES (Etapa 08–17): stream decidia EXISTÊNCIA
val filtered = withAffiliateBoost.filter { ch ->
    !streamsByChannel[ch.id].isNullOrEmpty() || ch.id in catalogOnlyIds
}
```

Consequência: um canal aprovado que perdesse temporariamente a fonte desaparecia da grade
(critério proibido pela diretriz). A auditoria quantitativa (Etapa 18, planejamento)
mostrou que **21.330** registros iptv-org eram excluídos por esse critério (523 BR, 93 PT,
3.589 US, 17.125 de outros países) — nenhum deles deve ser importado automaticamente.

## 2. Regras de pertencimento (novo critério)

1. Um canal pertence à grade se seu `channelId` está no **ledger aprovado** ou em
   `catalogOnlyIds` (registro da Fonte A sem fonte reproduzível).
2. `streamUrl`/`isAvailable`/sucesso de reprodução **não** admitem nem removem canais:
   controlam apenas `isAvailable` (estado) e a escolha de fonte.
3. Nenhum registro bruto do iptv-org é importado automaticamente. Os cenários de
   allow-list por país (C/D) foram **rejeitados**.
4. Duplicação impedida: o ledger é um `Set` e a grade é `distinctBy { channelId }`.

Implementação (parâmetro novo, retrocompatível):

```kotlin
val filtered = withAffiliateBoost.filter { ch ->
    ch.id in approvedIds || ch.id in catalogOnlyIds ||
        (approvedIds.isEmpty() && !streamsByChannel[ch.id].isNullOrEmpty()) // legado
}
```

## 3. Inicialização do ledger (sem registro vazio)

O ledger é a **união monotônica** de quatro fontes — nunca começa vazio e nunca remove
identidade:

| Fonte | Papel |
|---|---|
| Ledger persistido (`app_flags`) | Continuidade entre execuções. |
| Grade local (`channel_numbers`) | **Instalações existentes**: preserva exatamente o que já está na grade. |
| Semente empacotada (`assets/approved_catalog_seed.json`) | **Primeira execução**: rede-independente, evita grade vazia/diminuta se o download de streams falhar. |
| `ChannelNumbering.fixedApprovedIds` | Identidades das posições fixas 1–9. |

No fim de `assembleRdcPrimaryCatalog`, o ledger final recebe também **todas** as
identidades da Fonte A representadas (`metadataById.keys`), cobrindo registros
catalog-only e `rdc:<id>`.

**Semente**: 10.347 identificadores (dos quais 47 `rdc:`), correspondentes à composição
atual da grade aprovada (10.300 canais iptv-org + registros da Fonte A).

## 4. Persistência

- **Sem alteração de esquema Room** (versão 2 preservada; `fallbackToDestructiveMigration`
  intacto). O ledger é gravado em `app_flags` sob a chave `approved_catalog_ledger_v1`
  (`ApprovedCatalogLedger.serialize`, um id por linha, ordenado).
- Falha de leitura/gravação é **não destrutiva** (nunca derruba nem esvazia a grade).
- A adoção de uma tabela dedicada (`approved_channels`) é candidata futura, mas exigiria
  migração e **não** foi feita.

## 5. Procedimento de inclusão futura (não congelar)

Novos canais são aprovados **sem depender de stream** por:
1. **Fonte A (automático)**: qualquer canal novo do Rei dos Canais entra via
   `metadataById`/`catalogOnlyIds` na próxima sincronização.
2. **Semente curada**: regenerar `approved_catalog_seed.json` (adicionar os `channelId`s
   aprovados) — procedimento explícito e verificável.
3. **Grade existente**: canais já gravados no aparelho são absorvidos no ledger.

Nunca há remoção automática de identidades aprovadas.

## 6. Posições fixas 1–9 (complemento obrigatório)

As posições fixas existem independentemente da transmissão. A seleção usa apenas
identidade (id exato → afiliada da UF → cabeça de rede → nome exato → palavra-chave),
nunca a presença de stream:

```kotlin
val fixedPool = filtered.filter { it.id !in catalogOnlyIds || it.id in approvedIds }
```

`ChannelNumbering.fixedApprovedIds` garante que as identidades das posições (incluindo
`TVCentroSul` na 8) estejam sempre aprovadas, de modo que nenhuma posição desapareça por
perda de sinal. A proibição da Cultura Fast na posição 7 (Etapa 17) permanece.

## 7. Impacto na numeração

- Composição **idêntica** à grade atual na primeira execução (a semente reproduz a
  composição aprovada) → **nenhum canal renumera**.
- Ordem preservada: **fixos 1–9 → destacados → demais RDC → iptv-org** (`orderGrade`).
- Ajuste do `fixedPool` permite que uma identidade fixa sem stream mantenha seu número.

## 8. Compatibilidade com instalações existentes

- A grade local é incorporada ao ledger antes da regravação: nada do que o usuário já vê
  desaparece.
- Favoritos e último canal são remapeados por `channelId` (`preserveUserState`), nunca por
  número; como nada é removido, o estado do usuário é preservado integralmente.
- Sem migração Room, sem perda de dados.

## 9. Testes

`bash ./gradlew :app:testDebugUnitTest --tests "com.zap.tv.util.*" --tests "com.zap.tv.data.reidoscanais.*"`

- **142 testes, 0 falhas** (129 anteriores + 13 novos).
- Novos:
  - `ApprovedCatalogLedgerTest` (5): parse/serialize determinístico; união monotônica;
    ausência de duplicatas; entradas em branco ignoradas.
  - `ChannelNumberingApprovedLedgerTest` (8): canal aprovado permanece após perder o
    stream; canal não aprovado com stream **não** entra; catalog-only permanece; posição
    fixa sobrevive sem stream; catalog-only não aprovado não assume posição fixa;
    comportamento legado preservado quando o ledger está ausente; segunda sincronização não
    remove; aprovação futura não duplica.
- Regressões preservadas: navegação (Etapa 17), blocos/ordem, regionalização, catalog-only
  (Etapa 08), filtros de catálogo, fontes fixas.

## 10. Artefato

- **APK Debug**: `app/build/outputs/apk/debug/app-debug.apk`
  - tamanho: **17.944.027 bytes**
  - sha256: `dbfb7313e14f2e71d77e013286f33f7a8f598f647f9e374cb9593c5960214119`
  - assets: `approved_catalog_seed.json` (173.958 bytes) empacotado.

## 11. Limitações

- **Sem TV física/ADB**: reprodução/renderização não validadas; a correção é de
  composição de grade.
- A semente é um retrato da composição atual; crescimento dos canais iptv-org **não**
  ligados à Fonte A depende de curadoria da semente (procedimento §5).
- O catálogo adulto legado (`appendAdultChannels`) mantém o critério próprio e **não** faz
  parte deste ledger.
- `getAllAvailable`/`countAvailable`/`updateAvailability` permanecem como candidatos à
  remoção futura; **não** foram alterados nesta etapa.
