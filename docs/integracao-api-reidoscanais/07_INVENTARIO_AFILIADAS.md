# 07 — INVENTÁRIO DAS AFILIADAS

> Auditoria Etapa 04 — 2026-10-16. Evidências: código (`ChannelNumbering.kt`, `ChannelRepository.kt`), `assets/m3u_catalog.json` (embarcado), iptv-org oficial baixado em 2026-10-16 (`channels.json` 7,6 MB / `streams.json` 3,6 MB), API Rei dos Canais consultada ao vivo. **Nenhuma afiliada é presumida por nome** — todas as entradas reproduzíveis foram confirmadas por ID real nas fontes.

## 1. Redes nacionais identificadas (canais abertos)

| Rede | Cabeça de rede (fonte reproduzível) | ID real | Fonte | EPG RDC |
|---|---|---|---|---|
| Globo | Rede Globo (nacional) | `RedeGlobo.br` (2 streams) | iptv-org | sim (27 embeds regionais — páginas web) |
| SBT | SBT Nacional | `SBTNacional.br` (3 streams) | iptv-org | sim (nacional única) |
| Record | Record | `Record.br` (3 iptv + 5 m3u) | iptv-org + m3u | sim (nacional única) |
| RedeTV! | RedeTV! | `RedeTV.br` (6 streams) | iptv-org | sim (nacional única) |
| Band | Band | `Band.br` (2 iptv + `band.br` 4 m3u) | iptv-org + m3u | sim (nacional única) |
| Gazeta | TV Gazeta (SP) | `tv_gazeta.br` (1 stream) | m3u | não (ausente da RDC) |
| TV Cultura | TV Cultura | `TVCultura.br` (10 streams) | iptv-org | sim (nacional única) |
| TV Brasil | TV Brasil | `TVBrasil.br` (7 streams) | iptv-org | sim (nacional única) |

**Emissoras independentes com posição fixa:** TV Centro-Sul (CE — canal custom streamlock, posição 8, ver doc 03) e TV Litoral RN (`TVLitoralRN.br`, 1 stream iptv, posição 9).

## 2. Afiliadas regionais com reprodução CONFIRMADA (fontes atuais)

Convenção: ID = identificador real na fonte; S = streams reproduzíveis; Conf = grau de confiança da identificação rede/UF (alta = nome da afiliada inequívoco na fonte; média = dedução por cobertura/cidade).

### Globo — 27/27 UFs cobertas (todas no m3u_catalog embarcado)

| UF | Emissora afiliada | Cidade/cobertura | ID real (m3u) | S | Conf |
|----|---|---|---|---|---|
| AC | Rede Amazonas Rio Branco | Rio Branco | `globo_rede_amazonica_rio_branco.br` | 3 | alta |
| AL | TV Gazeta Alagoas | Maceió | `globo_tv_gazeta_alagoas.br` | 3 | alta |
| AP | Rede Amazonas Macapá | Macapá | `globo_rede_amazonica_amapa.br` | 3 | alta |
| AM | Rede Amazonas Manaus | Manaus | `globo_rede_amazonica_manaus.br` | 3 | alta |
| BA | TV Bahia | Salvador | `globo_rede_bahia.br` | 3 | alta |
| CE | TV Verdes Mares | Fortaleza | `globo_tv_verdes_mares.br` | 4 | alta |
| DF | TV Globo Brasília | Brasília | `globo_brasilia.br` | 3 | alta |
| ES | TV Gazeta Vitória | Vitória | `globo_tv_gazeta_vitoria.br` | 3 | alta |
| GO | TV Anhanguera | Goiânia | `globo_tv_anhanguera.br` | 3 | alta |
| MA | TV Mirante | São Luís | `globo_tv_mirante_sao_luis.br` | 3 | alta |
| MT | TV Centro América | Cuiabá | `globo_tv_centro_america.br` | 3 | alta |
| MS | TV Morena | Campo Grande | `globo_tv_morena.br` | 3 | alta |
| MG | TV Globo Minas | Belo Horizonte | `globo_minas.br` | 3 | alta |
| PA | TV Liberal | Belém | `globo_tv_liberal_belem.br` | 3 | alta |
| PB | TV Cabo Branco | João Pessoa | `globo_tv_cabo_branco.br` | 3 | alta |
| PR | RPC TV Curitiba | Curitiba | `globo_rpc_tv_curitiba.br` | 3 | alta |
| PE | TV Globo Nordeste | Recife | `globo_nordeste.br` | 3 | alta |
| PI | TV Clube | Teresina | `globo_tv_clube_teresina.br` | 3 | alta |
| RJ | TV Globo RJ | Rio de Janeiro | `globo_rj.br` | 3 | alta |
| RN | Inter TV Cabugi | Natal | `globo_inter_tv_cabugi.br` | 3 | alta |
| RS | RBS TV Porto Alegre | Porto Alegre | `globo_rbs_tv_porto_alegre.br` | 3 | alta |
| RO | Rede Amazonas Porto Velho | Porto Velho | `globo_rede_amazonica_porto_velho.br` | 3 | alta |
| RR | Rede Amazonas Boa Vista | Boa Vista | `globo_rede_amazonica_boa_vista.br` | 3 | alta |
| SC | NSC TV Florianópolis | Florianópolis | `globo_nsc_tv_florianopolis.br` | 3 | alta |
| SP | TV Globo SP | São Paulo | `globo_sp.br` | 4 | alta |
| SE | TV Sergipe | Aracaju | `globo_tv_sergipe.br` | 3 | alta |
| TO | TV Anhanguera Palmas | Palmas | `globo_tv_anhanguera_palmas.br` | 3 | alta |

