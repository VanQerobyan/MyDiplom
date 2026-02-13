package com.yerevan.transport.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yerevan.transport.data.local.entity.RouteStopEntity

@Dao
interface RouteStopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routeStops: List<RouteStopEntity>)

    @Query("SELECT stopId FROM route_stops WHERE routeId = :routeId AND direction = :direction ORDER BY sequence")
    suspend fun getStopIdsForRoute(routeId: String, direction: String = "forward"): List<String>

    @Query("SELECT routeId FROM route_stops WHERE stopId = :stopId")
    suspend fun getRouteIdsForStop(stopId: String): List<String>

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY sequence")
    suspend fun getRouteStops(routeId: String): List<RouteStopEntity>

    @Query("SELECT COUNT(*) FROM route_stops")
    suspend fun getRouteStopCount(): Int

    @Query("DELETE FROM route_stops")
    suspend fun deleteAll()
}
