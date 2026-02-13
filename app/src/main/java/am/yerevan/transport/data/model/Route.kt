package am.yerevan.transport.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routes",
    indices = [Index(value = ["routeNumber"], unique = false)]
)
data class Route(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeNumber: String,
    val routeName: String,
    val routeType: String, // bus, trolleybus, minibus
    val color: String = "#2196F3",
    val isActive: Boolean = true
)
