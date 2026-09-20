package com.zap.tv.util

object FlagEmoji {
    fun forCountry(code: String): String = when (code.uppercase().trim()) {
        "BR" -> "🇧🇷"
        "PT" -> "🇵🇹"
        "US", "USA" -> "🇺🇸"
        "GB", "UK" -> "🇬🇧"
        "ES" -> "🇪🇸"
        "AR" -> "🇦🇷"
        "INTL", "MUNDO" -> "🌐"
        else -> ""
    }
}
