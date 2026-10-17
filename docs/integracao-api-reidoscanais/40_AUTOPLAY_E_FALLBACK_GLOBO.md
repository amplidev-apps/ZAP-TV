# 40 — AUTOPLAY E FALLBACK GLOBO (ETAPA 19)

> Análise do autoplay do motor web e da cadeia de fallback da Globo. **Não** foi
> alterado o planejador regional (já correto) nem afrouxada qualquer política de
> segurança/autoplay para "forçar" reprodução.

## 1. Autoplay (P01) — o que o app controla

`WebPlayer` define:

```kotlin
settings.mediaPlaybackRequiresUserGesture = false   // Android TV não tem toque
javaScriptEnabled = true; domStorageEnabled = true
mixedContentMode = MIXED_CONTENT_NEVER_ALLOW        // nunca conteúdo misto
```

- Isso remove a exigência de gesto do **Android WebView**.
- **Não** controla o `autoPlay` do player embarcado (Fonte A: Clappr `autoPlay:true` +
  hls.js). Se o próprio site só iniciar após clique, o app **não** automatiza gestos
  (proibido pela diretriz).
- Fullscreen (`Element.requestFullscreen`) é tratado por
  `WebChromeClient.onShowCustomView/onHideCustomView` (Etapa 14 §01), anexando a View a um
  contêiner real. Se o player exigir gesto para entrar em fullscreen, é limite externo.

**Conclusão P01**: nenhuma mudança de código é segura além do já existente. Qualquer
"forçar autoplay" (injeção de JS/gesto sintético) violaria a diretriz e ficou de fora.

## 2. Fallback Globo — cadeia já correta

`RegionalSourcePlanner.planFixedNetwork` para a rede Globo
(`REFERENCE_BEFORE_HEAD_NETWORKS = {"Globo"}`):

```
1. afiliada da UF selecionada
2. referência SP
3. referência RJ
4. cabeça nacional
5. outras afiliadas (máx. 3)
6. sobras de embeds
7. indisponível
```

A transmissão da UF é **sempre** a primeira candidata quando existe fonte compatível; cada
fonte é tentada uma única vez (`MAX_ATTEMPTS = 8`), sem alternâncias infinitas. As
associações vêm só da API (`Globo <Estado>`) e do `AffiliateRegistry` — nunca de
inferência por URL.

## 3. O que efetivamente fazia a Globo "não recuperar" (P02)

O planejador entrega várias fontes, mas o **motor web não escalava falha** quando um embed
carregava a página e ficava sem mídia. Ou seja, a *segunda* fonte da fila nunca era
alcançada. A correção é o **guarda de mídia** (docs 38 §6 e 39): esgotada a janela sem
requisição de mídia, o motor avança para a próxima fonte da fila — que é exatamente a
cadeia SP → RJ → cabeça → outras já planejada.

## 4. Limites

- Não há confirmação audiovisual em iframe de outra origem (same-origin policy).
- A preferência de resolucao/variante é da fonte; não inventamos variantes.
- Resultado só é conclusivo com observação em dispositivo (doc 39 §5).
