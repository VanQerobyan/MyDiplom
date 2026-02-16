package com.yerevan.transport.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Combined data class for a route with all its stops.
 */
data class RouteWithStops(
    @Embedded
    val route: TransportRoute,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RouteStopCrossRef::class,
            parentColumn = "route_id",
            entityColumn = "stop_id"
        )
    )
    val stops: List<TransportStop>
)

/**
 * Combined data class for a stop with all routes passing through it.
 */
data class StopWithRoutes(
    @Embedded
    val stop: TransportStop,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RouteStopCrossRef::class,
            parentColumn = "stop_id",
            entityColumn = "route_id"
        )
    )
    val routes: List<TransportRoute>
)
