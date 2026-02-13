package com.yerevan.transport.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yerevan.transport.data.local.entity.RouteEntity

@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RouteEntity>)

    @Query("DELETE FROM routes")
    suspend fun clear()
}