(+ afiliadas de interior também presentes: EPTV Campinas/Ribeirão Preto/São Carlos/Sul de Minas, Inter TV Alto Litoral/Costa Branca/dos Vales/Serra Mar/Grande Minas, NSC TV Blumenau/Chapeco/Criciúma/Joinville/Centro Oeste, RBS TV Bagé/Caxias/Erechim/Passo Fundo/Pelotas/Santa Maria/Santa Rosa/Uruguaiana/Santa Cruz, RPC Cascavel/Londrina/Maringá/Foz/Ponta Grossa/Guarapuava/Paranavaí, TEM Bauru/Sorocaba/Itapetininga/S.J. Rio Preto, Vanguarda, Tribuna Santos, Rio Sul, Asa Branca, São Francisco, Subaé, Sudoeste, Grande Rio, Diario Mogi das Cruzes, Fronteira, Alvorada do Sul, Anhanguera Anápolis/Itumbiara/Luziânia/Rio Verde, Centro América Rondonópolis/Sinop, Mirante Cocais/Imperatriz, Liberal Castanhal/Marabá, Morena Ponta Porã, Verdes Mares Cariri, Rede Bahia TV Oeste/Santa Cruz, TV Gazeta Sul — todas com 2–3 streams.)

### SBT — 6 UFs cobertas

| UF | Emissora | ID real | Fonte | S | Conf |
|----|---|---|---|---|---|
| RJ | SBT RJ | `sbt_rj.br` | m3u | 3 | alta |
| SP | SBT SP | `sbt_sp.br` | m3u | 4 | alta |
| RS | SBT RS | `sbt_rs.br` | m3u | 2 | alta |
| PR | SBT Rede Massa | `sbt_rede_massa_pr.br` | m3u | 1 | alta |
| MG | SBT TV Alterosa | `sbt_tv_alterosa.br` | m3u | 2 | alta |
| MT | SBT Cuiabá | `SBTCuiaba.br` | iptv-org | 1 | alta |

(+ interior SP: SBT Ribeirão Preto, SBT Thathi Vale, SBT VTV Santos Campinas; interior MT: SBT Nova Mutum/Sinop/Rondonópolis via iptv.)
Sem cobertura: TV Jangadeiro (CE), TV Aratu (BA), TV Jornal (PE), TV Cidade Verde (PI), TV Tamba (PB), TV Ponta Verde (AL), TV Serra Dourada (GO), TV Allamanda (RO), TV Atalaia (SE), TV Brasília (DF) — **não encontradas em nenhuma fonte reproduzível** (ids do affiliateMap como `sbt_ce_jangadeiro.br` não existem nem no m3u nem no iptv-org atual).

