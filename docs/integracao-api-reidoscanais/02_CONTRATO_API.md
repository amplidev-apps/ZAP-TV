# 02 — CONTRATO DA API REI DOS CANAIS (verificado ao vivo)

> Endereço-base: `https://api.reidoscanais.si` | Docs: `https://reidoscanais.si/api`
> Verificação realizada em 2026-10-16 18:38 BRT, com requisições reais. Diferenças entre documentação e comportamento observado estão registradas em §5.

## 1. Endpoints verificados

| Endpoint | Status real | Observação |
|---|---|---|
| `GET /health` | HTTP 200, `{"success":true,"status":"healthy",...}` | Inclui `checks` (database, epg_cache) e `uptime`; documentação prevê 503 quando indisponível (não observado) |
| `GET /channels` | HTTP 200, 151 KB | `success`, `data` (104 canais), `total` |
| `GET /channels/globo` | HTTP 200 | Mesmo shape do item da lista; usado para detalhe |
| `GET /channels/categories` | HTTP 200 | 7 categorias com `id` numérico e `name` |
| `GET /search?q=globo` | HTTP 200 | Retorna `data.channels` (+ eventos, quando houver) |
| `GET /sports?status=live` | HTTP 200, `data: []`, `total: 0` | Sem eventos ao vivo no momento do teste; tratado como funcionalidade opcional |

## 2. Shape do canal (verificado em 104 registros)

```json
{
  "id": "globo",                       // slug estável, único (0 duplicados)
  "name": "Globo",
  "description": "A Globo é ...",
  "logo_url": "https://cdn.reidoscanais.si/imagens/tvglobo.png",
  "category": "Canais Abertos",        // PT-BR; uma das 7 categorias
  "embeds": [
    {"provider": "Globo Rio de Janeiro", "quality": "FULL HD",
     "embed_url": "https://rdcanais.org/globorj"}
  ],
  "epg": {
    "current": {"title","description","formatted_time","start_time","end_time","image"},
    "next":    {"title","description","formatted_time","start_time","end_time","image"}
  }
}
```

- `epg.start_time`/`end_time`: Unix em segundos (confirmado por tipos e valores).
- `epg.image`: proxy da API (`api.reidoscanais.si/img/<base64 da URL original>`).
- 98/104 canais com EPG no instante do teste; 6 sem EPG simplesmente não trazem o objeto (nenhum campo parcial).

## 3. Categorias (todas as 7, com contagem real)

Canais Abertos (9), Documentários (9), Entretenimento (18), Esportes (25), Filmes e Séries (30), Infantil (6), Notícias (7).

## 4. ACHADO CRÍTICO — embeds NÃO são streams

- 144 embeds em 104 canais; **100% apontam para páginas web `https://rdcanais.org/<slug>`** (29 provedores distintos: "Servidor Premium" 103×, "Servidor Alternativo" 14×, filiais regionais da Globo 8×...).
- **Zero** embeds com padrão `.m3u8`, `.ts` ou URL de stream direto.
- Conclusão: a API serve **metadados + EPG**, não vídeo reproduzível pelo ExoPlayer.
- Decisão (conforme protocolo §06): embeds são registrados como metadados (`RdcEmbedDto`), jamais usados como fonte de reprodução. Não há extração de streams de páginas de terceiros, não há contorno de DRM, não há falsificação de autenticação.

## 5. Diferenças/limitações observadas vs. documentação

| Item | Documentação | Observado |
|---|---|---|
| EPG | atualização a cada 5 min | Não é possível confirmar o intervalo pelo cliente; cache adotado com TTL de 5 min |
| Grade de programação | apenas `current`/`next` | Confirmado: **não há grade de 7/14 dias** — a UI existente de "guia completo" não pode ser populada por esta API |
| Esportes | eventos com status upcoming/live/finished | Endpoint funcional, vazio no momento do teste — integração permanece opcional/futura |
| Canal sem EPG | — | Campo `epg` simplesmente ausente (tratado como null no DTO) |

## 6. Regras de uso adotadas pelo ZAP-TV

1. Cache único de `GET /channels` com TTL de 5 minutos (alinhado ao refresh de EPG do servidor) — no máximo 1 requisição a cada 5 min, independentemente do zapping.
2. `GET /health` usado apenas para diagnóstico (`ReiDosCanaisRepository.isHealthy()`).
3. Toda falha de rede/5xx/parse degrada silenciosamente: sem EPG/metadados novos, catálogo existente intacto.
4. `/channels/{id}`, `/channels/categories`, `/search`, `/sports`: mapeados no cliente (`ReiDosCanaisApi`), sem uso no fluxo principal — reservados para etapas futuras.
