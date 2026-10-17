# 22 — REGIONALIZAÇÃO REAL DAS TRANSMISSÕES

> Etapa 11. Objetivo: fazer a regionalização determinar a **transmissão efetivamente
> reproduzida** (não apenas nome/logo/EPG) e implementar fallback determinístico entre
> afiliadas. Substitui a proibição absoluta de afiliadas de outras UFs. Preserva as
> Etapas 08–10 (catálogo RDC, player híbrido, recuperação e qualidade adaptativa).
> Sem commit/push, sem release, APK não instalado.

## 1. Causas identificadas (auditoria)

O sintoma: escolher **Ceará** no assistente não garantia a afiliada cearense no canal 1.

1. **Fonte regional não participava da seleção.** A UF influenciava apenas a escolha da
   entidade da posição fixa (`resolveFixedEntities`), que — com as listas M3U brasileiras
   desativadas na Etapa 08 — passou a resolver sempre para a **cabeça nacional**
   (`RedeGlobo.br`). As referências regionais da Globo vivem **nos embeds da Fonte A**
   (provedor `Globo <Estado>`), não em entidades separadas do catálogo.
2. **Embeds entregues na ordem bruta da API.** `getEmbedsForChannel` devolvia os 27 embeds
   da Globo na ordem da API, começando por `Globo Rio de Janeiro`. O player abria
   `globorj`/`globosp`, nunca `globoce`.
3. **Stream direto nacional preferido à UF.** Mesmo quando a afiliada da UF existia como
   embed, a precedência "stream direto > embed" fazia o canal abrir na cabeça nacional
   (`RedeGlobo.br`) — exibindo programação de rede/outra praça.
4. **Sem separação identidade x fonte.** O estado não informava qual transmissão estava
   no ar, então um fallback podia exibir São Paulo/Rio como se fosse a UF escolhida.
5. **Sem plano ordenado de fallback.** As tentativas eram duas listas planas
   (diretas/embeds), incapazes de expressar prioridade por região.

## 2. Correções realizadas

- **`RdcCatalogMapper.regionForEmbedProvider(provider)`** — expõe a região atribuída pela
  própria API ao provedor (`Globo Ceará` → `CE`). `null` = transmissão nacional. Sem
  inferência por URL/nome solto.
- **`RegionalSourcePlanner`** (novo) — produz a lista **ordenada e determinística** de
  tentativas por política de prioridade; limita afiliadas "de outra região"
  (`MAX_OTHER_AFFILIATES = 3`) e o total (`MAX_ATTEMPTS = 8`).
- **`PlaybackResolver`** reescrito para um **plano de tentativas** (`PlaybackPlan.attempts`)
  com metadados de fonte (`PlaybackSource`/`SourceRegionKind`). `resolveForChannel(...)`
  aplica regionalização só em canais de rede fixa; demais canais mantêm a Etapa 09.
- **`ChannelNumbering.networkForFixedPosition(pos)`** — mapeia posição → rede (posição 8 = `null`).
- **`PlayerViewModel`** — percorre as tentativas; a FONTE REAL é atualizada a cada troca
  (`sourceLabel`/`sourceRegion`/`sourceKind`); `nextStreamOnError`/`onWebError` avançam
  uma tentativa (fim do "streamIndex" inconsistente).
- **`ChannelInfoBanner`** — exibe `FONTE: <emissora> • <UF>` quando aplicável.
- **`ExoPlayerManager`** — repetição transitória **limitada** (2) antes de escalar a falha
  da fonte: instabilidade momentânea não troca de região prematuramente.
- Diagnóstico `RegionalDiag` (somente DEBUG), sem URLs/credenciais (apenas host).

## 3. Política final de seleção

Para cada canal de rede fixa (posições 1–7 e 9), a ordem é:

| Prioridade | Região (`SourceRegionKind`) | Fonte |
|-----------:|-----------------------------|-------|
| 1 | `SELECTED_UF` | afiliada da UF escolhida |
| 2 | `NATIONAL_HEAD` | cabeça nacional da mesma rede |
| 3 | `REFERENCE_SP_RJ` | referência SP e/ou RJ (SP antes de RJ) |
| 4 | `OTHER_AFFILIATE` | outra afiliada autorizada (máx. 3), depois "cinto de segurança" |
| 5 | — | indisponível (plano vazio) |

