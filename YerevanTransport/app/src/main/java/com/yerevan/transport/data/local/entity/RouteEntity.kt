package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routes",
    indices = [Index(value = ["number"])]
)
data class RouteEntity(
    @PrimaryKey
    val id: String,
    val number: String,
    val name: String?,
    val transportType: String, // bus, trolleybus, minibus
    val color: String?
)
