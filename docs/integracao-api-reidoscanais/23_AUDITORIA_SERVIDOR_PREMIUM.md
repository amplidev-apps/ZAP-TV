# 23 — AUDITORIA DO SERVIDOR PREMIUM DA API REI DOS CANAIS

> Etapa 12 — **investigação e documentação apenas**. Nenhum código Kotlin alterado,
> nenhuma numeração/regionalização tocada, sem build, sem commit, sem instalar APK.
> Evidência primária: `GET https://api.reidoscanais.si/channels` capturado em
> 2026-10-16 18:38 BRT (`/tmp/opencode/rdc_channels.json`, 151 KB) + documentação
> pública `https://reidoscanais.si/api` (consultada nesta etapa) + código atual
> (`RdcCatalogMapper`, `ReiDosCanaisApi`, `ChannelRepository`).

## 1. O que é o "Servidor Premium"

**"Servidor Premium" NÃO é um servidor, não é um endpoint e não é uma categoria de
canais.** É um **rótulo textual no campo `provider` da lista `embeds` de cada canal**,
exatamente no mesmo nível de "Servidor Alternativo" e de "Globo Ceará". Ou seja, é
apenas o **nome de um provedor/fonte de reprodução alternativa** apresentado pela API.

- Não existe menu, categoria ou endpoint "Premium" na API pública.
- A API segue "Gratuita, sem autenticação" (documentação oficial); Premium não introduz
  login, token, DRM ou parâmetro extra.
- Todos os embeds (Premium, Alternativo e afiliadas Globo) apontam para o **mesmo host**
  `https://rdcanais.org/<slug>` — o "servidor" é o mesmo; muda apenas o rótulo do provedor.
- Os canais associados a "Servidor Premium" **são** os canais de `GET /channels`
  (103 dos 104). Logo, Premium **não acrescenta canais**.

## 2. Evidências encontradas

| Evidência | Resultado |
|---|---|
| `provider` distintos em 144 embeds | 29: `Servidor Premium` (103×), `Servidor Alternativo` (14×), 27 afiliadas `Globo <Estado>` (1× cada) |
| `embed_url` em 144/144 embeds | `https://rdcanais.org/<slug>` (scheme `https` 144/144; host `rdcanais.org` 144/144) |
| Campos disponíveis em `embeds` | apenas `provider`, `quality`, `embed_url` (DTO `RdcEmbedDto`) |
| Docs oficiais | listam **só** `/channels`, `/channels/{id}`, `/channels/categories`, `/sports`, `/search`, `/health`; exemplo usa `"provider": "Servidor 1"` |
| Categorias | 7 fixas (Canais Abertos, Documentários, Entretenimento, Esportes, Filmes e Séries, Infantil, Notícias) — nenhuma "Premium" |
| Busca textual "premium" no payload | aparece só como substring de **nomes/categorias** de canais (ex.: "Telecine Premium", "Premiere") — nunca como categoria/servidor |
| Código atual | `RdcCatalogMapper.regionForEmbedProvider` trata `Servidor Premium` como **genérico → nacional** (`null`), jamais como região |
| Doc `02_CONTRATO_API.md` | já registrava "Servidor Premium" como provedor; **desatualizado** quanto às afiliadas Globo ("8×" → hoje **27×**) |

> Observação de método: não foram enumeradas rotas ocultas, não se tentou acessar áreas
> protegidas, não se buscou contornar autenticação/DRM. Tudo veio de dados públicos já
> consumidos pela integração e da documentação oficial.

## 3. Quantidade de canais associados

- Catálogo total da API: **104 canais** / **144 embeds**.
- Canais com o rótulo `Servidor Premium`: **103**.
  - **89** têm **somente** `Servidor Premium`.
  - **14** têm `Servidor Premium` **e** `Servidor Alternativo`.
- **1** canal **não** possui `Servidor Premium`: `globo` (usa 27 embeds de afiliadas Globo).

## 4. Lista completa dos registros públicos identificados

