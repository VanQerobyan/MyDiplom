package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "route_stops",
    primaryKeys = ["routeId", "stopId"],
    indices = [Index("stopId"), Index("routeId")]
)
data class RouteStopEntity(
    val routeId: String,
    val stopId: String,
    val distanceToRouteMeters: Double,
    val projectedDistanceMeters: Double
)
