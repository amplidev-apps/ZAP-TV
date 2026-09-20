package com.zap.tv.data.local.dao

import androidx.room.*
import com.zap.tv.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelNumberDao {
    @Query("SELECT * FROM channel_numbers ORDER BY number ASC")
    suspend fun getAll(): List<ChannelNumberEntity>
    @Query("SELECT * FROM channel_numbers WHERE isAvailable = 1 ORDER BY number ASC")
    suspend fun getAllAvailable(): List<ChannelNumberEntity>
    @Query("SELECT * FROM channel_numbers ORDER BY number ASC")
    fun observeAll(): Flow<List<ChannelNumberEntity>>
    @Query("SELECT * FROM channel_numbers WHERE number = :number LIMIT 1")
    suspend fun getByNumber(number: Int): ChannelNumberEntity?
    @Query("SELECT * FROM channel_numbers WHERE country = :country ORDER BY number ASC")
    suspend fun getByCountry(country: String): List<ChannelNumberEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelNumberEntity>)
    @Query("UPDATE channel_numbers SET isAvailable = :available, lastCheckedMs = :checked WHERE number = :number")
    suspend fun updateAvailability(number: Int, available: Boolean, checked: Long)
    @Query("DELETE FROM channel_numbers")
    suspend fun clear()
    @Query("SELECT COUNT(*) FROM channel_numbers")
    suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM channel_numbers WHERE isAvailable = 1")
    suspend fun countAvailable(): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE channelNumber = :number)")
    suspend fun isFavorite(number: Int): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE channelNumber = :number")
    suspend fun remove(number: Int)
}

@Dao
interface StateConfigDao {
    @Query("SELECT * FROM state_config WHERE id = 1 LIMIT 1")
    suspend fun get(): StateConfigEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: StateConfigEntity)
}

@Dao
interface ParentalPinDao {
    @Query("SELECT * FROM parental_pin WHERE id = 1 LIMIT 1")
    suspend fun get(): ParentalPinEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ParentalPinEntity)
}

@Dao
interface LastChannelDao {
    @Query("SELECT * FROM last_channel WHERE id = 1 LIMIT 1")
    suspend fun get(): LastChannelEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: LastChannelEntity)
}

@Dao
interface AppFlagDao {
    @Query("SELECT value FROM app_flags WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: AppFlagEntity)
}