Todos os 103 canais abaixo pertencem a `GET /channels` (mesmos registros da API
principal). `ID ZAP-TV` = identificador que o catálogo atual usaria (id iptv-org quando há
stream da mesma emissora; `rdc:<id>` quando é catalog-only). Estado atual do app:
`stream` = reproduzível via iptv-org; `embed-only` = registro de catálogo sem stream.

| # | ID RDC | Nome | Categoria | Provedor(es) | Qualidade | EPG | ID ZAP-TV | Reprodução |
|--:|--------|------|-----------|--------------|-----------|-----|-----------|------------|
| 1 | `adultswim` | Adult Swim | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:adultswim` | embed-only |
| 2 | `ae` | AE | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:ae` | embed-only |
| 3 | `agromais` | Agro+ | Notícias | Servidor Premium | FULL HD | sim | `rdc:agromais` | embed-only |
| 4 | `amc` | AMC | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:amc` | embed-only |
| 5 | `animalplanet` | Animal Planet | Documentários | Servidor Premium | FULL HD | sim | `rdc:animalplanet` | embed-only |
| 6 | `arte1` | Arte1 | Entretenimento | Servidor Premium | SD | sim | `Arte1.br` | stream |
| 7 | `axn` | AXN | Filmes e Séries | Servidor Premium | FULL HD | sim | `AXN.br` | stream |
| 8 | `band` | Band | Canais Abertos | Servidor Premium | FULL HD | sim | `Band.br` | stream |
| 9 | `bandnews` | Band News | Notícias | Servidor Premium | FULL HD | sim | `BandNews.br` | stream |
| 10 | `bis` | BIS | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:bis` | embed-only |
| 11 | `canal-brasil` | Canal Brasil | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:canal-brasil` | embed-only |
| 12 | `canaloff` | Canal OFF | Entretenimento | Servidor Premium | FULL HD | sim | `CanalOff.br` | stream |
| 13 | `cartoonnetwork` | Cartoon Network | Infantil | Servidor Premium | FULL HD | sim | `rdc:cartoonnetwork` | embed-only |
| 14 | `cartoonito` | Cartoonito | Infantil | Servidor Premium | FULL HD | sim | `rdc:cartoonito` | embed-only |
| 15 | `cazetv` | Caze TV | Esportes | Servidor Premium | FULL HD | não | `rdc:cazetv` | embed-only |
| 16 | `cazetv2` | Caze TV 2 | Esportes | Servidor Premium | FULL HD | não | `rdc:cazetv2` | embed-only |
| 17 | `cazetv3` | Caze TV 3 | Esportes | Servidor Premium | FULL HD | não | `rdc:cazetv3` | embed-only |
| 18 | `cinemax` | Cinemax | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:cinemax` | embed-only |
| 19 | `cnnbrasil` | CNN Brasil | Notícias | Servidor Alternativo, Servidor Premium | FULL HD | sim | `CNNBrasil.br` | stream |
| 20 | `combate` | Combate | Esportes | Servidor Premium | FULL HD | sim | `Combate.br` | stream |
| 21 | `curta` | Curta! | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:curta` | embed-only |
| 22 | `discoverychannel` | Discovery Channel | Documentários | Servidor Premium | FULL HD | sim | `rdc:discoverychannel` | embed-only |
| 23 | `discoveryheh` | Discovery Home e Health | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:discoveryheh` | embed-only |
| 24 | `discoveryid` | Discovery ID | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:discoveryid` | embed-only |
| 25 | `discoverykids` | Discovery Kids | Infantil | Servidor Premium | FULL HD | sim | `rdc:discoverykids` | embed-only |
| 26 | `discoveryscience` | Discovery Science | Documentários | Servidor Premium | FULL HD | sim | `rdc:discoveryscience` | embed-only |
| 27 | `discoverytheater` | Discovery Theater | Documentários | Servidor Premium | FULL HD | sim | `rdc:discoverytheater` | embed-only |
| 28 | `discoveryturbo` | Discovery Turbo | Documentários | Servidor Premium | FULL HD | sim | `rdc:discoveryturbo` | embed-only |
| 29 | `discoveryworld` | Discovery World | Documentários | Servidor Premium | FULL HD | sim | `rdc:discoveryworld` | embed-only |
| 30 | `e` | E! | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:e` | embed-only |
| 31 | `fashiontv` | Fashion TV | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:fashiontv` | embed-only |
| 32 | `fishtv` | Fish TV | Entretenimento | Servidor Premium | FULL HD | sim | `FishTV.br` | stream |
| 33 | `foodnetwork` | Food Network | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:foodnetwork` | embed-only |
| 34 | `futura` | Futura | Documentários | Servidor Premium | FULL HD | sim | `Futura.br` | stream |
| 35 | `globonews` | Globo News | Notícias | Servidor Premium | FULL HD | sim | `rdc:globonews` | embed-only |
| 36 | `globoplaynovelas` | Globoplay Novelas | Entretenimento | Servidor Alternativo, Servidor Premium | FULL HD | sim | `GloboplayNovelas.br` | stream |
| 37 | `gloob` | Gloob | Infantil | Servidor Premium | FULL HD | sim | `Gloob.br` | stream |
| 38 | `gloobinho` | Gloobinho | Infantil | Servidor Premium | FULL HD | sim | `rdc:gloobinho` | embed-only |
| 39 | `gnt` | GNT | Entretenimento | Servidor Premium | FULL HD | sim | `GNT.br` | stream |
| 40 | `hbo` | HBO | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:hbo` | embed-only |
| 41 | `hbo2` | HBO 2 | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:hbo2` | embed-only |
| 42 | `hbofamily` | HBO Family | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:hbofamily` | embed-only |
| 43 | `hbopop` | HBO Pop | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:hbopop` | embed-only |
| 44 | `hbosignature` | HBO Signature | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:hbosignature` | embed-only |
| 45 | `hboxtreme` | HBO Xtreme | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:hboxtreme` | embed-only |
| 46 | `hboplus` | HBO+ | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:hboplus` | embed-only |
| 47 | `hgtv` | HGTV | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:hgtv` | embed-only |
| 48 | `history` | History | Documentários | Servidor Premium | FULL HD | sim | `History.br` | stream |
| 49 | `history2` | History 2 | Documentários | Servidor Premium | FULL HD | sim | `rdc:history2` | embed-only |
| 50 | `jpnews` | JP News | Notícias | Servidor Premium | FULL HD | sim | `rdc:jpnews` | embed-only |
| 51 | `lifetime` | Lifetime | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:lifetime` | embed-only |
| 52 | `megapix` | Megapix | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:megapix` | embed-only |
| 53 | `modoviagem` | Modo Viagem | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:modoviagem` | embed-only |
| 54 | `multishow` | Multishow | Entretenimento | Servidor Alternativo, Servidor Premium | FULL HD | sim | `rdc:multishow` | embed-only |
| 55 | `nsports` | NSports | Esportes | Servidor Premium | FULL HD | sim | `NSports.br` | stream |
| 56 | `paramountplus` | Paramount+ | Esportes | Servidor Premium | FULL HD | sim | `rdc:paramountplus` | embed-only |
| 57 | `paramountplus2` | Paramount+ 2 | Esportes | Servidor Premium | FULL HD | sim | `rdc:paramountplus2` | embed-only |
| 58 | `paramountplus3` | Paramount+ 3 | Esportes | Servidor Premium | FULL HD | sim | `rdc:paramountplus3` | embed-only |
| 59 | `paramountplus4` | Paramount+ 4 | Esportes | Servidor Premium | FULL HD | sim | `rdc:paramountplus4` | embed-only |
| 60 | `premiereclubes` | Premiere | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `rdc:premiereclubes` | embed-only |
| 61 | `premiere2` | Premiere 2 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `rdc:premiere2` | embed-only |
| 62 | `premiere3` | Premiere 3 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `rdc:premiere3` | embed-only |
| 63 | `premiere4` | Premiere 4 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `rdc:premiere4` | embed-only |
| 64 | `premiere5` | Premiere 5 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `rdc:premiere5` | embed-only |
| 65 | `premiere6` | Premiere 6 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `rdc:premiere6` | embed-only |
| 66 | `premiere7` | Premiere 7 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `rdc:premiere7` | embed-only |
| 67 | `premiere8` | Premiere 8 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | não | `rdc:premiere8` | embed-only |
| 68 | `record` | Record | Canais Abertos | Servidor Premium | FULL HD | sim | `Record.br` | stream |
| 69 | `recordnews` | Record News | Notícias | Servidor Premium | FULL HD | sim | `RecordNews.br` | stream |
| 70 | `redetv` | RedeTV! | Canais Abertos | Servidor Premium | FULL HD | sim | `RedeTV.br` | stream |
| 71 | `saborearte` | Sabor e Arte | Canais Abertos | Servidor Premium | FULL HD | sim | `rdc:saborearte` | embed-only |
| 72 | `sbt` | SBT | Canais Abertos | Servidor Premium | FULL HD | sim | `SBTNacional.br` | stream |
| 73 | `sbtnews` | SBT News | Notícias | Servidor Premium | FULL HD | sim | `SBTNews.br` | stream |
| 74 | `sonychannel` | Sony Channel | Filmes e Séries | Servidor Premium | FULL HD | sim | `SonyChannel.br` | stream |
| 75 | `sonymovies` | Sony Movies | Filmes e Séries | Servidor Premium | FULL HD | não | `SonyMovies.br` | stream |
| 76 | `space` | Space | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:space` | embed-only |
| 77 | `sportv` | Sportv | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `SporTV.br` | stream |
| 78 | `sportv2` | Sportv 2 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `SporTV2.br` | stream |
| 79 | `sportv3` | Sportv 3 | Esportes | Servidor Alternativo, Servidor Premium | FULL HD, HD | sim | `SporTV3.br` | stream |
| 80 | `sportynet` | SportyNet | Esportes | Servidor Premium | FULL HD | sim | `rdc:sportynet` | embed-only |
| 81 | `sportynetplus` | SportyNet+ 1 | Esportes | Servidor Premium | FULL HD | sim | `rdc:sportynetplus` | embed-only |
| 82 | `sportynetplus2` | SportyNet+ 2 | Esportes | Servidor Premium | FULL HD | sim | `rdc:sportynetplus2` | embed-only |
| 83 | `sportynetplus3` | SportyNet+ 3 | Esportes | Servidor Premium | FULL HD | sim | `rdc:sportynetplus3` | embed-only |
| 84 | `studiouniversal` | Studio Universal | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:studiouniversal` | embed-only |
| 85 | `telecineaction` | Telecine Action | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:telecineaction` | embed-only |
| 86 | `telecinecult` | Telecine Cult | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:telecinecult` | embed-only |
| 87 | `telecinefun` | Telecine Fun | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:telecinefun` | embed-only |
| 88 | `telecinepipoca` | Telecine Pipoca | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:telecinepipoca` | embed-only |
| 89 | `telecinepremium` | Telecine Premium | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:telecinepremium` | embed-only |
| 90 | `telecinetouch` | Telecine Touch | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:telecinetouch` | embed-only |
| 91 | `tlc` | TLC | Entretenimento | Servidor Premium | FULL HD | sim | `rdc:tlc` | embed-only |
| 92 | `tnt` | TNT | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:tnt` | embed-only |
| 93 | `tntnovelas` | TNT Novelas | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:tntnovelas` | embed-only |
| 94 | `tntseries` | TNT Séries | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:tntseries` | embed-only |
| 95 | `tooncast` | Tooncast | Infantil | Servidor Premium | FULL HD | não | `rdc:tooncast` | embed-only |
| 96 | `aparecida` | TV Aparecida | Canais Abertos | Servidor Premium | FULL HD | sim | `TVAparecida.br` | stream |
| 97 | `tvbrasil` | TV Brasil | Canais Abertos | Servidor Premium | FULL HD | sim | `TVBrasil.br` | stream |
| 98 | `tvcultura` | TV Cultura | Canais Abertos | Servidor Premium | FULL HD | sim | `TVCultura.br` | stream |
| 99 | `universaltv` | Universal TV | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:universaltv` | embed-only |
| 100 | `usa` | USA | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:usa` | embed-only |
| 101 | `warnertv` | Warner TV | Filmes e Séries | Servidor Premium | FULL HD | sim | `rdc:warnertv` | embed-only |
| 102 | `woohoo` | WooHoo | Entretenimento | Servidor Premium | FULL HD | sim | `WooHoo.br` | stream |
| 103 | `xsports` | XSports | Esportes | Servidor Premium | FULL HD | sim | `rdc:xsports` | embed-only |

