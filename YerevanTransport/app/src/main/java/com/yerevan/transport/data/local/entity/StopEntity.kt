package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stops",
    indices = [Index(value = ["name"]), Index(value = ["latitude", "longitude"])]
)
data class StopEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val nameRu: String?,
    val latitude: Double,
    val longitude: Double,
    val stopCode: String?,
    val routes: String = "" // Comma-separated route IDs serving this stop
)
