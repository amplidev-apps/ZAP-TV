package com.zap.tv.di

import android.content.Context
import androidx.room.Room
import com.zap.tv.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "zap.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChannelNumberDao(db: AppDatabase) = db.channelNumberDao()
    @Provides fun provideFavoriteDao(db: AppDatabase) = db.favoriteDao()
    @Provides fun provideStateConfigDao(db: AppDatabase) = db.stateConfigDao()
    @Provides fun provideParentalDao(db: AppDatabase) = db.parentalPinDao()
    @Provides fun provideLastChannelDao(db: AppDatabase) = db.lastChannelDao()
    @Provides fun provideAppFlagDao(db: AppDatabase) = db.appFlagDao()
}
