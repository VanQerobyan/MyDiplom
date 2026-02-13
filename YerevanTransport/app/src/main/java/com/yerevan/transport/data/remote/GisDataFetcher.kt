package com.yerevan.transport.data.remote

import android.util.Log
import com.yerevan.transport.data.local.database.TransportDatabase
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.data.local.entity.TransportStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches transport data from Yerevan GIS ArcGIS REST API.
 *
 * API Endpoints used:
 * - Bus stops: https://gis.yerevan.am/server/rest/services/Hosted/Bus_stops_lots/FeatureServer/0/query
 * - Metro stations: https://gis.yerevan.am/server/rest/services/Hosted/Մետρο_κkeydelays/FeatureServer/0/query
 *
 * The Experience Builder app that visualizes this data:
 * https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2
 */
class GisDataFetcher(
    private val apiService: GisApiService,
    private val database: TransportDatabase
) {
    companion object {
        private const val TAG = "GisDataFetcher"
        private const val METRO_STATIONS_SERVICE = "Մետdelays_κkeydelays"
    }

    /**
     * Fetch bus stops from GIS and update the database.
     * This connects to the live ArcGIS Feature Service.
     */
    suspend fun fetchBusStopsFromGis(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching bus stops from GIS API...")
            val response = apiService.getBusStops()
            val features = response.features

            Log.d(TAG, "Received ${features.size} bus stops from GIS")

            val stops = features.mapNotNull { feature ->
                val attrs = feature.attributes
                val geom = feature.geometry ?: return@mapNotNull null

                TransportStop(
                    gisId = (attrs["fid"] as? Number)?.toInt() ?: 0,
                    name = (attrs["street"] as? String)?.trim() ?: "",
                    street = (attrs["street"] as? String)?.trim() ?: "",
                    address = (attrs["address"] as? String)?.trim() ?: "",
                    community = (attrs["community"] as? String)?.trim() ?: "",
                    latitude = geom.y ?: 0.0,
                    longitude = geom.x ?: 0.0,
                    stopType = StopType.BUS,
                    lot = (attrs["lot"] as? Number)?.toInt() ?: 0
                )
            }

            database.transportStopDao().insertStops(stops)
            Log.d(TAG, "Saved ${stops.size} bus stops to database")
            Result.success(stops.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch bus stops from GIS", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch metro stations from GIS and update the database.
     */
    suspend fun fetchMetroStationsFromGis(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching metro stations from GIS API...")
            val response = apiService.getFeatures(
                serviceName = "Մետρο_κkeydelays"
            )
            val features = response.features

            val stops = features.mapNotNull { feature ->
                val attrs = feature.attributes
                val geom = feature.geometry ?: return@mapNotNull null
                val stationName = attrs["մdelays_κkey"] as? String ?: return@mapNotNull null
                val phase = attrs["φkey"] as? String
                // Only include existing stations
                if (phase != "Գdelays delaysdelays") return@mapNotNull null

                TransportStop(
                    gisId = 10000 + ((attrs["objectid"] as? Number)?.toInt() ?: 0),
                    name = stationName.trim(),
                    latitude = geom.y ?: 0.0,
                    longitude = geom.x ?: 0.0,
                    stopType = StopType.METRO
                )
            }

            database.transportStopDao().insertStops(stops)
            Log.d(TAG, "Saved ${stops.size} metro stations to database")
            Result.success(stops.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch metro stations from GIS", e)
            Result.failure(e)
        }
    }
}
