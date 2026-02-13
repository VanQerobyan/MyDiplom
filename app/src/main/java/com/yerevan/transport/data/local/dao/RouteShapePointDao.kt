package com.yerevan.transport.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yerevan.transport.data.local.entity.RouteShapePointEntity

@Dao
interface RouteShapePointDao {

    @Query("SELECT * FROM route_shape_points ORDER BY routeId, partIndex, pointIndex")
    suspend fun getAll(): List<RouteShapePointEntity>

    @Query("SELECT * FROM route_shape_points WHERE routeId IN (:routeIds) ORDER BY routeId, partIndex, pointIndex")
    suspend fun getForRoutes(routeIds: List<String>): List<RouteShapePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RouteShapePointEntity>)

    @Query("DELETE FROM route_shape_points")
    suspend fun clear()
}