Distribuição por categoria (dos 103): Filmes e Séries 30, Esportes 25, Entretenimento 18,
Documentários 9, Canais Abertos 8, Notícias 7, Infantil 6 (o 104º canal, `globo`, também em
Canais Abertos, não usa Premium).

## 5. Canais exclusivos (classificação A/B/C/D)

- **A. Canais exclusivos do conjunto "Premium": NENHUM.** Premium não é um conjunto
  separado — os canais que carregam o rótulo são exatamente os de `GET /channels`.
- **B. Canais que também aparecem na API principal: os 103.** São os mesmos registros
  públicos da API principal (o rótulo Premium vive dentro deles).
- **C. Canais que usam Premium como fonte alternativa: os 103.** Em 14 deles há **duas**
  fontes alternativas rotuladas (`Servidor Premium` + `Servidor Alternativo`).
- **D. Sem evidência suficiente: 0.** Não há registro público órfão nem rota privada
  associada ao termo "Premium".

## 6. Canais duplicados

- **Duplicação interna (Premium × API): nenhuma.** Premium é um campo dentro do embed do
  próprio canal; não gera um segundo canal.
- **RDC × iptv-org (mesma emissora):** 29 canais RDC possuem stream reproduzível de mesma
  emissora no iptv-org e o app **reutiliza o id iptv** (sem segunda entrada). Os outros 75
  permanecem catalog-only (`rdc:<id>`). Não há número duplicado na grade.
