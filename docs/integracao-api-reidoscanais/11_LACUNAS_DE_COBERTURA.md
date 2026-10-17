# 11 — LACUNAS DE COBERTURA

> O que NÃO existe nas fontes reproduzíveis em 2026-10-16. Nenhum canal é declarado funcional sem stream confirmado. Nenhum substituto inventado.

## 1. Lacunas por rede

| Rede | UFs cobertas | UFs descobertas (sem afiliada reproduzível) | % coberto |
|---|---|---|---|
| Globo | 27 | 0 | 100% |
| SBT | 6 | 21 | 22% |
| Record | 15 | 12 | 56% |
| Band | 1 | 26 | 4% |
| Gazeta | — (rede única) | 27 (canal único nacional) | n/a |
| RedeTV! | — (rede única) | 27 (RedeTV.br nacional) | n/a |
| TV Cultura | — (rede única) | 27 (TVCultura.br nacional) | n/a |
| TV Brasil | — (rede única) | 27 (TVBrasil.br nacional) | n/a |

## 2. Lacunas detalhadas por estado (rede sem afiliada regional)

| UF | SBT | Record | Band |
|---|---|---|---|
| AC | falta | falta | falta |
| AL | falta | falta | falta |
| AP | falta | falta | falta |
| AM | falta | — | falta |
| BA | falta | — | falta |
| CE | falta | falta | falta |
| DF | falta | — | falta |
| ES | falta | — (média) | falta |
| GO | falta | — | falta |
| MA | falta | falta | falta |
| MT | — | falta | falta |
| MS | falta | falta | falta |
| MG | — | — (média) | falta |
| PA | falta | — | falta |
| PB | falta | — (média) | falta |
| PR | — | falta | falta |
| PE | falta | — | falta |
| PI | falta | — | falta |
| RJ | — | — | falta |
| RN | falta | falta | falta |
| RS | — | — | falta |
| RO | falta | — | falta |
| RR | falta | — | falta |
| SC | falta | falta | falta |
| SP | — | — | — |
| SE | falta | falta | falta |
| TO | falta | falta | falta |

("—" = coberta.)

## 3. Canais presentes no iptv-org mas SEM stream reproduzível (não utilizáveis)

Estes IDs existem no catálogo iptv-org, mas com **0 streams não-bloqueados** → **não** entram como afiliada funcional:

- SBT: `SBTRio.br`, `SBTPara.br`, `SBTRioGrande.br`, `SBTSinop.br`, `SBTMS.br`.
- Record: `RecordTVCabralia.br`, `RecordTVAmericas.br`, `RecordTV Criciuma` (SC), demais praças SC.
- Band: `BandRS.br`, `BandSPInterior.br`, `BandBahia.br`, `BandCeara.br`, `BandCuritiba.br`, `BandMinas.br`, `BandAmazonas.br`, `BandRede.br`, `BandTriangulo.br`, `BandGoiania.br`, `BandTribuna.br`, `BandRioInterior.br`, `BandAlternativo.br`, `BandMais.br`.
- Globo (interior): `RedeGlobo.br` variantes regionais sem stream.
- RedeTV!, TV Cultura, TV Brasil: praças regionais sem stream (só a cabeça nacional reproduz).

## 4. Limitações estruturais das fontes

1. **M3U embarcado (`assets/m3u_catalog.json`)** — concentra quase toda a cobertura reproduzível (Globo 27 UFs, Record, SBT, Band SP). É a fonte primária da regionalização proposta.
2. **iptv-org (2026-10-16)** — a maior parte das afiliadas regionais está cadastrada **sem stream**; contribui para Globo (RedeGlobo nacional), SBT (SBTNacional + Cuiabá), Record (Record nacional), RedeTV!, TV Cultura, TV Brasil. **Nenhum ID regional do `affiliateMap` atual existe no iptv-org de hoje.**
3. **API Rei dos Canais** — os 27 embeds regionais da Globo são **páginas web** (`rdcanais.org/globо{uf}`), **não streams**. Portanto **não reproduzem** e não podem substituir uma afiliada. Servem apenas como metadado/indicação de existência regional.
4. **EPG** — inexistente por afiliada. A RDC entrega EPG nacional por rede. Regiões exibirão EPG da cabeça de rede.

## 5. Lacunas que a implementação NÃO consegue resolver

- Afiliadas SBT na maioria das UFs, Record em ~12 UFs e Band em ~26 UFs **não têm stream público** conhecido → permanecerão no sinal da cabeça de rede.
- EPG local por praça → indisponível.
- Interior de vários estados (praças pequenas) → apenas as afiliadas listadas no doc 07 estão reproduzíveis; praças sem stream não entram.
- Não há como reproduzir os embeds regionais da Globo da RDC (são páginas, não players) sem violar a regra de "não reproduzir sem stream verificável".
