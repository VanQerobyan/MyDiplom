package am.yerevan.transport.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Data class representing a route with all its stops
 */
data class RouteWithStops(
    @Embedded val route: Route,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RouteStop::class,
            parentColumn = "routeId",
            entityColumn = "stopId"
        )
    )
    val stops: List<Stop>
)

/**
 * Helper class for route search results
 */
data class StopWithRoutes(
    @Embedded val stop: Stop,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RouteStop::class,
            parentColumn = "stopId",
            entityColumn = "routeId"
        )
    )
    val routes: List<Route>
)
