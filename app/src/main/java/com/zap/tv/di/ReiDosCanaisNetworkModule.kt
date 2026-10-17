package com.zap.tv.di

import com.squareup.moshi.Moshi
import com.zap.tv.data.reidoscanais.ReiDosCanaisApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

// Fornece o cliente da API Rei dos Canais reutilizando o Moshi e o OkHttpClient
// já configurados no NetworkModule (timeouts e logging idênticos aos da API iptv-org).
@Module
@InstallIn(SingletonComponent::class)
object ReiDosCanaisNetworkModule {

    const val REI_DOS_CANAIS_BASE_URL = "https://api.reidoscanais.si/"

    @Provides
    @Singleton
    fun provideReiDosCanaisApi(moshi: Moshi, okHttp: OkHttpClient): ReiDosCanaisApi =
        Retrofit.Builder()
            .baseUrl(REI_DOS_CANAIS_BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ReiDosCanaisApi::class.java)
}
