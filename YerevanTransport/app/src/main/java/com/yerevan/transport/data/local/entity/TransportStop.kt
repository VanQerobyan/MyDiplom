package com.yerevan.transport.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a transport stop (bus stop, metro station, etc.)
 * Data sourced from Yerevan GIS: https://gis.yerevan.am
 */
@Entity(tableName = "transport_stops")
data class TransportStop(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "gis_id")
    val gisId: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "name_en")
    val nameEn: String = "",

    @ColumnInfo(name = "street")
    val street: String = "",

    @ColumnInfo(name = "address")
    val address: String = "",

    @ColumnInfo(name = "community")
    val community: String = "",

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "stop_type")
    val stopType: StopType = StopType.BUS,

    @ColumnInfo(name = "lot")
    val lot: Int = 0
)

enum class StopType {
    BUS,
    METRO,
    TROLLEYBUS,
    MINIBUS
}
