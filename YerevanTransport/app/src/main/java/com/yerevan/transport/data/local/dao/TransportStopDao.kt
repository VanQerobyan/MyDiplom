package com.yerevan.transport.data.local.dao

import androidx.room.*
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.data.local.entity.StopWithRoutes
import com.yerevan.transport.data.local.entity.TransportStop
import kotlinx.coroutines.flow.Flow

@Dao
interface TransportStopDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: TransportStop): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<TransportStop>): List<Long>

    @Update
    suspend fun updateStop(stop: TransportStop)

    @Delete
    suspend fun deleteStop(stop: TransportStop)

    @Query("SELECT * FROM transport_stops ORDER BY name")
    fun getAllStops(): Flow<List<TransportStop>>

    @Query("SELECT * FROM transport_stops ORDER BY name")
    suspend fun getAllStopsList(): List<TransportStop>

    @Query("SELECT * FROM transport_stops WHERE id = :stopId")
    suspend fun getStopById(stopId: Long): TransportStop?

    @Query("SELECT * FROM transport_stops WHERE gis_id = :gisId")
    suspend fun getStopByGisId(gisId: Int): TransportStop?

    @Query("""
        SELECT * FROM transport_stops 
        WHERE name LIKE '%' || :query || '%' 
        OR name_en LIKE '%' || :query || '%'
        OR street LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT :limit
    """)
    fun searchStops(query: String, limit: Int = 20): Flow<List<TransportStop>>

    @Query("""
        SELECT * FROM transport_stops 
        WHERE name LIKE '%' || :query || '%' 
        OR name_en LIKE '%' || :query || '%'
        OR street LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT :limit
    """)
    suspend fun searchStopsList(query: String, limit: Int = 20): List<TransportStop>

    @Query("SELECT * FROM transport_stops WHERE stop_type = :type ORDER BY name")
    fun getStopsByType(type: StopType): Flow<List<TransportStop>>

    @Query("SELECT * FROM transport_stops WHERE community = :community ORDER BY name")
    fun getStopsByCommunity(community: String): Flow<List<TransportStop>>

    @Query("SELECT DISTINCT community FROM transport_stops ORDER BY community")
    fun getAllCommunities(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM transport_stops WHERE id = :stopId")
    suspend fun getStopWithRoutes(stopId: Long): StopWithRoutes?

    @Query("SELECT COUNT(*) FROM transport_stops")
    suspend fun getStopCount(): Int

    @Query("""
        SELECT * FROM transport_stops 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getStopsInBounds(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<TransportStop>
}
