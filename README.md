# ZAP-TV — Receptor Android TV (Estilo Parabólica) 📺

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin)
![AGP](https://img.shields.io/badge/AGP-8.4.0-3DDC84?logo=android)
![Media3](https://img.shields.io/badge/Media3-1.2.1-FF6D00?logo=google)
![Compose TV](https://img.shields.io/badge/Compose_TV-1.0--alpha10-4285F4)
![minSdk](https://img.shields.io/badge/minSdk-26-0096FF)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

> **Liga e toca.** Controle remoto total — sem mouse. Zapping com `▲▼`, digita `0-9`, `OK` abre banner, `🔴` favorita. Estilo receptor de parabólica para TV Box e Android TV.

<p align="center">
  <img src="zap-tv%20logo.png" width="180" alt="ZAP-TV Logo 1024x1024" />
  <br/>
  <sub>Banner com <code>zap_tv_logo</code> 56dp + <code>🇧🇷 flag</code> no topo — receiver premium</sub>
</p>

---

## 📑 Índice

- [Fluxo STB](#fluxo-stb)
- [Numeração e Categorias](#numeração-e-categorias)
- [Player e Keep-Alive](#player-e-keep-alive)
- [Banner](#banner)
- [Arquitetura](#arquitetura)
- [Stack Técnico](#stack-técnico)
- [Mapeamento do Controle](#mapeamento-do-controle)
- [Como Rodar](#como-rodar)
- [Gerar Catálogo M3U](#gerar-catálogo-m3u)
- [Estrutura de Pastas](#estrutura-de-pastas)
- [API / Dados](#api--dados)
- [Checklist MVP](#checklist-mvp)
- [Roadmap](#roadmap)
- [Notas Legais](#notas-legais)

---

## Fluxo STB

1. **Primeira inicialização:** `Splash` → `Setup` 5 etapas → `Player` canal `1`
   - **1. Aparelho:** `ANDROID_TV` (com teclas coloridas 🔴🟢🟡🔵) vs `TV_BOX` (só setas+OK) — salva `SharedPreferences zap_tv_prefs.device_mode`
   - **2. Região:** Grid 5 colunas `res/raw/brazil_states.json` (27 UFs + `NACIONAL`) — define filial prioritária
   - **3. Perfil:** `Como gostaria de ser chamado?` → `user_name` — exibe `Bem-vindo, João!` no tuning
   - **4. Senha:** PIN 4 dígitos `ParentalUtils` SHA-256, lista fraca `0000/1111.../1234`, opcional **Pular**
   - **5. Sintonizando:** `completeSetupWithVerification` com `VerifyProgress(currentName, checked, total, alive)` barra + `%` + `alive`
2. **Demais inicializações:** `Splash` → `Player` no **último canal** (`Room LastChannelEntity`) + `Bem vindo de volta, João!` em `SharedPreferences user_name`
3. **Navegação:** `0-9` digita número (debounce 900ms, até 5 dígitos para `20000`), `▲▼` próximo/anterior com loop `1↔9999`, `OK` banner, `BACK` fecha overlay/sai

---

## Numeração e Categorias

**Fixos 1-9 (sempre):** `1 Globo` `2 SBT` `3 Record` `4 RedeTV!` `5 Gazeta` `6 Band` `7 TV Cultura` `8 TV Centro Sul` (custom `https://5c483b9d1019c.streamlock.net/.../playlist.m3u8`) `9 TV Litoral RN` — resolvidos via `ChannelNumbering.resolveFixedEntities()` com `affiliateMap` 27 UFs + `stateFallbacks` (ex: `CE→globo_ce_tv_verdes_mares.br`).

**Dinâmicos `10-20000` (sem gap):**

| Faixa | Conteúdo | Exemplo |
|-------|----------|---------|
| `10-1999` | `BR general` (TVs locais) | `Cinturão Verde` removido via `BLOCKLIST` |
| `2000-2999` | `maratonar` (`[24H] 101 Dálmatas` tjtor) | Mantido como categoria, 1007 canais |
| `3000-9999` | `INTL/mundo` | Mundo |
| `10000+` | `PT/US` + `Adulto` (`isNsfw=true` PIN) | `11922` adultos de `CanaisBR01/02.m3u8` |

Filtro por UF **antes** de numerar: `ChannelNumbering.getAffiliateState()` → `NACIONAL` vê tudo, `CE` esconde `SP`.

**Categorias (`M3uChannelMapper.categorizeChannel` + `CategoryTranslator`):** `general→Entretenimento`, `news→Notícias`, `sports→Esportes`, `movies→Filmes`, `kids→Infantil`, `entertainment→Entretenimento`, `maratonar→Maratonar`, `documentary→Documentário`, `music→Música`, `religious→Religioso`. `channel_numbers.categories` comma-separated, `FlagEmoji.forCountry(BR)=🇧🇷 PT=🇵🇹 US=🇺🇸`.

**Remoções hard `M3uChannelMapper.BLOCKED_EXACT`:** `Cinturão Verde, ElyTV, MBC Brasil, RCTV Brasil, TV3 Curitiba, TV Alternativa, 8NTV, [11:00] Guarani x Caxias` (regex `\[\d{1,2}:\d{2}\].* X `) + `BBB/BIG BROTHER`. `m3u_catalog.json` final `BR 1670` (não 999).

---

## Player e Keep-Alive

`ExoPlayerManager` **infalível** (causa raiz: `tjtor .ts` e `up.kiwi` opaco eram tratados como progressivo sem `LiveConfiguration`, buffer `30s/120s` esgotava em 30s-1min e watchdog `BUFFERING>10s` não cobria `READY stall`):

*   **Detecta:** `isTs = url.contains(".ts")` → `DefaultHttpDataSource` para `tjtor` TS, `OkHttpDataSource (VLC/3.0.9)` para HLS (`up.kiwi`, `*.m3u8` com `LiveConfiguration 10s/5s/20s`)
*   **Keep-alive:** `OkHttp connect 5s/read 8s`, `DefaultLoadControl 15s/60s/1.2s/2s`, `backBuffer 10s`, `PARTIAL_WAKE_LOCK`, `watchdog 2s` → `BUFFERING>5s` ou `READY pos==lastPos>3s` → `playCurrentStream()` com `seekTo(lastPos-1500ms)` (não `seekToDefaultPosition` que comia 10s) + `onReconnecting(true)` → `• Extraindo Sinal`.
*   **Fallback:** `playMulti` lista `apiStreams + m3uStreams` (`distinctBy url, sortedBy quality SD0→4K`), `tryNextStream` sequencial + `autoRetry 2.5s` loop `index0` (m3u cai e volta sozinho, sem precisar sair/voltar do canal).

Logs: `adb logcat | grep ZapPlayer` → `play ... isHls=true` + `watchdog BUFFERING>5s`.

---

## Banner

`ChannelInfoBanner` (receiver premium):

*   **Header:** `zap_tv_logo.png` `56dp` quadrado mapeado `1024×1024` com `padding 6dp` + `RoundedCorner 10dp` + `Box weight 1f` divisor + `ZAP-TV` / `• Extraindo Sinal` (quando `isReconnecting`) `10sp ZapGold` no topo à esquerda.
*   **Top Row:** logo canal 68×48 `Coil AsyncImage` (fallback `country` code), número `32sp ZapGold Black`, nome `21sp` + `★`, `Category • 🇧🇷 País` (`FlagEmoji`), badges `FHD/1080p`, `AO VIVO` 🔴 + relógio `HH:mm`.
*   **Middle:** `NO AR: Transmissão Digital Ao Vivo` + `A seguir: Programação Geral` + barra `55%`.
*   **Footer:** `ANDROID_TV` → legendas `🔴 Favoritar 🟢 Categorias 🟡 Áudio&Tela 🔵 Guia` / `TV_BOX` → `Pressione e segure [OK]`.

---

## Arquitetura

```
SPLASH → SETUP (5) → PLAYER
   ↓         ↓           ↓
Room zap.db v2 (6 tabelas) + SharedPreferences zap_tv_prefs
```

*   `SplashScreen` `withTimeout 4s` + `try countAvailable` fallback → `onSetupNeeded/onReady`.
*   `SetupViewModel` `StateFlow uf/pin/confirm/userName/deviceMode/step/verifying/verifyProgress` + `SharedPreferences user_name`.
*   `PlayerViewModel` `PlayerUiState` (`current, isLoading, inputDigits 5 díg., showInfoBanner/*Drawer/*Guide/*Parental/*TvBoxHub, deviceMode, isReconnecting, aspectRatio FILL`) + `channelUp/down` loop + `onDigit` 5 dígitos + `tune` com `ParentalRepository` check.
*   `AppNavHost` `NavHost` `SPLASH→SETUP→PLAYER`.
*   `KeepScreenOn` + `PowerManager.PARTIAL_WAKE_LOCK`.

---

## Stack Técnico

| Camada | Versão | Artefato |
|--------|--------|----------|
| AGP | 8.4.0 | `com.android.application` |
| Kotlin | 1.9.22 | `org.jetbrains.kotlin.android` |
| Compose BOM | 2024.06.00 | `androidx.compose:compose-bom` |
| Compose TV | 1.0.0-alpha10 | `androidx.tv:tv-foundation` |
| Room | 2.6.1 | `room-runtime, room-ktx` |
| Hilt | 2.48 | `hilt-android` |
| Media3 | 1.2.1 | `exoplayer, hls, datasource-okhttp` |
| OkHttp | 4.12.0 | `okhttp` |
| Retrofit | 2.11.0 | `retrofit + moshi` |
| Coil | 2.5.0 | `coil-compose` |

`compileSdk 34, minSdk 26, targetSdk 34, namespace com.zap.tv, version 1.0.0 (1), JvmTarget 17, Java 17, signing debug.keystore`.

---

## Mapeamento do Controle

`util/RemoteKeyHandler.kt` → `RemoteAction`:

`0-9` → `DIGIT_0..9`, `CHANNEL_UP/DOWN ↔ DPAD_UP/DOWN`, `DPAD_LEFT/RIGHT → VOLUME`, `DPAD_CENTER/ENTER → OK`, `BACK/ESC → BACK`, `MENU → MENU`, `F1-F4 → RED/GREEN/YELLOW/BLUE`, `INFO → INFO`, `GUIDE → GUIDE`, `LAST_CHANNEL → LAST`, `PLAY_PAUSE → PLAY`

---

## Como Rodar

```bash
# 1. Pré-requisito
local.properties -> sdk.dir=C:\Temp\android\sdk
JDK 17 -> set JAVA_HOME=C:\Java17\jdk-17.0.12

# 2. Clone
git clone git@github.com:amplidev-apps/ZAP-TV.git
cd ZAP-TV

# 3. Sync & Run (Android Studio Hedgehog+)
./gradlew :app:assembleRelease -Dorg.gradle.java.home=C:\Java17\jdk-17.0.12
# ou Build > Generate Signed APK > release

# 4. Instalar na TV Box (API 26+)
adb install -r ZAP-TV/app-release.apk
# limpo (recomendado após 999→20000): adb uninstall com.zap.tv && adb install ZAP-TV/app-release.apk

# Logs keep-alive
adb logcat | grep ZapPlayer
```

---

## Gerar Catálogo M3U

```bash
# CanaisBR01/02.m3u8 (28+30MB, git add -f) → assets/m3u_catalog.json
python regen_catalog.py # filtra BLOCKLIST hard, mantém maratonar, junta 11922→600 adultos, BR 1670
# ou Kotlin: ./gradlew :app:run --args="CanaisBR01.m3u8 CanaisBR02.m3u8" (M3uCatalogGenerator)
```

`M3uParser` `ADULT_KEYWORDS XXX/PORN/...`, `M3uChannelMapper` `VOD_KEYWORDS FILME/SERIE...` (sem 24H/MARATONA).

---

## Estrutura de Pastas

```
ZAP-TV/
  app/src/main/java/com/zap/tv/
    data/api/IptvApiService.kt        # https://iptv-org.github.io/api/
    data/local/AppDatabase.kt         # zap.db
    data/m3u/M3uChannelMapper.kt      # BLOCKLIST + categorize
    data/repository/ChannelRepository.kt # merge iptv-org + M3U
    util/ChannelNumbering.kt          # 1-20000
    util/FlagEmoji.kt                 # 🇧🇷
    presentation/splash/ setup/ player/ overlay/ # ChannelInfoBanner, CategoriesDrawer
    navigation/AppNavHost.kt
  app/src/main/res/drawable/zap_tv_logo.png # 1024x1024
  app/src/main/assets/m3u_catalog.json
  CanaisBR01/02.m3u8                   # git add -f
  CANAIS_LISTA.md                      # 13592 canais por categoria
  zap-tv logo.png / icon.png / tile
```

---

## Checklist MVP

- [ ] Instalar limpo → Aparelho → Região CE → Nome João → PIN → canal 1 Globo toca <3s com `🇧🇷`
- [ ] Digitar `12` → sintoniza 12; `▲▼` loopa `20000→1`
- [ ] `🔴` favorita, reabre mantém `★`
- [ ] Deixar `Globo 1 tjtor` 70s → ver só `• Extraindo Sinal` 2-3s, sem congelar 20s
- [ ] Trocar `1→2→1` rápido sem frame preso

---

## Roadmap

Ver `ROADMAP.md` — Fase 2 EPG XMLTV 03:00 BRT, Fase 3 favoritos backup, Fase 4 PiP/WakeLock, Fase 5 Timeshift 30min, Fase 6 companion.

---

## Notas Legais

Listas `iptv-org` e `CanaisBR*.m3u8` são links públicos. Nenhum vídeo armazenado no app. Uso educacional. `android:usesCleartextTraffic true` necessário para `tjtor:80`.
