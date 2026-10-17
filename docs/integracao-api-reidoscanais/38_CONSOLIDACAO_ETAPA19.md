# 38 — CONSOLIDAÇÃO DA ETAPA 19 (AUDITORIA DO MOTOR DE REPRODUÇÃO)

> Etapa 19 — auditoria integral do motor de reprodução + correção incremental
> fundamentada. Uma única correção de comportamento foi implementada (**guarda de
> MÍDIA do motor web**, §6). Nenhuma identidade/canal foi adicionado, removido ou
> renumerado. Sem WebView interna nova, sem commit/push, sem release, APK não instalado.

## 1. Escopo e método

Reconstrução documental do caminho completo:

```
número do canal → identidade (channel_numbers) → fontes (iptv-org + Fonte A)
   → plano ordenado (PlaybackResolver/RegionalSourcePlanner)
   → mecanismo (DIRECT_STREAM | WEB_EMBED | UNAVAILABLE)
   → inicialização → reprodução → recuperação (ExoPlayerManager | WebPlayer)
```

Arquivos auditados (somente leitura, exceto a correção do §6):
`PlayerViewModel.kt`, `PlayerScreen.kt`, `ExoPlayerManager.kt`, `WebPlayer.kt`,
`WebSessionPolicy.kt`, `PlaybackResolver.kt`, `RegionalSourcePlanner.kt`,
`VideoFallbackPolicy.kt`, `StreamPreference.kt`, `PlaybackState.kt`,
`AffiliateRegistry.kt`, `RdcCatalogMapper.kt`, `ReiDosCanaisRepository.kt`.

## 2. Caminho de recuperação — como está hoje

| Mecanismo | Falha detectável | Recuperação |
|---|---|---|
| DIRECT_STREAM (Exo) | erro de player, timeout de buffer, congelamento de posição, vídeo sem quadro | repetições transitórias (2) → `tryNextStream` (streams internos) → `onError` → `advanceAttempt` (próxima fonte) |
| WEB_EMBED | erro/SSL do documento principal, renderizador morto, timeout de carregamento (**antes** do load) | `onWebError` → `advanceAttempt` |
| WEB_EMBED | **página carrega mas nunca inicia mídia** | **antes: nenhuma** → agora: guarda de mídia (§6) |
| Plano esgotado | — | `noSignal = true`, estado `UNAVAILABLE` (canal permanece na grade) |

Garantia central preservada: **falha de reprodução nunca remove/renumera o canal**.

## 3. Pontos de falha identificados (P01–P11)

| Código | Achado na auditoria | Situação |
|---|---|---|
| P01 Globo não inicia sozinha | `mediaPlaybackRequiresUserGesture=false` já definido; o autoplay é do JS do embed (`autoPlay:true`), fora do controle do app. | limite externo; documentado (doc 40) |
| P02 Globo não recupera | Embed carrega a página (200) e o guarda de carregamento é desarmado em `onPageFinished`; nenhum evento escala falha depois. | **corrigido** (§6) |
| P03 GNT não reproduz | Canal não fixo → plano `resolve()` com embed(s) da Fonte A; embed morto ficava preso. | **corrigido** (§6) |
| P04 SporTV | idem P03. | **corrigido** (§6) |
| P05 Premiere | idem P03. | **corrigido** (§6) |
| P06 Gloob cai após início | Queda por renderizador/erro de documento já escalava; queda por parada de mídia interna não. | **corrigido** (§6) |
| P07 áudio sem vídeo | Tratado para DIRECT_STREAM (`PlaybackStates`/`VideoFallbackPolicy`); em WEB_EMBED é intraduzível por same-origin. | limite externo; documentado |
| P08 longas esperas | Guarda de carregamento 15 s; guarda de mídia 15 s; Exo 5 s/8 s + 2 retries. | documentado (doc 39) |
| P09 UI do player web na sintonia | A própria UI do embed é exibida; não injetamos CSS/JS (não contornamos o player). | limite externo |
| P10 embeds com tecnologias diferentes | Inventário em doc 41; a observação de mídia é agnóstica de player. | **coberto** |
| P11 preservar grade | Nenhuma alteração de catálogo/numeração nesta etapa. | **garantido** |

## 4. Ordem de fallback regional (verificada, sem alteração)

`RegionalSourcePlanner` já produz, para a **Globo** (`REFERENCE_BEFORE_HEAD_NETWORKS`):

```
UF → SP → RJ → cabeça nacional → outras afiliadas → sobras → indisponível
```

e, para as demais redes: `UF → cabeça → SP/RJ → outras → sobras → indisponível`.
`MAX_OTHER_AFFILIATES = 3`, `MAX_ATTEMPTS = 8`. **Nada a corrigir no planejador.**

## 5. Preservação do catálogo (Etapa 18)

A correção desta etapa vive inteiramente em `WebPlayer`/`WebSessionPolicy` (apresentação
+ política pura). Não toca `ChannelNumbering`, `ChannelRepository`, ledger
`approved_catalog_ledger_v1`, semente `approved_catalog_seed.json` nem a numeração.
`isAvailable`/sucesso de reprodução continuam sem poder remover/admitir canais.

## 6. Correção implementada — guarda de MÍDIA do motor web

**Problema**: um embed pode carregar o documento e nunca iniciar a mídia (player interno
com erro, origem sem sinal, gesto exigido). Após `onPageFinished`, o guarda de
carregamento é desarmado — a WebView ficava presa para sempre.

**Evidência legítima disponível**: a própria WebView requisita a playlist/segmento
(`.m3u8/.mpd/.ts/.m4s/...`), inclusive dentro do iframe de outra origem, pois tudo passa
pelo `WebViewClient.shouldInterceptRequest`. Isso **não** lê o `<video>` e **não** contorna
restrições de origem.

**Regra** (`WebSessionPolicy.decideMediaTimeout`, pura e testável):

```
falha ⇔ documento carregou  E  nenhuma requisição de mídia observada na janela
```

- `WebPlayer` arma o guarda em `onPageFinished` (janela `WEB_MEDIA_GRACE_MS = 15 s`).
- Qualquer requisição de mídia cancela o guarda (**nunca** falha com mídia observada).
- Ao estourar sem mídia: `onError("Sem mídia no player web")` → `advanceAttempt` → próxima
  fonte autorizada; esgotado o plano, `SEM SINAL` (canal permanece na grade).
- O guarda é cancelado em troca de URL, erro de página/SSL, renderizador morto e descarte.

**O que NÃO é afirmado**: observar requisição de mídia não é "quadro renderizado". Não
declaramos reprodução confirmada; apenas evitamos escalar falha quando há mídia.

## 7. Testes e build

- `WebSessionPolicyTest`: +5 casos (`decideMediaTimeout` ×3, `isMediaRequest` ×2).
- Suíte `util.*` + `reidoscanais.*`: **147 testes, 0 falhas** (142 → 147).
- APK Debug: `app/build/outputs/apk/debug/app-debug.apk`
  - tamanho **17.946.213 bytes**
  - sha256 `964e47e35b2be66ca05d64b4321ad8c95f741da4248cf9989a69df621bc913c9`

## 8. Limitações (não validado em dispositivo)

Sem ADB/TV física. Efeitos reais de autoplay, fullscreen e detecção de mídia em cada
provedor **não** foram observados. Casos de borda a validar: player que inicia mídia só
após gesto (agora tratado como falha e escalado — comportamento pretendido), e players que
busquem mídia via Service Worker (podem não passar por `shouldInterceptRequest`). Coleta no
aparelho: versão do System WebView e `logcat -s WebMotor WebProbe AvDiag`.
