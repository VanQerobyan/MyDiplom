package com.yerevan.transport.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yerevan.transport.data.local.entity.RouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)

    @Query("SELECT * FROM routes ORDER BY number")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: String): RouteEntity?

    @Query("SELECT * FROM routes WHERE id IN (:routeIds)")
    suspend fun getRoutesByIds(routeIds: List<String>): List<RouteEntity>

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun getRouteCount(): Int

    @Query("DELETE FROM routes")
    suspend fun deleteAll()
}
