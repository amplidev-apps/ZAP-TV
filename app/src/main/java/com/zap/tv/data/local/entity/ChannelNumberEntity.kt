package com.zap.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_numbers")
data class ChannelNumberEntity(
    @PrimaryKey val number: Int,
    val channelId: String,
    val channelName: String,
    val country: String,
    val categories: String, // comma separated
    val logoUrl: String?,
    val isNsfw: Boolean,
    val streamCount: Int,
    val affiliateState: String? = null,
    val isAvailable: Boolean = true,
    val lastCheckedMs: Long = 0
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val channelNumber: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "state_config")
data class StateConfigEntity(
    @PrimaryKey val id: Int = 1,
    val uf: String, // "SP", "RJ", "NACIONAL"
    val isSetupComplete: Boolean = true
)

@Entity(tableName = "parental_pin")
data class ParentalPinEntity(
    @PrimaryKey val id: Int = 1,
    val pinHash: String?, // null = disabled, SHA-256 hex
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long = 0
)

@Entity(tableName = "last_channel")
data class LastChannelEntity(
    @PrimaryKey val id: Int = 1,
    val channelNumber: Int
)

@Entity(tableName = "app_flags")
data class AppFlagEntity(
    @PrimaryKey val key: String,
    val value: String
)
