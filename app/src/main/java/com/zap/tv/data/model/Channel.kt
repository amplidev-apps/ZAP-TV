package com.zap.tv.data.model

import com.zap.tv.data.local.entity.ChannelNumberEntity

data class Channel(
    val number: Int,
    val id: String,
    val name: String,
    val country: String,
    val categories: List<String>,
    val logoUrl: String?,
    val isNsfw: Boolean,
    val streams: List<Stream>,
    val affiliateState: String? = null,
    val isFavorite: Boolean = false
) {
    fun toEntity(): ChannelNumberEntity = ChannelNumberEntity(
        number = number,
        channelId = id,
        channelName = name,
        country = country,
        categories = categories.joinToString(","),
        logoUrl = logoUrl,
        isNsfw = isNsfw,
        streamCount = streams.size,
        affiliateState = affiliateState
    )
}

data class Stream(
    val url: String,
    val title: String,
    val quality: String?,
    val referrer: String?,
    val userAgent: String?,
    val label: String?
)

data class Country(
    val code: String,
    val name: String,
    val flag: String
)

data class StateOption(
    val uf: String,
    val name: String
)

data class Program(
    val channelId: String,
    val title: String,
    val startMs: Long,
    val stopMs: Long,
    val desc: String?
)
