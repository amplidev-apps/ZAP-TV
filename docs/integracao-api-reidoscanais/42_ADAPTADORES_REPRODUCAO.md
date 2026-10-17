# 42 — ADAPTADORES DE REPRODUÇÃO (ETAPA 19)

> Mapa dos componentes que compõem o motor de reprodução e a decisão de **não** criar
> adaptadores por player. Descreve as costuras (seams) entre identidade, fonte, mecanismo
> e recuperação.

## 1. Componentes e responsabilidades

| Componente | Papel | Não faz |
|---|---|---|
| `ChannelRepository` | identidade, grade, ledger aprovado, streams da Fonte B | não decide mecanismo |
| `ReiDosCanaisRepository` | metadados/EPG/embeds da Fonte A | não reproduz |
| `PlaybackResolver` | escolhe mecanismo (DIRECT/WEB) e ordena tentativas | não executa |
| `RegionalSourcePlanner` | regionalização (UF→SP→RJ→cabeça→outras) | só rede fixa 1–9 |
| `SourcePreferenceRegistry` | preferência manual de fonte (`novas regras.md`) | não remove fontes |
| `StreamPreference` | ordena streams (estabilidade/qualidade/URL) | mesmo canal |
| `ExoPlayerManager` | reprodução direta + watchdogs + retries | só DIRECT_STREAM |
| `WebPlayer` | reprodução/observação web + fullscreen | só WEB_EMBED |
| `WebSessionPolicy` | decisões puras de sessão web | sem Android |
| `VideoFallbackPolicy` | falha de vídeo → próxima fonte / áudio-só | sem Android |
| `PlaybackStates` | deriva estado observável do Exo | sem Android |
| `PlayerViewModel` | orquestra estado, gerações, fallback | sem I/O de disco |

## 2. Fluxo de recuperação (resumo)

```
DIRECT_STREAM: Exo erro/stall → retries → tryNextStream → onError → advanceAttempt
WEB_EMBED:     erro doc/SSL/renderer → onWebError ─────────────► advanceAttempt
               página ok sem mídia (15 s) → "sem_midia" ───────► advanceAttempt
Plano esgotado: noSignal=true (UNAVAILABLE) — canal PERMANECE na grade
```

## 3. Por que não há adaptador por player

Os players web da Fonte A (Clappr/hls.js, ver doc 41) não expõem API para o app (iframe de
outra origem). Criar adaptadores exigiria injeção de JS/leitura de estado interno —
proibido. Em vez disso, um **adaptador universal por evidência de rede** observa
requisições de mídia e trata "sem mídia" como falha de fonte. É agnóstico de player e não
contorna restrições.

## 4. Costura segura de fallback

- `PlayerViewModel.attemptIndex` + `tuneGeneration` descartam resultados obsoletos.
- `advanceAttempt` só avança (índice estritamente maior); nunca repete a mesma fonte.
- `stateForAttempt` reflete sempre a **fonte real** (nunca mostra a região errada).
- Cancelamentos de guarda evitam que uma falha de uma fonte antiga derrube a nova
  (`DisposableEffect(url)` + holders de guarda).

## 5. Candidatos futuros (não implementados)

- Adaptador de stall web (mídia começou e parou), simétrico ao `videoFallback`.
- Recriação de WebView após `onRenderProcessGone` quando a próxima fonte também é embed.
- Persistir epg/fonte por canal — fora de escopo (não afetar catálogo/navegação).
