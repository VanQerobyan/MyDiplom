package com.yerevan.transport.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yerevan.transport.data.local.entity.RouteStopEntity

@Dao
interface RouteStopDao {

    @Query("SELECT * FROM route_stops")
    suspend fun getAll(): List<RouteStopEntity>

    @Query("SELECT routeId FROM route_stops WHERE stopId = :stopId")
    suspend fun getRouteIdsByStop(stopId: String): List<String>

    @Query("SELECT stopId FROM route_stops WHERE routeId = :routeId ORDER BY projectedDistanceMeters")
    suspend fun getStopIdsByRoute(routeId: String): List<String>

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY projectedDistanceMeters")
    suspend fun getMembershipsForRoute(routeId: String): List<RouteStopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RouteStopEntity>)

    @Query("DELETE FROM route_stops")
    suspend fun clear()
}
