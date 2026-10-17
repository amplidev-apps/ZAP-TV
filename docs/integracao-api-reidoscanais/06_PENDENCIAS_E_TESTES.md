# 06 — PENDÊNCIAS E TESTES

> Status honesto: implementação concluída, **compilação e execução NÃO validadas** (proibidas nesta etapa). Nada aqui foi testado em runtime.

## 1. Classificação de risco das alterações

| Risco | Componente | Motivo |
|---|---|---|
| Baixo | DTOs, mapper, DI module, repository | Código novo isolado, sem chamadas no caminho crítico em caso de falha (try/catch + fallback) |
| Médio | `ChannelRepository` (construtor + loadM3uCatalog) | Construtor com novo parâmetro afeta Hilt e `AppDependencies` — ambos atualizados, mas sem build para confirmar |
| Médio | `PlayerViewModel` (launch de EPG) | Coroutina adicional por sintonia; guarda anti-race implementada, sem teste |
| Baixo | Banner/PlayerScreen | Param opcional com fallback visual idêntico |

## 2. Pendências técnicas

1. **Compilação** (`:app:assembleDebug`) — primeira validação obrigatória; ainda não autorizada.
2. **Casamento de nomes** — 91/104 canais RDC casam com o catálogo BR pela heurística atual; os 13 sem casamento ficam sem EPG/logo novo (comportamento normal). Ajustes finos de casamento (ex.: aliases "Discovery ID"→"investigation-discovery") só após observação em runtime.
3. **Canais RDC sem stream reproduzível** (13: Caze TV 2/3, TNT Novelas, TNT Séries, Tooncast, Discovery ID, Paramount+ 2-4, Premiere 2-8 já casam parcialmente, USA, Agro+...) — decisão pendente de produto: manter fora da grade (atual) ou exibir como "indisponível".
4. **EPG filiais regionais** — EPG da RDC para "Globo" é nacional; filiais (Globo RJ/SP/CE...) recebem o mesmo EPG. Limitação aceita; refinar só com fonte EPG regional.
5. **ProgramGuideOverlay** — continua com texto estático; popular a coluna "programação" exigiria fetch em lote (104 canais, já cobertos pelo cache de 5 min — viável, mas é mudança de UI a ser validada).
6. **Esportes/`/search`/`/channels/{id}`** — mapeados no cliente, sem uso no fluxo; integração opcional futura.
7. **`fallbackToDestructiveMigration()`** — preexistente; qualquer futura mudança de schema Room destruirá dados. Registrar antes de mexer em entidades.
8. **Regeneração de catálogo** — `regen_catalog.py` citado no README não existe no repo; se a regeração do `m3u_catalog.json` for necessária, usar `M3uCatalogGenerator`.

## 3. Plano de testes — emulador Android TV 11 (`ZAP_TV_Test`, API 30, x86, KVM)

### 3.1 Pré-requisitos
- JDK 17 ativo para o Gradle; `local.properties` já apontando o SDK (`sdk.dir=/home/amplidev/Android/Sdk`).
- `sh gradlew :app:assembleDebug --no-daemon --max-workers=2` (o wrapper não tem bit exec no NTFS — usar `sh gradlew`).
- Emulador `ZAP_TV_Test` rodando, `adb devices` conectado.

### 3.2 Suite funcional (ordem)
1. **Setup limpo:** desinstalar `com.zap.tv` → instalar APK debug → setup 5 passos (ANDROID_TV, CE, nome, PIN) → canal 1 toca.
2. **TV Centro-Sul:** digitar `8` → TV Centro-Sul sintoniza (URL streamlock) → verificar número 8, nome, logo no banner. ** regressão crítica **.
3. **EPG no banner:** sintonizar canal casado (ex.: 10+ "Cartoon Network", "Band", "Space") → banner "NO AR:" deve mostrar programa real (ex.: título de programa, não "Transmissão Digital Ao Vivo") → "A seguir:" com programa seguinte → barra de progresso coerente com hora.
4. **Fallback sem EPG:** sintonizar canal sem casamento (ex.: canal international) → banner volta ao texto estático sem travar/erro.
5. **API offline:** (opcional, avião/chromecast off) app continua 100% funcional — sintonia, favoritos, guia, sem EPG e sem crashes.
6. **API saudável:** `curl https://api.reidoscanais.si/health` deve dar 200 no host.
7. **Regressão:** favoritar (🔴), categorias (🟢), guia (🔵), zapping ▲▼ com loop, digitação 0-9, último canal ao reabrir.
8. **Performance de sintonia:** trocar canais rápido (1→2→1→8→10) — EPG não pode travar o tune (é async) nem causar frame preso.
9. **Logcat:** `adb logcat | grep -iE "reidoscanais|ZapPlayer"` — sem exceções Moshi/Retrofit; um único fetch de `/channels` a cada ≥5 min.

### 3.3 Critérios de falha
- Qualquer crash no tune/banner com EPG presente ou ausente.
- TV Centro-Sul fora do número 8 ou sem tocar.
- Mudança de numeração/ordem do catálogo vs. APK 1.0.0 (comparar grade antes/depois).
- Requests à API com frequência maior que 1/5 min durante zapping contínuo.

## 4. Próximos passos sugeridos

1. Autorizar build → validar compilação → corrigir erros de compilação se existirem (só então os componentes implementados deixam de ser "pendentes de validação").
2. Instalar no emulador e executar a suite 3.2.
3. Decidir pendência #3 (canais RDC sem stream) com base na experiência real.
4. Avaliar enriquecimento do ProgramGuideOverlay com EPG do cache (pendência #5).
