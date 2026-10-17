package com.zap.tv.data

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.zap.tv.data.api.IptvApiService
import com.zap.tv.data.local.AppDatabase
import com.zap.tv.data.reidoscanais.ReiDosCanaisApi
import com.zap.tv.data.reidoscanais.ReiDosCanaisRepository
import com.zap.tv.data.repository.ChannelRepository
import com.zap.tv.data.repository.FavoriteRepository
import com.zap.tv.data.repository.ParentalRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object AppDependencies {
    @Volatile private var db: AppDatabase? = null
    @Volatile private var api: IptvApiService? = null
    @Volatile private var rdcApi: ReiDosCanaisApi? = null
    @Volatile private var rdcRepo: ReiDosCanaisRepository? = null
    @Volatile private var channelRepo: ChannelRepository? = null
    @Volatile private var favoriteRepo: FavoriteRepository? = null
    @Volatile private var parentalRepo: ParentalRepository? = null

    fun getDatabase(context: Context): AppDatabase =
        db ?: synchronized(this) {
            db ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "zap.db")
                .fallbackToDestructiveMigration()
                .build().also { db = it }
        }

    fun getApi(): IptvApiService =
        api ?: synchronized(this) {
            api ?: run {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val okHttp = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                    .build()
                Retrofit.Builder()
                    .baseUrl("https://iptv-org.github.io/api/")
                    .client(okHttp)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build().create(IptvApiService::class.java)
            }.also { api = it }
        }

    fun getReiDosCanaisApi(): ReiDosCanaisApi =
        rdcApi ?: synchronized(this) {
            rdcApi ?: run {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val okHttp = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                    .build()
                Retrofit.Builder()
                    .baseUrl(ReiDosCanaisRepository.BASE_URL)
                    .client(okHttp)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build().create(ReiDosCanaisApi::class.java)
            }.also { rdcApi = it }
        }

    fun getReiDosCanaisRepository(): ReiDosCanaisRepository =
        rdcRepo ?: synchronized(this) {
            rdcRepo ?: ReiDosCanaisRepository(getReiDosCanaisApi()).also { rdcRepo = it }
        }

    fun getChannelRepository(context: Context): ChannelRepository =
        channelRepo ?: synchronized(this) {
            channelRepo ?: ChannelRepository(
                api = getApi(),
                rdcRepository = getReiDosCanaisRepository(),
                channelNumberDao = getDatabase(context).channelNumberDao(),
                stateConfigDao = getDatabase(context).stateConfigDao(),
                lastChannelDao = getDatabase(context).lastChannelDao(),
                favoriteDao = getDatabase(context).favoriteDao(),
                appFlagDao = getDatabase(context).appFlagDao(),
                parentalDao = getDatabase(context).parentalPinDao(),
                ctx = context.applicationContext
            ).also { channelRepo = it }
        }

    fun getFavoriteRepository(context: Context): FavoriteRepository =
        favoriteRepo ?: synchronized(this) {
            favoriteRepo ?: FavoriteRepository(getDatabase(context).favoriteDao()).also { favoriteRepo = it }
        }

    fun getParentalRepository(context: Context): ParentalRepository =
        parentalRepo ?: synchronized(this) {
            parentalRepo ?: ParentalRepository(getDatabase(context).parentalPinDao()).also { parentalRepo = it }
        }
}