- **Legado M3U × RDC:** nomes como `Telecine Premium`, `Premiere` também existem no
  `m3u_catalog.json` legado, mas o catálogo BR legado está **desativado**
  (`CatalogFilters.LEGACY_BR_CATALOG_ENABLED = false`); a única parte preservada do M3U é o
  adulto. **Não reativar listas antigas.**
- Risco a vigiar se um dia Premium virar bloco próprio: duplicar os mesmos canais já
  trazidos pela API (por isso a recomendação da §12 é **não** criar bloco Premium).

## 7. Formatos de reprodução

- `quality` observada: **FULL HD** 132 embeds, **HD** 11, **SD** 1.
- Formato real do `embed_url`: **página HTML** (`https://rdcanais.org/<slug>`), adequada a
  `<iframe>`/WebView — **nunca** `.m3u8`/`.ts` (0 em 144). O campo `quality` é um rótulo
  anunciado; não é prova de resolução real.
- O site declara indexar mídia hospedada em terceiros, sem armazenar arquivos. **Uma URL de
  embed não é prova de autorização de redistribuição** (§08 do protocolo).

## 8. Situação do EPG

- **98/104** canais com EPG no instante do teste; os 103 canais Premium seguem essa
  proporção (apenas `cazetv`, `cazetv2`, `cazetv3`, `premiere8`, `sonymovies`, `tooncast`
  aparecem sem EPG — 6 no total, coincidindo com os 6 ausentes gerais).
