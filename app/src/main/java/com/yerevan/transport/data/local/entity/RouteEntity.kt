package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mode: String,
    val geometryType: String,
    val sourceLayerTitle: String,
    val sourceLayerUrl: String,
    val rawAttributesJson: String
)
