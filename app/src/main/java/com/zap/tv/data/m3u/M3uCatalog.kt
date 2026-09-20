package com.zap.tv.data.m3u

import com.zap.tv.data.api.ApiChannelDto
import com.zap.tv.data.api.ApiStreamDto

data class M3uCatalog(
    val brChannels: List<M3uChannelGroup>,
    val ptChannels: List<M3uChannelGroup>,
    val usChannels: List<M3uChannelGroup>,
    val adultChannels: List<M3uChannelGroup>
)

data class M3uChannelGroup(
    val channelId: String,
    val displayName: String,
    val country: String,
    val categories: List<String>,
    val streams: List<M3uStreamInfo>,
    val logoUrl: String? = null,
    val isNsfw: Boolean = false
)

data class M3uStreamInfo(
    val url: String,
    val quality: String,
    val referrer: String? = null,
    val userAgent: String? = null
)

fun M3uChannelGroup.toApiChannel(): ApiChannelDto {
    return ApiChannelDto(
        id = channelId,
        name = displayName,
        country = country,
        categories = categories,
        is_nsfw = isNsfw,
        owners = emptyList()
    )
}

// Regular function for use from other modules
fun toApiStream(stream: M3uStreamInfo, channelId: String): ApiStreamDto {
    return ApiStreamDto(
        channel = channelId,
        feed = null,
        title = stream.quality,
        url = stream.url,
        referrer = stream.referrer,
        user_agent = stream.userAgent,
        quality = stream.quality,
        label = stream.quality
    )
}