package com.yerevan.transport.data.local.database

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.yerevan.transport.data.local.entity.RouteStopCrossRef
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.data.local.entity.TransportRoute
import com.yerevan.transport.data.local.entity.TransportStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seeds the database with transport data extracted from Yerevan GIS.
 * Data source: https://gis.yerevan.am/server/rest/services/Hosted/Bus_stops_lots/FeatureServer
 *
 * The data was originally fetched from:
 * - Bus stops: Bus_stops_lots/FeatureServer/0/query (384 stops)
 * - Metro stations: Մետρο_κkeydelays/FeatureServer/0/query (10 existing stations)
 *
 * Route data is generated based on geographic analysis of stop positions,
 * creating realistic routes that connect stops along corridors and within communities.
 */
class DatabaseSeeder(
    private val context: Context,
    private val database: TransportDatabase
) {
    companion object {
        private const val TAG = "DatabaseSeeder"
        private const val ASSET_FILE = "transport_data.json"
    }

    /**
     * Check if database needs seeding.
     */
    suspend fun needsSeeding(): Boolean = withContext(Dispatchers.IO) {
        database.transportStopDao().getStopCount() == 0
    }

    /**
     * Seed the database from the bundled JSON asset.
     * This data was extracted from Yerevan GIS ArcGIS REST API.
     */
    suspend fun seedDatabase(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting database seeding...")

            val jsonString = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val transportData = Gson().fromJson(jsonString, TransportData::class.java)

            Log.d(TAG, "Parsed ${transportData.stops.size} stops, ${transportData.routes.size} routes")

            // Insert stops
            val stops = transportData.stops.map { dto ->
                TransportStop(
                    id = dto.id,
                    gisId = dto.gisId,
                    name = dto.name,
                    nameEn = dto.nameEn,
                    street = dto.street,
                    address = dto.address,
                    community = dto.community,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    stopType = StopType.valueOf(dto.stopType),
                    lot = dto.lot
                )
            }
            database.transportStopDao().insertStops(stops)
            Log.d(TAG, "Inserted ${stops.size} stops")

            // Insert routes
            val routes = transportData.routes.map { dto ->
                TransportRoute(
                    id = dto.id,
                    routeNumber = dto.routeNumber,
                    routeName = dto.routeName,
                    routeType = StopType.valueOf(dto.routeType),
                    color = dto.color,
                    avgIntervalMinutes = dto.avgIntervalMinutes,
                    operatingHours = dto.operatingHours
                )
            }
            database.transportRouteDao().insertRoutes(routes)
            Log.d(TAG, "Inserted ${routes.size} routes")

            // Insert route-stop cross references
            val crossRefs = transportData.routeStopRefs.map { dto ->
                RouteStopCrossRef(
                    routeId = dto.routeId,
                    stopId = dto.stopId,
                    stopOrder = dto.stopOrder,
                    distanceFromPrevMeters = dto.distanceFromPrevMeters,
                    timeFromPrevSeconds = dto.timeFromPrevSeconds
                )
            }
            database.transportRouteDao().insertRouteStopCrossRefs(crossRefs)
            Log.d(TAG, "Inserted ${crossRefs.size} route-stop connections")

            Log.d(TAG, "Database seeding completed successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Database seeding failed", e)
            Result.failure(e)
        }
    }
}

// DTO classes for JSON parsing
data class TransportData(
    val stops: List<StopDto>,
    val routes: List<RouteDto>,
    val routeStopRefs: List<RouteStopRefDto>
)

data class StopDto(
    val id: Long,
    val gisId: Int,
    val name: String,
    val nameEn: String = "",
    val street: String = "",
    val address: String = "",
    val community: String = "",
    val latitude: Double,
    val longitude: Double,
    val stopType: String = "BUS",
    val lot: Int = 0
)

data class RouteDto(
    val id: Long,
    val routeNumber: String,
    val routeName: String = "",
    val routeType: String = "BUS",
    val color: String = "#2196F3",
    val avgIntervalMinutes: Int = 10,
    val operatingHours: String = "07:00-22:00"
)

data class RouteStopRefDto(
    val routeId: Long,
    val stopId: Long,
    val stopOrder: Int,
    val distanceFromPrevMeters: Int = 0,
    val timeFromPrevSeconds: Int = 0
)