A transmissão da UF é **sempre** a primeira candidata quando existe fonte compatível.
Não há reordenação por latência/resolução. Cada fonte é tentada **uma vez** (com as
repetições transitórias internas do player), sem alternâncias infinitas.

Dentro de uma mesma região, stream direto é tentado antes de embed. A posição 8
(TV Centro Sul) **não** é regionalizada.

## 4. Mapeamento das afiliadas (Globo na Fonte A)

Todos os embeds regionais da Fonte A pertencem ao canal RDC `globo` (id `globo`), são
**páginas** (`WEB_EMBED`, `https://rdcanais.org/<slug>`) e têm confiança **MÉDIA**
(identidade informada pela própria API — reprodução não comprovada). Nenhuma outra rede
possui embeds regionais na Fonte A (SBT/Record/Band/RedeTV!/Cultura → 1 embed nacional).

| Rede | Emissora (provedor) | UF | ID RDC | Slug/embed | ID iptv-org | Tipo | Confiança |
|------|---------------------|----|--------|------------|-------------|------|-----------|
| Globo | Globo Ceará | CE | globo | globoce | — | WEB_EMBED | MÉDIA |
| Globo | Globo São Paulo | SP | globo | globosp | TVGloboSaoPaulo.br (stream) | WEB_EMBED | MÉDIA |
| Globo | Globo Rio de Janeiro | RJ | globo | globorj | TVGloboRiodeJaneiro.br (sem stream) | WEB_EMBED | MÉDIA |
| Globo | Globo Minas Gerais | MG | globo | globomg | TVGloboMinas.br (sem stream) | WEB_EMBED | MÉDIA |
| Globo | Globo Rio Grande do Sul | RS | globo | globors | — | WEB_EMBED | MÉDIA |
| Globo | Globo Bahia | BA | globo | globoba | — | WEB_EMBED | MÉDIA |
| Globo | Globo Paraná | PR | globo | globopr | — | WEB_EMBED | MÉDIA |
| Globo | Globo Pernambuco | PE | globo | globope | TVGloboNordeste.br (sem stream) | WEB_EMBED | MÉDIA |
| Globo | Globo Distrito Federal | DF | globo | globodf | TVGloboBrasilia.br (sem stream) | WEB_EMBED | MÉDIA |
| Globo | Globo Espírito Santo | ES | globo | globoes | — | WEB_EMBED | MÉDIA |
| Globo | Globo Santa Catarina | SC | globo | globosc | — | WEB_EMBED | MÉDIA |
| Globo | Globo Piauí | PI | globo | globopi | — | WEB_EMBED | MÉDIA |
| Globo | Globo Maranhão | MA | globo | globoma | — | WEB_EMBED | MÉDIA |
| Globo | Globo Rio Grande do Norte | RN | globo | globorn | — | WEB_EMBED | MÉDIA |
| Globo | Globo Paraíba | PB | globo | globopb | — | WEB_EMBED | MÉDIA |
| Globo | Globo Goiás | GO | globo | globogo | — | WEB_EMBED | MÉDIA |
| Globo | Globo Amazonas | AM | globo | globoam | — | WEB_EMBED | MÉDIA |
| Globo | Globo Pará | PA | globo | globopa | — | WEB_EMBED | MÉDIA |
| Globo | Globo Acre | AC | globo | globoac | — | WEB_EMBED | MÉDIA |
| Globo | Globo Alagoas | AL | globo | globoal | — | WEB_EMBED | MÉDIA |
| Globo | Globo Amapá | AP | globo | globoap | — | WEB_EMBED | MÉDIA |
| Globo | Globo Mato Grosso | MT | globo | globomt | — | WEB_EMBED | MÉDIA |
| Globo | Globo Mato Grosso do Sul | MS | globo | globoms | — | WEB_EMBED | MÉDIA |
| Globo | Globo Rondônia | RO | globo | globoro | — | WEB_EMBED | MÉDIA |
| Globo | Globo Roraima | RR | globo | globorr | — | WEB_EMBED | MÉDIA |
| Globo | Globo Sergipe | SE | globo | globose | — | WEB_EMBED | MÉDIA |
| Globo | Globo Tocantins | TO | globo | globoto | — | WEB_EMBED | MÉDIA |

