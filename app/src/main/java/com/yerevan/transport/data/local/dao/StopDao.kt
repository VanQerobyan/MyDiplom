package com.yerevan.transport.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yerevan.transport.data.local.entity.StopEntity

@Dao
interface StopDao {

    @Query("SELECT * FROM stops ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<StopEntity>

    @Query("SELECT * FROM stops WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StopEntity?

    @Query("SELECT * FROM stops WHERE name LIKE :query ORDER BY name COLLATE NOCASE LIMIT :limit")
    suspend fun search(query: String, limit: Int = 12): List<StopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StopEntity>)

    @Query("DELETE FROM stops")
    suspend fun clear()
}