- EPG traz somente `current`/`next` (sem grade de 7/14 dias) — limitação já documentada na
  `02_CONTRATO_API.md`.

## 9. Compatibilidade com a arquitetura híbrida

- A arquitetura híbrida (Fonte A = identidade/catálogo/EPG/regionalização; iptv-org =
  reprodução da mesma emissora) **já consome** os canais rotulados Premium via
  `/channels`; nada precisa mudar para "incluí-los".
- `RdcCatalogMapper.regionForEmbedProvider("Servidor Premium")` retorna `null` (genérico →
  nacional) — comportamento correto e inalterado na Etapa 11.
- Canais embed-only permanecem `isAvailable = false`, sem entregar HTML ao ExoPlayer.

## 10. Condições de acesso conhecidas

- API: gratuita, **sem autenticação**, JSON, sem chave. Premium **não** introduz condição
  distinta (sem token, sem header, sem parâmetro).
- Não foi identificada área paga/privada associada a "Premium"; o rótulo é apenas
  nomenclatura de provedor. Não se buscou nem se tentou contornar qualquer controle.

## 11. Viabilidade de integração

- **Não há ganho em tratar Premium separadamente.** Não existe endpoint "Premium"; os
  canais já estão no catálogo; não há reprodução direta (só página de embed).
