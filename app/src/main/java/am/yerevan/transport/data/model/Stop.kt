package am.yerevan.transport.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stops",
    indices = [Index(value = ["name"], unique = false)]
)
data class Stop(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val nameEn: String? = null,
    val latitude: Double,
    val longitude: Double,
    val type: String = "bus_stop" // bus_stop, trolleybus_stop, minibus_stop
) {
    fun distanceTo(other: Stop): Double {
        // Haversine formula for calculating distance between two points
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}