Cabeças nacionais reproduzíveis (iptv-org, **ALTA**): `RedeGlobo.br`, `SBTNacional.br`,
`Record.br`, `RedeTV.br`, `Band.br`, `TVCultura.br`. Stream de referência adicional:
`TVGloboSaoPaulo.br`. As demais referências `TVGlobo*.br` existem no iptv-org **sem stream**.

> "Uma página de player que contenha apenas o nome de uma UF não comprova transmissão."
> Por isso os embeds regionais têm confiança **MÉDIA** e o app não afirma reprodução
> comprovada — a validação audiovisual é pendente na TV física (§9).

## 5. Fallback entre regiões

Ordem efetiva para o canal 1 (Globo), UF = CE:

```
[T0] WEB_EMBED  globoce  (SELECTED_UF)      <- afiliada da UF
[T1] DIRECT     RedeGlobo.br (NATIONAL_HEAD) <- cabeça nacional
[T2] WEB_EMBED  globosp  (REFERENCE_SP_RJ)   <- referência SP
[T3] WEB_EMBED  globorj  (REFERENCE_SP_RJ)   <- referência RJ
[T4..] WEB_EMBED outras afiliadas Globo (OTHER_AFFILIATE, máx. 3)
```

A passagem só ocorre diante de **falha confirmada**: o `ExoPlayerManager` repete a fonte
até `MAX_TRANSIENT_RETRIES` (2) por falha transitória; a WebView sinaliza erro do frame
principal (ou timeout de 15 s). Carregar a página **não** é confirmação audiovisual.
Esgotado o plano → indisponibilidade real (`noSignal`). Sem alternâncias infinitas.

Para redes sem afiliada regional (SBT/Record/Band/RedeTV!/Cultura), a ordem efetiva é
cabeça nacional → referência SP/RJ → outra afiliada (quando existir).

## 6. Integração com WebView

- O embed da afiliada é carregado por `WebPlayer` (`WEB_EMBED`), sem alteração de política
  de segurança (TLS cancela, sem file/content access, sem captura de foco).
- Troca de embed (`webUrl` diferente) → `stopLoading()` + `loadUrl()` (sem dois players).
- Timeout de 15 s e erro do frame principal → `onWebError()` → próxima tentativa.

## 7. Integração com ExoPlayer

- Atraso de abertura não declara a afiliada indisponível: o watchdog reconecta e a
  repetição transitória é limitada antes de escalar.
- Áudio-só continua detectado (`AUDIO_ONLY`) e não é tratado como sucesso audiovisual.
- Qualidade adaptativa (Etapa 10) preservada (`DefaultTrackSelector` inalterado).

## 8. Testes e resultados

`RegionalSourcePlannerTest` (14) + suíte existente. Casos cobertos:
Ceará/SP/RS selecionam a afiliada correspondente; afiliada da UF tem prioridade sobre a
cabeça nacional; identidade exibida corresponde à fonte real; fallback nacional; fallback
SP/RJ; fallback outra afiliada; ausência de fontes mantém plano vazio; determinismo e
ausência de repetição de fonte; limite de outras afiliadas; rede sem embeds usa a cabeça;
posições 8/9 não regionalizadas; limpeza/preservação do catálogo (`ChannelNumberingOrderTest`).

- `:app:testDebugUnitTest` (JDK 17) — **verde** (80 testes; `M3uCatalogGeneratorTest`
  permanece OOM, pré-existente e não modificado).
- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- APK: `app/build/outputs/apk/debug/app-debug.apk` — 17.917.717 bytes,
  sha256 `075bf1737d4c7a36969858584c654218d18852f7a46ee9df8a7f33fff71db102`.

> Os testes validam a **ordem determinística** das fontes, não a reprodução real.

## 9. Pendências de validação na TV física

1. Confirmar que o canal 1, com UF = CE, abre `globoce` (afiliada cearense) em WebView.
2. Confirmar que UF = SP/RJ/RS abrem `globosp`/`globorj`/`globors`.
3. Confirmar o fallback real: afiliada indisponível → cabeça nacional → referência.
4. Confirmar que o banner mostra a FONTE real após fallback.
5. Confirmar ausência de alternâncias/apagões em rede fraca.
6. Confirmar preservação de 8/9, favoritos, último canal, categorias e EPG.

Nada foi commitado/pushado; o APK não foi instalado em dispositivos.
