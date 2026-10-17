# 14 — VALIDAÇÃO TÉCNICA

> Etapa 06. Ambiente: Ubuntu, Intel i3-2120, 8 GB RAM. JDK 17 em `/usr/lib/jvm/java-17-openjdk-amd64` (o `java` padrão do sistema é o 25, não suportado pelo Gradle 8.7 — por isso `JAVA_HOME` foi fixado por comando). Gradle Wrapper 8.7, Android SDK em `/home/amplidev/Android/Sdk` (platform 34, build-tools 34.0.0). Emulador **não** utilizado. Nenhum commit/push.

## 1. Verificação inicial
- Git: branch `main`, HEAD `5b7d44e`; alterações das Etapas 03 e 05 presentes e preservadas; **nenhuma deleção** de arquivo original.
- JDK: 17.0.20 ativo via `JAVA_HOME` (necessário porque o default é o 25).
- SDK: `local.properties` → `sdk.dir=/home/amplidev/Android/Sdk`; licenças presentes; plataforma 34 instalada.
- `AffiliateRegistry.kt` íntegro (187 linhas, dado verificado).
- JUnit: `junit = "4.13.2"` no version catalog + `testImplementation(libs.junit)`.

## 2. Testes executados

Comando:
```
bash ./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=384m"
```

## 3. Resultado dos testes

| Classe | Testes | Aprovados | Reprovados |
|---|---|---|---|
| `com.zap.tv.util.AffiliateRegistryTest` | 9 | **9** | 0 |
| `com.zap.tv.util.ChannelNumberingRegionalTest` | 17 | **17** | 0 |
| `com.zap.tv.data.m3u.M3uCatalogGeneratorTest` | 1 | 0 | **1** |
| **Total** | **27** | **26** | **1** |

- **26/26 aprovados** entre os testes existentes de regionalização e os novos (Etapa 05 + regressões dos Riscos A/B).
- A única reprovação é **pré-existente e não relacionada** às Etapas 03/05: `M3uCatalogGeneratorTest.runGenerator` estoura `OutOfMemoryError` ao rodar o gerador sobre os arquivos reais `CanaisBR01.m3u8` (~28 MB) e `CanaisBR02.m3u8` (~30 MB). É um utilitário de geração manual **sem asserções**; não foi modificado (a missão proíbe alterar regras/remoções para forçar testes). Reproduz o mesmo comportamento independentemente das alterações recentes.

Cenários validados (destaques): seleção da afiliada da UF correta; fallback nacional; ausência de afiliada no estado; **proibição de afiliada de outro estado** (inclusive no fallback por nome/palavra-chave); identificação de UF por metadado verificado (e não por palavras do nome); resolução de IDs reais; estabilidade da numeração fixa 1–9; ausência de duplicação entre posições fixas; preservação da TV Centro-Sul (8) e TV Litoral RN (9); troca de estado; determinismo; não mutação de entrada.

## 4. Problemas de compilação e correções

| # | Problema | Causa | Correção |
|---|---|---|---|
| P1 | Build falhava na **configuração** (`Cannot convert URL 'C:/Users/SnyX/.android/debug.keystore' to a file`), quebrando inclusive `testDebugUnitTest`. | `signingConfigs.release` com caminho Windows avaliado em Linux. | Guard autorizado pelo usuário: mantém o caminho/passwords no Windows e usa `~/.android/debug.keystore` fora do Windows. Não altera a assinatura onde funcionava. |
| P2 | Sem dependência de teste no projeto. | Ausência de JUnit no build. | Adicionado `junit 4.13.2` + `testImplementation`. |
| P3 | (Preventivo) Risco A — fallback por nome/palavra-chave poderia escolher afiliada de outro estado se faltasse a cabeça nacional. | Palavras-chave amplas. | `selectFixedEntity` agora descarta afiliadas regionais de **outra** UF nos passos de nome/palavra-chave. |
| P4 | (Preventivo) Risco B — TV Centro-Sul reconhecida por `contains("Centro Sul")` poderia capturar outro canal. | Match por substring. | Identificação por **ID exato** primeiro; fallback por **igualdade** de nome "TV Centro Sul". |

