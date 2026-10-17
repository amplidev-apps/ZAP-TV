# 34 — CORREÇÃO DA GRADE E NAVEGAÇÃO (ETAPA 17)

> Etapa 17 §02/§03. Corrige o salto artificial de numeração na navegação do controle
> remoto e consolida a organização da grade em blocos determinísticos, **sem** alterar
> os números já publicados (as ordens coincidem para o catálogo real).

## 1. Defeito real confirmado — navegação saltava 9 → 14

### 1.1 Causa raiz (comprovada sobre os dados reais)
A grade ativa numera o **bloco destacado** a partir de 10. Os primeiros itens são canais
da Fonte A que **não possuem stream reproduzível** no iptv-org
(`isAvailable = false`, ex.: `rdc:adultswim`, `rdc:ae`, `rdc:amc`, `AnimalPlanet.br`).

A navegação (`PlayerViewModel.channelUp/channelDown` → `ChannelRepository.getNext/getPrev`)
consultava **somente canais disponíveis**:

```kotlin
// ANTES (defeito)
val all = channelNumberDao.getAllAvailable().ifEmpty { channelNumberDao.getAll() }
```

Logo, ao sair do canal 9, os canais 10–13 (presentes, porém sem fonte) eram **pulados** e a
sintonia ia direto para o 14 — exatamente o comportamento relatado ("pula do 9 para o 14 e
continua pulando posições"). O mesmo defeito fazia a família **Telecine** parecer
"desagrupada": 66 (Action, disponível) → 72 (TLC), porque 67–71 (sem fonte) eram ocultados.

### 1.2 Correção
A navegação passou a percorrer a **GRADE ATIVA COMPLETA**, por número, sem ocultar canais
presentes mas indisponíveis (Etapa 17 §02: *"seguir a ordem numérica da grade ativa"*,
*"não ocultar canais RDC apenas por ausência de stream"*):

```kotlin
suspend fun getNext(current: Int) = ChannelNumbering.nextChannel(channelNumberDao.getAll(), current)
suspend fun getPrev(current: Int) = ChannelNumbering.prevChannel(channelNumberDao.getAll(), current)
```

`ChannelNumbering.nextChannel/prevChannel` (puros e testáveis) ordenam por número e
**envolvem as pontas**. Como a ordem já vem `ORDER BY number ASC`, o custo é desprezível.

**Diferenciação ausente × indisponível (preservada):**
- número inexistente → `PlayerViewModel.tune` informa "Canal N não existe";
- número presente sem fonte → canal permanece na grade e exibe **SEM SINAL** (`UNAVAILABLE`),
  nunca é removido nem pulado.

## 2. Organização em blocos (Etapa 17 §03)

`ChannelNumbering.orderGrade` mantém a política única e determinística:

| Bloco | Conteúdo | Ordem |
|---|---|---|
| A | Fixos 1–9 (Globo, SBT, Record, RedeTV!, Gazeta, Band, TV Cultura, TV Centro Sul, TV Litoral RN) | por posição |
| B | Canais citados em `docs/novas regras.md` (`SourcePreferenceRegistry.isFeatured`) | família + alfabética canônica |
| C | Demais canais da Fonte A (Rei dos Canais, `channelId in rdcIds`) | categoria, nome |
| D | Demais canais (iptv-org / internacionais / complementares) | país, categoria, nome |

### 2.1 Agrupamento de famílias (§03)
Foi adicionada `SourcePreferenceRegistry.familyOrderKey`, critério **primário** do bloco B:
chave canônica sem o sufixo numérico de variante (`Premiere 2`→`premiere`, `Cazé TV 2`→
`cazetv`, `HBO 2`→`hbo`). O desempate continua alfabético por `nameOrderKey` + `channelId`.

```
familia (Cazé TV)  -> cazetv   -> [Cazé TV, Cazé TV 2, Cazé TV 3] consecutivos
familia (Premiere) -> premiere -> [Premiere, Premiere 2, ...]     consecutivos
```

**Sem impacto de renumeração:** para o conjunto real de `novas regras.md`, a ordem com
`familyOrderKey` é **idêntica** à ordem alfabética anterior (verificado item a item). Portanto
nenhum número muda em instalações existentes e **não há migração destrutiva** de dados.

### 2.2 Preservação do estado do usuário
`ChannelRepository.preserveUserState` continua traduzindo **número antigo → `channelId` →
número novo** durante a sintonia, mantendo favoritos e último canal pelo identificador do
canal (nunca pelo número). Nenhuma aprovação de migração foi necessária porque os números
não mudam.

## 3. Testes adicionados

- `ChannelNavigationTest` (5): 9→10 sem pular indisponíveis, 14→13, sequência crescente,
  envolução nas pontas, canal desconhecido, grade vazia.
- `ChannelNumberingEtapa17Test` (6): famílias Cazé TV/Premiere consecutivas; Gazeta pela
  cabeça real; TV Cultura nunca usa a Cultura Fast.

Suíte alvo: `com.zap.tv.util.*` + `com.zap.tv.data.reidoscanais.*` — **129 testes, 0 falhas**.

## 4. Escopo preservado

Posições 1–9, TV Centro-Sul (8) e TV Litoral RN (9), demais canais RDC, iptv-org, favoritos,
histórico, EPG, regionalização por UF e as Etapas 14–16 permanecem inalterados.
