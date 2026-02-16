package com.yerevan.transport.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a transport route (bus line, metro line, trolleybus line, etc.)
 */
@Entity(tableName = "transport_routes")
data class TransportRoute(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "route_number")
    val routeNumber: String,

    @ColumnInfo(name = "route_name")
    val routeName: String = "",

    @ColumnInfo(name = "route_type")
    val routeType: StopType = StopType.BUS,

    @ColumnInfo(name = "color")
    val color: String = "#2196F3",

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "avg_interval_minutes")
    val avgIntervalMinutes: Int = 10,

    @ColumnInfo(name = "operating_hours")
    val operatingHours: String = "07:00-22:00"
)