Nenhuma regra regional aprovada foi alterada; as correções P3/P4 apenas reforçam as regras já aprovadas. Testes de regressão adicionados para ambos.

Erros de compilação: **nenhum** após P1/P2. A compilação emitiu apenas *warnings* (variáveis/parâmetros não usados; safe-call desnecessário) — não bloqueantes.

## 5. Integridade das fontes IPTV
- Fonte iptv-org inalterada: `baseUrl = "https://iptv-org.github.io/api/"`.
- `app/src/main/assets/m3u_catalog.json` **não modificado** (fora do diff).
- Integração RDC preservada: `REI_DOS_CANAIS_BASE_URL = "https://api.reidoscanais.si/"`; `embed_url` continua tratado como página web (não stream).

## 6. Integridade da TV Centro-Sul e TV Litoral RN
- TV Centro-Sul: ID `tv_centro_sul_br`, nome "TV Centro Sul", posição **8** e URL StreamLock originais preservados (`https://5c483b9d1019c.streamlock.net/tvcentrosul/tvcentrosul/playlist.m3u8`) em `ChannelNumbering.kt` e `ChannelRepository.kt`. Teste dedicado aprova a preservação e o Risco B.
- TV Litoral RN: `TVLitoralRN.br` na posição **9** (via `AffiliateRegistry` + `exactId`). Teste dedicado aprova.

## 7. Resultado da compilação Debug
```
bash ./gradlew :app:assembleDebug --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=384m"
→ BUILD SUCCESSFUL in 4m 18s
```
Release **não** compilado. Configuração de assinatura não alterada em semântica (apenas o guard P1).

## 8. APK gerado
- Caminho: `app/build/outputs/apk/debug/app-debug.apk`
- Tamanho: `17.691.347` bytes (~16,9 MB)
- SHA-256: `730c2e3da6a476d4ccfdd256825096b8b1cbaff0b8f994d3ae0808481250e757`
- Keystore de debug gerado em `~/.android/debug.keystore` (usado apenas pelo build debug).

## 9. Arquivos modificados nesta etapa
- `app/build.gradle.kts` — guard da assinatura (P1).
- `app/src/main/java/com/zap/tv/util/ChannelNumbering.kt` — endurecimento Riscos A/B.
- `app/src/test/java/com/zap/tv/util/ChannelNumberingRegionalTest.kt` — +4 testes de regressão.
- (Etapas anteriores mantidas: `AffiliateRegistry.kt`, `ChannelRepository.kt`, `libs.versions.toml`, e os arquivos da Etapa 03.)

## 10. Estado do Git
`git status`: 8 arquivos modificados (tracked) + não rastreados (`data/reidoscanais/`, `di/ReiDosCanaisNetworkModule.kt`, `util/AffiliateRegistry.kt`, `test/.../util/`, `docs/`). **Sem deleções**. Nada commitado.

```
 app/build.gradle.kts                               |  15 +-
 .../data/AppDependencies.kt                        |  27 ++
 .../data/repository/ChannelRepository.kt           |  27 +-
 .../presentation/overlay/ChannelInfoBanner.kt      |  23 +-
 .../presentation/player/PlayerScreen.kt            |   1 +
 .../presentation/player/PlayerViewModel.kt         |  21 +-
 .../util/ChannelNumbering.kt                       | 407 +++--------
 gradle/libs.versions.toml                          |   4 +
 8 files changed, 252 insertions(+), 273 deletions(-)
```

## 11. Pendências para teste no emulador
1. Fluxo de setup completo escolhendo CE e SP; verificar posição 1 = afiliada local (CE → TV Verdes Mares; SP → Globo SP).
2. Reprodução real dos streams das afiliadas (ExoPlayer) e do canal 8 (TV Centro-Sul) e 9 (TV Litoral RN).
3. Fallback nacional em UF sem afiliada (SBT/Record/Band).
4. Troca de região via `ChannelRepository.updateRegion(uf)` (sem UI dedicada ainda).
5. Persistência de favoritos/último canal por número após regionalização.
6. `M3uCatalogGeneratorTest`: tratar OOM (ex.: heap de teste maior ou marcar como utilitário manual) — fora do escopo desta etapa.

> Testes e compilação **foram realmente executados**. As 26 aprovações acima são resultado de execução, não de inspeção.
