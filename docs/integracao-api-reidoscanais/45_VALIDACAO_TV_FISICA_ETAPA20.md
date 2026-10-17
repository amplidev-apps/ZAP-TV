# 45 — VALIDAÇÃO EM TV FÍSICA (ETAPA 20)

> Roteiro para validação no dispositivo Android TV real. O OpenCode **não** tem acesso ao
> aparelho. Execute os passos, colete os logs e reporte. Nenhuma transmissão externa é
> declarada funcional sem esta evidência.

## 1. Instalação

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
APK Debug Etapa 20: 17.947.134 bytes · sha256
`e5c0b4885dc90d30417e1247ea96d46f9e1b66ed64daeba0d9a56c51d4268443`.

## 2. Coleta de ambiente

```
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys package com.google.android.webview | grep -i versionName
adb logcat -c
```

## 3. Logs (só host/motivo/extensão; sem URL/token)

```
adb logcat -s WebMotor WebProbe AvDiag ZapPlayer RegionalDiag TuningDiag CatalogSync
```

Mensagens esperadas:
- `WebMotor ... System WebView versao=...`
- `WebMotor midia requisitada host=<host>` — 1ª requisição de mídia do embed
- `WebMotor midia ativa req=N err=M` — evidência de mídia (sem falha)
- `WebMotor sem midia req=0 err=0 -> fallback motivo=sem_midia` — embed morto → avança
- `WebMotor midia http=404 host=<host> err=N` — playlist/segmento falhou
- `ZapPlayer watchdog BUFFERING sem progresso -> escalar` — stream iptv morto → próxima
- `RegionalDiag fallback motivo=... -> proxima="<rótulo>"` — troca de fonte
- `AvDiag reproducao confirmada|NAO verificavel ...` — confirmação (Media3) vs não observável

## 4. Checklist por canal

| Caso | O que observar |
|---|---|
| **Globo (canal 1)** | UF→SP→RJ; se card de erro da origem, deve **avançar** (log `sem midia`) e nunca travar; trocar de canal e voltar deve re-sintonizar |
| **Premiere 1/2/3** | continuam reproduzindo (**não regredir**) |
| **Gloob, HBO, Cartoon** | continuam reproduzindo (**não regredir**) |
| **Gazeta** | continua corrigido |
| **Band** | iptv morto → deve **escalar** para o embed em vez de travar |
| **GNT / SporTV 1/2/3** | tentar premium → alternativa; registrar `WebMotor`/`AvDiag` |
| **TV Cultura** | sinal principal (não Cultura Fast) |
| **TV Centro-Sul (8) / TV Litoral RN (9)** | preservados |
| **Navegação 9→10→11→12→13→14** | correta |
| **Canais destacados** | ordem alfabética; Telecine agrupado |
| **Troca de canal** | áudio anterior não permanece |
| **Troca rápida** | sem travamento; eventos atrasados não derrubam o canal novo |

## 5. Testes de regressão de catálogo (verificar na TV)

- Ledger persistido (Etapa 18) intacto; seed aplicada; grade completa.
- Números 1–9 fixos; favoritos/histórico/EPG preservados.
- Nenhum canal aprovado sumiu.

## 6. O que reportar

1. Modelo da TV e versão do System WebView.
2. Por canal: `WebMotor` (req/err, motivo) + `RegionalDiag` (fonte/região) + `AvDiag`.
3. Onde ficou preso (se ficar) e por quanto tempo.
4. Ordem efetiva do fallback da Globo observada.
5. Qualquer regressão em Premiere/Gloob/HBO/Cartoon/Gazeta.

## 7. Critério de sucesso

- Nenhum canal **preso** indefinidamente; fallback observável por log.
- Globo percorre UF→SP→RJ→demais (quando aplicável) sem travar.
- Funcionais da Etapa 19 permanecem funcionais.
