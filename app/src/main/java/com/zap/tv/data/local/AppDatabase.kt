package com.zap.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zap.tv.data.local.dao.*
import com.zap.tv.data.local.entity.*

@Database(
    entities = [
        ChannelNumberEntity::class,
        FavoriteEntity::class,
        StateConfigEntity::class,
        ParentalPinEntity::class,
        LastChannelEntity::class,
        AppFlagEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelNumberDao(): ChannelNumberDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun stateConfigDao(): StateConfigDao
    abstract fun parentalPinDao(): ParentalPinDao
    abstract fun lastChannelDao(): LastChannelDao
    abstract fun appFlagDao(): AppFlagDao
}
