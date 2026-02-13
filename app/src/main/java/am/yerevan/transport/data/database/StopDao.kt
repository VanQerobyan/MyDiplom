package am.yerevan.transport.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import am.yerevan.transport.data.model.Stop

@Dao
interface StopDao {
    @Query("SELECT * FROM stops ORDER BY name")
    fun getAllStops(): LiveData<List<Stop>>

    @Query("SELECT * FROM stops WHERE id = :stopId")
    suspend fun getStopById(stopId: Long): Stop?

    @Query("SELECT * FROM stops WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 20")
    suspend fun searchStops(query: String): List<Stop>

    @Query("SELECT * FROM stops WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 20")
    fun searchStopsLive(query: String): LiveData<List<Stop>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: Stop): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<Stop>)

    @Update
    suspend fun updateStop(stop: Stop)

    @Delete
    suspend fun deleteStop(stop: Stop)

    @Query("DELETE FROM stops")
    suspend fun deleteAllStops()

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun getStopCount(): Int

    // Find nearest stops to a given location
    @Query("""
        SELECT * FROM stops
        WHERE (:latitude - latitude) * (:latitude - latitude) + 
              (:longitude - longitude) * (:longitude - longitude) < 0.0001
        ORDER BY (:latitude - latitude) * (:latitude - latitude) + 
                 (:longitude - longitude) * (:longitude - longitude)
        LIMIT :limit
    """)
    suspend fun getNearbyStops(latitude: Double, longitude: Double, limit: Int = 10): List<Stop>
}
