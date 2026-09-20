package com.zap.tv.util

object CategoryTranslator {
    private val map = mapOf(
        "general" to "Entretenimento",
        "entertainment" to "Entretenimento",
        "variety" to "Variedades",
        "lifestyle" to "Variedades",
        "culture" to "Variedades",
        "news" to "Notícias",
        "sports" to "Esportes",
        "sport" to "Esportes",
        "movies" to "Filmes",
        "movie" to "Filmes",
        "series" to "Séries",
        "kids" to "Infantil",
        "children" to "Infantil",
        "animation" to "Infantil",
        "music" to "Música",
        "documentary" to "Documentário",
        "education" to "Educação",
        "religious" to "Religioso",
        "business" to "Negócios",
        "classic" to "Clássicos",
        "comedy" to "Comédia",
        "cooking" to "Culinária",
        "outdoor" to "Aventura",
        "travel" to "Viagem",
        "weather" to "Clima",
        "science" to "Ciência",
        "health" to "Saúde",
        "auto" to "Automotivo",
        "shop" to "Vendas"
    )
    fun translate(cat: String): String {
        val key = cat.trim().lowercase()
        return map[key] ?: key.replaceFirstChar { it.titlecase() }
    }
    fun translateList(cats: String): String {
        if (cats.isBlank()) return "Entretenimento"
        val list = cats.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty()) return "Entretenimento"
        // pega primeira categoria relevante
        val first = list.firstOrNull { it.lowercase() != "general" } ?: list.first()
        return translate(first)
    }
}

object CountryTranslator {
    private val map = mapOf(
        "BR" to "Brasil",
        "PT" to "Portugal",
        "US" to "Estados Unidos",
        "GB" to "Reino Unido",
        "UK" to "Reino Unido",
        "ES" to "Espanha",
        "AR" to "Argentina",
        "MX" to "México",
        "CL" to "Chile",
        "CO" to "Colômbia",
        "PE" to "Peru",
        "UY" to "Uruguai",
        "PY" to "Paraguai",
        "BO" to "Bolívia",
        "VE" to "Venezuela",
        "FR" to "França",
        "DE" to "Alemanha",
        "IT" to "Itália",
        "JP" to "Japão",
        "CN" to "China",
        "CA" to "Canadá",
        "AU" to "Austrália",
        "RU" to "Rússia",
        "TR" to "Turquia",
        "IN" to "Índia"
    )
    fun translate(code: String): String = map[code.uppercase()] ?: code
}
