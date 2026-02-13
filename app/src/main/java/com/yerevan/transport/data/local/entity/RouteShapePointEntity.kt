package com.yerevan.transport.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "route_shape_points",
    primaryKeys = ["routeId", "partIndex", "pointIndex"],
    indices = [Index("routeId")]
)
data class RouteShapePointEntity(
    val routeId: String,
    val partIndex: Int,
    val pointIndex: Int,
    val lat: Double,
    val lng: Double,
    val geometryRole: String
)
