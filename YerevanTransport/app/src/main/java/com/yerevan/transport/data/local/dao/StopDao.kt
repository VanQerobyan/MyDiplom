package com.yerevan.transport.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yerevan.transport.data.local.entity.StopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)

    @Query("SELECT * FROM stops ORDER BY name")
    fun getAllStops(): Flow<List<StopEntity>>

    @Query("SELECT * FROM stops WHERE name LIKE '%' || :query || '%' OR nameRu LIKE '%' || :query || '%' ORDER BY name LIMIT 20")
    suspend fun searchStops(query: String): List<StopEntity>

    @Query("SELECT * FROM stops WHERE id = :stopId")
    suspend fun getStopById(stopId: String): StopEntity?

    @Query("SELECT * FROM stops WHERE name = :name OR nameRu = :name LIMIT 1")
    suspend fun getStopByName(name: String): StopEntity?

    @Query("SELECT * FROM stops WHERE id IN (:stopIds)")
    suspend fun getStopsByIds(stopIds: List<String>): List<StopEntity>

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun getStopCount(): Int

    @Query("SELECT * FROM stops ORDER BY name")
    suspend fun getAllStopsOnce(): List<StopEntity>

    @Query("DELETE FROM stops")
    suspend fun deleteAll()
}
