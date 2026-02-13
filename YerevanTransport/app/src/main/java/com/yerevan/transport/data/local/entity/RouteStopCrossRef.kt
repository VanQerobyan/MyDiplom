package com.yerevan.transport.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference table linking routes to their stops with ordering.
 * This represents which stops belong to which route and in what order.
 */
@Entity(
    tableName = "route_stop_cross_ref",
    primaryKeys = ["route_id", "stop_id"],
    foreignKeys = [
        ForeignKey(
            entity = TransportRoute::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransportStop::class,
            parentColumns = ["id"],
            childColumns = ["stop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("route_id"),
        Index("stop_id"),
        Index("route_id", "stop_order")
    ]
)
data class RouteStopCrossRef(
    @ColumnInfo(name = "route_id")
    val routeId: Long,

    @ColumnInfo(name = "stop_id")
    val stopId: Long,

    @ColumnInfo(name = "stop_order")
    val stopOrder: Int,

    @ColumnInfo(name = "distance_from_prev_meters")
    val distanceFromPrevMeters: Int = 0,

    @ColumnInfo(name = "time_from_prev_seconds")
    val timeFromPrevSeconds: Int = 0
)
