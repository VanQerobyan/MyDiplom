package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val experienceItemId: String,
    val syncedAtEpochMs: Long,
    val stopCount: Int,
    val routeCount: Int
)
