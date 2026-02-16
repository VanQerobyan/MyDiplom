package com.yerevan.transport.data.local.dao

import androidx.room.*
import com.yerevan.transport.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransportRouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: TransportRoute): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<TransportRoute>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStopCrossRef(crossRef: RouteStopCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteStopCrossRefs(crossRefs: List<RouteStopCrossRef>)

    @Query("SELECT * FROM transport_routes ORDER BY route_number")
    fun getAllRoutes(): Flow<List<TransportRoute>>

    @Query("SELECT * FROM transport_routes ORDER BY route_number")
    suspend fun getAllRoutesList(): List<TransportRoute>

    @Query("SELECT * FROM transport_routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): TransportRoute?

    @Query("SELECT * FROM transport_routes WHERE route_type = :type ORDER BY route_number")
    fun getRoutesByType(type: StopType): Flow<List<TransportRoute>>

    @Transaction
    @Query("SELECT * FROM transport_routes WHERE id = :routeId")
    suspend fun getRouteWithStops(routeId: Long): RouteWithStops?

    @Transaction
    @Query("SELECT * FROM transport_routes")
    suspend fun getAllRoutesWithStops(): List<RouteWithStops>

    @Query("""
        SELECT DISTINCT tr.* FROM transport_routes tr
        INNER JOIN route_stop_cross_ref rsc ON tr.id = rsc.route_id
        WHERE rsc.stop_id = :stopId
        ORDER BY tr.route_number
    """)
    suspend fun getRoutesForStop(stopId: Long): List<TransportRoute>

    @Query("""
        SELECT DISTINCT tr.* FROM transport_routes tr
        INNER JOIN route_stop_cross_ref rsc1 ON tr.id = rsc1.route_id
        INNER JOIN route_stop_cross_ref rsc2 ON tr.id = rsc2.route_id
        WHERE rsc1.stop_id = :stopId1 AND rsc2.stop_id = :stopId2
        ORDER BY tr.route_number
    """)
    suspend fun getDirectRoutes(stopId1: Long, stopId2: Long): List<TransportRoute>

    @Query("""
        SELECT rsc.* FROM route_stop_cross_ref rsc
        WHERE rsc.route_id = :routeId
        ORDER BY rsc.stop_order
    """)
    suspend fun getRouteStopCrossRefs(routeId: Long): List<RouteStopCrossRef>

    @Query("""
        SELECT ts.* FROM transport_stops ts
        INNER JOIN route_stop_cross_ref rsc ON ts.id = rsc.stop_id
        WHERE rsc.route_id = :routeId
        ORDER BY rsc.stop_order
    """)
    suspend fun getOrderedStopsForRoute(routeId: Long): List<TransportStop>

    @Query("SELECT COUNT(*) FROM transport_routes")
    suspend fun getRouteCount(): Int

    @Query("""
        SELECT rsc.stop_order FROM route_stop_cross_ref rsc
        WHERE rsc.route_id = :routeId AND rsc.stop_id = :stopId
    """)
    suspend fun getStopOrderInRoute(routeId: Long, stopId: Long): Int?

    @Query("""
        SELECT DISTINCT ts.* FROM transport_stops ts
        INNER JOIN route_stop_cross_ref rsc1 ON ts.id = rsc1.stop_id
        INNER JOIN route_stop_cross_ref rsc2 ON ts.id = rsc2.stop_id
        WHERE rsc1.route_id = :routeId1 AND rsc2.route_id = :routeId2
    """)
    suspend fun getTransferStops(routeId1: Long, routeId2: Long): List<TransportStop>
}
