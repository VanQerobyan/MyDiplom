package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_stops",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StopEntity::class,
            parentColumns = ["id"],
            childColumns = ["stopId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routeId"]), Index(value = ["stopId"])]
)
data class RouteStopEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: String,
    val stopId: String,
    val sequence: Int,
    val direction: String = "forward" // forward or backward
)
