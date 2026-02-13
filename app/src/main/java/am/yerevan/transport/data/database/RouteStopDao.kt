package am.yerevan.transport.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import am.yerevan.transport.data.model.RouteStop
import am.yerevan.transport.data.model.Stop

@Dao
interface RouteStopDao {
    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY direction, sequence")
    suspend fun getStopsForRoute(routeId: Long): List<RouteStop>

    @Query("""
        SELECT stops.* FROM stops
        INNER JOIN route_stops ON stops.id = route_stops.stopId
        WHERE route_stops.routeId = :routeId
        ORDER BY route_stops.direction, route_stops.sequence
    """)
    suspend fun getStopsForRouteDetailed(routeId: Long): List<Stop>

    @Query("""
        SELECT stops.* FROM stops
        INNER JOIN route_stops ON stops.id = route_stops.stopId
        WHERE route_stops.routeId = :routeId AND route_stops.direction = :direction
        ORDER BY route_stops.sequence
    """)
    suspend fun getStopsForRouteDirection(routeId: Long, direction: Int): List<Stop>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStop(routeStop: RouteStop): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStops(routeStops: List<RouteStop>)

    @Delete
    suspend fun deleteRouteStop(routeStop: RouteStop)

    @Query("DELETE FROM route_stops WHERE routeId = :routeId")
    suspend fun deleteRouteStopsForRoute(routeId: Long)

    @Query("DELETE FROM route_stops")
    suspend fun deleteAllRouteStops()

    @Query("SELECT COUNT(*) FROM route_stops")
    suspend fun getRouteStopCount(): Int

    // Check if a stop is on a route
    @Query("""
        SELECT COUNT(*) FROM route_stops 
        WHERE routeId = :routeId AND stopId = :stopId
    """)
    suspend fun isStopOnRoute(routeId: Long, stopId: Long): Int

    // Get the sequence/position of a stop on a route
    @Query("""
        SELECT sequence FROM route_stops 
        WHERE routeId = :routeId AND stopId = :stopId AND direction = :direction
        LIMIT 1
    """)
    suspend fun getStopSequence(routeId: Long, stopId: Long, direction: Int): Int?
}
