# ROADMAP ZAP-TV — Pós-MVP

## MVP entregue (2 dias) — FASE 1
- Setup UF + PIN, numeração fixa 1-999, último canal, player ExoPlayer, fallback, mini-guia, parental, favoritos Room, tema ZAP-TV.

---

## FASE 2 — EPG Completo BR (1-2 semanas)
- [ ] `EpgRepository` + `SyncEpgUseCase` (WorkManager daily 03:00 BRT)
- [ ] Download paralelo de TODOS sites BR do `iptv-org/epg/sites` (telemanche, globoplay, sbt, recordtv, band, redetv, tvcultura, gazeta, novotempo, sesc...)
- [ ] Parse XMLTV → `Program` (channelId, title, startMs, stopMs, desc) merge por tvg-id
- [ ] MiniGuide expandido: agora + próximos 6 (3h)
- [ ] `EpgGridScreen` (tecla AZUL/GUIDE): grade temporal canais × horário, navegação ▲▼ canais, ◀▶ tempo, OK info

## FASE 3 — Favoritos & Personalização (1 semana)
- [ ] `FavoritesOverlay` (AMARELO): lista rápida favoritos
- [ ] Ocultar canais, renomear apelidos, reordenar (Editar Lista) — ainda fixo, mas UI para mover
- [ ] Backup/restore `channel_numbers` + `favorites` + `state_config` → JSON em Downloads

## FASE 4 — Qualidade & Robustez (1 semana)
- [ ] QuickSettings: qualidade Auto/1080p/720p/480p (seleção de stream por `quality`)
- [ ] Faixa áudio/legenda (AUDIO/SUBTITLE)
- [ ] PiP nativo Android TV, WakeLock, auto-reconnect exponencial, logs locais

## FASE 5 — Timeshift / DVR (2-3 semanas)
- [ ] Buffer circular 30min (CacheDataSource), PAUSE pausa live, REW/FWD navega
- [ ] Gravação agendada via EPG (VERDE), gerenciador de gravações

## FASE 6 — Ecossistema (opcional)
- [ ] Companion mobile, M3U externo, temas alto contraste, i18n PT/EN/ES, TTS fala canal

---

## Backlog técnico
- Testes: ChannelNumbering unit, Parental hash, PlayerViewModel
- CI: GitHub Actions `assembleRelease`
- Crash reporting local (sem Firebase, arquivo)
- EPG cache 7 dias + limpeza LRU
