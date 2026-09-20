package com.zap.tv.data.api

import retrofit2.http.GET

// DTOs matching iptv-org/api JSON
data class ApiChannelDto(
    val id: String,
    val name: String,
    val country: String,
    val categories: List<String>,
    val is_nsfw: Boolean,
    val owners: List<String> = emptyList()
)

data class ApiStreamDto(
    val channel: String?,
    val feed: String?,
    val title: String,
    val url: String,
    val referrer: String?,
    val user_agent: String?,
    val quality: String?,
    val label: String?
)

data class ApiCountryDto(
    val code: String,
    val name: String,
    val flag: String
)

data class ApiLogoDto(
    val channel: String,
    val feed: String?,
    val url: String,
    val width: Int?,
    val height: Int?
)

interface IptvApiService {
    @GET("channels.json")
    suspend fun getChannels(): List<ApiChannelDto>

    @GET("streams.json")
    suspend fun getStreams(): List<ApiStreamDto>

    @GET("countries.json")
    suspend fun getCountries(): List<ApiCountryDto>

    @GET("logos.json")
    suspend fun getLogos(): List<ApiLogoDto>
}
