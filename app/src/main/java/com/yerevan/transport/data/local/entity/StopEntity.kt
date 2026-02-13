package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val sourceLayerTitle: String,
    val sourceLayerUrl: String,
    val rawAttributesJson: String
)