### Record — 15 UFs cobertas

| UF | Emissora | ID real | Fonte | S | Conf |
|----|---|---|---|---|---|
| RJ | RecordTV RJ | `recordtv_rj.br` | m3u | 3 | alta |
| SP | RecordTV SP | `recordtv_sp.br` | m3u | 4 | alta |
| RS | RecordTV RS | `recordtv_rs.br` | m3u | 3 | alta |
| BA | RecordTV Itapoan | `record_tvitapoan.br` | m3u | 1 | alta |
| AM | RecordTV Manaus | `recordtv_manaus.br` | m3u | 3 | alta |
| PE | RecordTV Guararapes | `recordtv_guararapes.br` | m3u | 3 | alta |
| PI | RecordTV Piauí (A10) | `recordtv_piaui_a10.br` | m3u | 3 | alta |
| RR | RecordTV Roraima | `recordtv_roraima.br` | m3u | 3 | alta |
| RO | RecordTV SIC | `recordtv_sic_rondonia.br` | m3u | 1 | alta |
| PA | RecordTV Belém | `record_tvbelem.br` | m3u | 1 | alta |
| DF | RecordTV Brasília | `record_tvbrasilia.br` | m3u | 1 | alta |
| GO | RecordTV Goiás | `record_tvgoias.br` | m3u | 1 | alta |
| ES | RecordTV Vila Real | `recordtv_vila_real.br` | m3u | 2 | **média** |
| MG | RecordTV Paranaíba | `recordtv_paranaiba_de_uberlandia.br` | m3u | 2 | **média** |
| PB | TV Cidade/RecordTV | `tv_cidade_recordtv.br` | m3u | 5 | **média** |

(+ interior: RecordTV RJ Interior, Rio Preto, Ribeirão, Thathi Campinas, Paulista, Leste, Litoral/Vale do Parnaíba, Interior SP.) Sem cobertura: CE (RecordTV Cidade), AL, MA, RN, SC (NDTV Criciuma existe no iptv com 0 streams), TO, MS, AP, AC, SE.

### Band — 1 UF coberta

| UF | Emissora | ID real | Fonte | S | Conf |
|----|---|---|---|---|---|
| SP | Band SP | `band_sp.br` | m3u | 4 | alta |

BandBahia/Ceara/Curitiba/Minas/Amazonas/Rede/Triangulo/Rio Interior/São Paulo Interior/RS existem no iptv-org **sem streams** (0 reproduzíveis). Cobertura regional Band: **PENDENTE** para 26 UFs.

### Gazeta, RedeTV!, TV Cultura, TV Brasil — 0 afiliadas

Sem estrutura de afiliação reproduzível em nenhuma fonte; operam como canal único nacional (exatamente como o affiliateMap atual trata a Gazeta).

## 3. Corroboração pela API Rei dos Canais

- Globo: **27 embeds regionais (1 por UF)** na RDC — `rdcanais.org/globo{uf}` para todas as 27 UFs. Confirma a existência de sinal regional por UF como conceito, mas são **páginas web não reproduzíveis** — apenas metadado.
- SBT/Record/Band/RedeTV!/TV Cultura/TV Brasil: **1 embed nacional cada** — sem regionalização na RDC.
- EPG: nacional por rede (o EPG do canal "globo" é único; embeds regionais não têm EPG próprio). **Não há EPG por afiliada.**

## 4. Totais do inventário

| Métrica | Valor |
|---|---|
| Redes nacionais com canal aberto | 8 (Globo, SBT, Record, RedeTV!, Band, Gazeta, TV Cultura, TV Brasil) |
| Independentes com posição fixa | 2 (TV Centro-Sul, TV Litoral RN) |
| Afiliadas regionais reproduzíveis confirmadas | **49** (Globo 27, SBT 6, Record 15, Band 1) |
| UFs com ≥1 afiliada reproduzível | **27/27** (todas via Globo) |
| Afiliadas com EPG regional | **0** (RDC fornece EPG apenas nacional por rede) |