- Integração "Premium" como nova fonte **não é viável** sem (a) extrair stream de páginas
  de terceiros (proibido/fora de escopo) ou (b) incorporar a página via WebView — o que a
  Etapa 11 já faz para os canais de rede fixa.
- Conclusão: **Premium = fonte alternativa já presente e já contemplada**; nenhum canal
  novo a incorporar.

## 12. Proposta de ordenação

A ordenação sugerida (1. fixos 1–9 · 2. Premium · 3. demais RDC · 4. iptv-org) **não se
sustenta** porque Premium não é um conjunto distinto: inserir o bloco 2 repetiria canais já
trazidos pelo bloco 3. Recomenda-se manter a ordenação vigente (Etapa 10):

1. **Fixos 1–9** (Globo, SBT, Record, RedeTV!, Gazeta, Band, Cultura, TV Centro-Sul, TV Litoral RN).
2. **Fonte A (RDC)** — todos os 104 canais, incluídos os 103 com rótulo Premium.
3. **Complementares iptv-org** — demais canais BR e internacionais.
4. **Adulto** (preservado do M3U legado).

Alternativa (não implementar agora, só registrar): um **selo de proveniência de fonte**
(ex.: `PREMIUM`/`ALTERNATIVO`/`REGIONAL`) exibido no detalhe do canal, sem criar novas
entradas — usa o `provider` já disponível no embed.

## 13. Pendências para implementação

1. **Decisão de produto:** manter a grade atual (recomendado) ou adicionar selo de fonte —
   requer autorização; **nada foi alterado nesta etapa**.
2. **Atualizar `02_CONTRATO_API.md`:** corrigir "afiliadas Globo 8×" → **27×** (doc
   desatualizado; contagem atual de 144 embeds está correta).
3. **Validação em TV física (Etapa 11):** confirmar se os embeds `rdcanais.org/<slug>`
   (incluindo os rotulados Premium) reproduzem em WebView e se exigem referrer/UA
   específicos — sem inventar resultados.
4. **Verificação periódica:** nova captura de `/channels` para confirmar que o rótulo
   `Servidor Premium` permanece (hoje 103×) e que não surge categoria/endpoint novo.
5. **Aspecto legal:** registrar que embed ≠ autorização de redistribuição; incorporação só
   dentro do comportamento já existente (iframe/WebView), sem extração de stream.

---

### Resumo técnico

**"Servidor Premium" é um rótulo de provedor em `embeds`, não um servidor.** Os 103 canais
a ele associados são os próprios canais públicos de `GET /channels` (89 só-Premium, 14
Premium+Alternativo; o único canal sem Premium é `globo`, com 27 embeds regionais). Todos os
144 embeds apontam para páginas `https://rdcanais.org/<slug>` (FULL HD 132/HD 11/SD 1), sem
autenticação e sem endpoint dedicado. **Não existem canais exclusivos Premium** — portanto
não há novos canais a incorporar e não se deve criar um bloco "Premium" na grade, sob risco
de duplicação. Aguardando autorização antes de qualquer alteração.
