package am.yerevan.transport.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import am.yerevan.transport.data.model.Route

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes WHERE isActive = 1 ORDER BY routeNumber")
    fun getAllRoutes(): LiveData<List<Route>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): Route?

    @Query("SELECT * FROM routes WHERE routeNumber = :routeNumber")
    suspend fun getRouteByNumber(routeNumber: String): Route?

    @Query("SELECT * FROM routes WHERE routeType = :type AND isActive = 1 ORDER BY routeNumber")
    fun getRoutesByType(type: String): LiveData<List<Route>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: Route): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<Route>)

    @Update
    suspend fun updateRoute(route: Route)

    @Delete
    suspend fun deleteRoute(route: Route)

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun getRouteCount(): Int

    // Get routes that pass through a specific stop
    @Query("""
        SELECT DISTINCT routes.* FROM routes
        INNER JOIN route_stops ON routes.id = route_stops.routeId
        WHERE route_stops.stopId = :stopId AND routes.isActive = 1
        ORDER BY routes.routeNumber
    """)
    suspend fun getRoutesForStop(stopId: Long): List<Route>

    // Get common routes between two stops
    @Query("""
        SELECT DISTINCT r.* FROM routes r
        INNER JOIN route_stops rs1 ON r.id = rs1.routeId
        INNER JOIN route_stops rs2 ON r.id = rs2.routeId
        WHERE rs1.stopId = :stopId1 
        AND rs2.stopId = :stopId2
        AND rs1.direction = rs2.direction
        AND rs1.sequence < rs2.sequence
        AND r.isActive = 1
        ORDER BY r.routeNumber
    """)
    suspend fun getCommonRoutes(stopId1: Long, stopId2: Long): List<Route>
}
