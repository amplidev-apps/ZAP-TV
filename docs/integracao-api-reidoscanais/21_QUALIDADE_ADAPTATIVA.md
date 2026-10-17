# 21 — QUALIDADE ADAPTATIVA

> Etapa 10 (v2). Perfis de qualidade aplicados sobre o **ABR nativo do Media3**, sem
> transcodificação, sem variante fixa e sem servidor auxiliar. Sem commit/push, sem release.

## 1. Objetivo

Permitir limitar a qualidade máxima (economia de dados / melhor qualidade) **sem** abrir mão
da adaptação automática nativa: inicialização conservadora, upswitch progressivo e redução em
quedas de banda continuam por conta do Media3.

## 2. Superfície pública (`util/PlaybackQualityProfile.kt`)

```kotlin
enum class PlaybackQualityProfile { AUTO, DATA_SAVER, BEST }

data class VideoQualityCaps(
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val maxBitrate: Int? = null
)

object QualityPolicy {
    fun capsFor(profile: PlaybackQualityProfile): VideoQualityCaps
}
```

| Perfil | Teto | Efeito |
|--------|------|--------|
| `AUTO` (padrão) | sem caps | adaptação nativa completa |
| `DATA_SAVER` | 854×480, 800 kbps | teto SD; favorece estabilidade em redes fracas |
| `BEST` | 1920×1080, 8 Mbps | teto Full HD, ainda adaptativo |

`null` significa "sem cap" — nenhum perfil força uma variante específica.

## 3. Aplicação no player (`ExoPlayerManager`)

- `build()` cria um `DefaultTrackSelector` e o instala no `ExoPlayer.Builder` junto ao
  `DefaultLoadControl` existente.
- `buildTrackParameters(profile)` parte de `DefaultTrackSelector.Parameters.Builder`,
  habilita adaptação nativa mista/não-seamless e aplica o teto via
  `setMaxVideoSize(w, h)` e `setMaxVideoBitrate(bits)`.
- `setQualityProfile(profile)` troca o perfil em tempo real (`trackSelector.setParameters(...)`)
  sem reinstanciar o player; no-op quando o perfil não muda.
- O ABR nativo permanece o responsável por escolher a variante inicial e por subir/descer.

## 4. WebView (embeds web) — limite arquitetural

O player de um embed (`WebEmbed`) é executado dentro de uma página de terceiros, em **outro
contexto (cross-origin)**. Não há API padronizada que permita ao app hospedeiro selecionar
360p/480p/720p. Portanto:

- **Não** se tenta controlar a qualidade por dentro da WebView.
- Mantém-se o player web no **modo automático nativo dele**.
- O app não injeta scripts para forçar qualidade nem realiza transcodificação/proxy.

Esse limite é **documentado**, não contornado.

## 5. Testes

`QualityPolicyTest`:
- `AUTO` não impõe caps;
- `DATA_SAVER` limita a 480p e bitrate ≤ 1 Mbps;
- `BEST` limita a 1080p com bitrate positivo.

## 6. Verificação

- `:app:testDebugUnitTest` (JDK 17) — **verde**.
- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- APK: 17.907.196 bytes, sha256 `3afc92d37bd4d4fa976b5a1ac76c3c5dc68e34c683fa30b9b568da2d6de18fea`.

## 7. Não implementado (fora do escopo desta etapa)

- **UI de seleção de perfil** — a camada técnica existe (`setQualityProfile`), mas nenhuma tela
  de configuração foi adicionada (requer autorização, conforme regra do projeto).
- Persistência da preferência de perfil.
- Qualidade granular dentro de embeds web (limite arquitetural, §4).
