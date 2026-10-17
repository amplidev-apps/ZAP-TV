package com.zap.tv.util

/**
 * Etapa 18 — Ledger persistente do catálogo complementar aprovado.
 *
 * Registro monotônico (somente cresce) dos identificadores ESTÁVEIS (`channelId`) dos
 * canais que pertencem ao catálogo ativo. O pertencimento é uma decisão de IDENTIDADE
 * (origem/seleção aprovada), NUNCA da existência de stream: um canal aprovado
 * permanece na grade mesmo que perca a transmissão.
 *
 * Formato persistido: texto com um `channelId` por linha (ordenado, sem cabeçalho).
 * Nenhum canal é removido por falha de rede ou ausência de fonte.
 */
object ApprovedCatalogLedger {

    /** Chave em `app_flags` onde o ledger é persistido (sem alteração de esquema Room). */
    const val FLAG_KEY: String = "approved_catalog_ledger_v1"

    /** Lê o ledger persistido. Texto nulo/em branco => conjunto vazio (primeira execução). */
    fun parse(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(LinkedHashSet())
    }

    /** Serializa o ledger de forma determinística (ordenado), estável entre execuções. */
    fun serialize(ids: Collection<String>): String =
        ids.asSequence().filter { it.isNotBlank() }.distinct().sorted().joinToString("\n")

    /**
     * União monotônica das fontes aprovadas. Nunca remove identidades já presentes:
     * o resultado é a união de `fontes` (ledger anterior + composição atual + semente),
     * garantindo que nenhuma identidade aprovada desapareça.
     */
    fun merge(vararg sources: Iterable<String>): Set<String> {
        val out = LinkedHashSet<String>()
        for (source in sources) for (id in source) if (id.isNotBlank()) out.add(id)
        return out
    }
}
