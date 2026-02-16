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
 * - Metro stations: https://gis.yerevan.am/server/rest/services/Hosted/%D5%84%D5%A5%D5%BF%D6%80%D5%B8_%D5%AF%D5%A1%D5%B5%D5%A1%D5%B6%D5%B6%D5%A5%D6%80/FeatureServer/0/query
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
        // URL-encoded Armenian: Մետրdelays_κkeydelays (Metro stations)
        private const val METRO_STATIONS_SERVICE = "\u0544\u0565\u057f\u0580\u0578_\u056f\u0561\u0575\u0561\u0576\u0576\u0565\u0580"
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
     * Only imports existing (operational) stations, not planned ones.
     */
    suspend fun fetchMetroStationsFromGis(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching metro stations from GIS API...")
            val response = apiService.getFeatures(
                serviceName = METRO_STATIONS_SERVICE
            )
            val features = response.features

            // Armenian field names from GIS:
            // \u0574\u0565\u057f\u0580\u0578_\u056f\u0561\u0575\u0561\u0576 = station name
            // \u0583\u0578\u0582\u056c = phase (existing vs planned)
            val stationNameField = "\u0574\u0565\u057f\u0580\u0578_\u056f\u0561\u0575\u0561\u0576"
            val phaseField = "\u0583\u0578\u0582\u056c"
            val existingPhase = "\u0533\u0578\u0575\u0578\u0582\u0569\u0575\u0578\u0582\u0576 \u0578\u0582\u0576\u0565\u0581\u0578\u0572"

            val stops = features.mapNotNull { feature ->
                val attrs = feature.attributes
                val geom = feature.geometry ?: return@mapNotNull null
                val stationName = attrs[stationNameField] as? String ?: return@mapNotNull null
                val phase = attrs[phaseField] as? String

                // Only include existing stations
                if (phase != existingPhase) return@mapNotNull null

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
