package com.zap.tv.data.reidoscanais

import retrofit2.http.GET
import retrofit2.http.Path

// DTOs matching api.reidoscanais.si JSON (documentação: https://reidoscanais.si/api)
// Todos os campos toleram ausência/null — EPG e metadados são opcionais e o app
// não pode falhar quando a programação estiver ausente.
// embed_url é uma página web (https://rdcanais.org/...) e NÃO um stream reproduzível
// pelo ExoPlayer — verificado em 100% dos embeds da API em 2026-10-16.

data class RdcHealthDto(
    val success: Boolean = false,
    val status: String? = null
)

data class RdcChannelsResponseDto(
    val success: Boolean = false,
    val data: List<RdcChannelDto> = emptyList(),
    val total: Int = 0
)

data class RdcChannelResponseDto(
    val success: Boolean = false,
    val data: RdcChannelDto? = null
)

data class RdcCategoriesResponseDto(
    val success: Boolean = false,
    val data: List<RdcCategoryDto> = emptyList(),
    val total: Int = 0
)

data class RdcCategoryDto(
    val id: Long? = null,
    val name: String? = null
)

data class RdcChannelDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val logo_url: String? = null,
    val category: String? = null,
    val embeds: List<RdcEmbedDto> = emptyList(),
    val epg: RdcEpgDto? = null
)

data class RdcEmbedDto(
    val provider: String? = null,
    val quality: String? = null,
    val embed_url: String? = null
)

data class RdcEpgDto(
    val current: RdcProgramDto? = null,
    val next: RdcProgramDto? = null
)

data class RdcProgramDto(
    val title: String? = null,
    val description: String? = null,
    val formatted_time: String? = null,
    val start_time: Long? = null,
    val end_time: Long? = null,
    val image: String? = null
)

interface ReiDosCanaisApi {
    @GET("health")
    suspend fun health(): RdcHealthDto

    @GET("channels")
    suspend fun getChannels(): RdcChannelsResponseDto

    @GET("channels/{id}")
    suspend fun getChannel(@Path("id") id: String): RdcChannelResponseDto

    @GET("channels/categories")
    suspend fun getCategories(): RdcCategoriesResponseDto
}
