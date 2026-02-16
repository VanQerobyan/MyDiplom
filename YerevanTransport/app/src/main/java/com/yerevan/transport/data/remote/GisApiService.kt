package com.yerevan.transport.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service for Yerevan GIS ArcGIS REST API.
 * Base URL: https://gis.yerevan.am/server/rest/services/Hosted/
 *
 * Data is extracted from the GIS portal:
 * https://gis.yerevan.am/portal/apps/experiencebuilder/experience/?id=13c109e913644a8d877db51465ace1f2
 */
interface GisApiService {

    /**
     * Fetch bus stops from Bus_stops_lots feature service.
     */
    @GET("Bus_stops_lots/FeatureServer/0/query")
    suspend fun getBusStops(
        @Query("where") where: String = "1=1",
        @Query("outFields") outFields: String = "*",
        @Query("f") format: String = "json",
        @Query("resultRecordCount") limit: Int = 2000,
        @Query("outSR") outSR: String = "4326",
        @Query("resultOffset") offset: Int = 0
    ): GisFeatureResponse

    /**
     * Fetch metro stations from Մետրո_կայdelays feature service.
     */
    @GET("{serviceName}/FeatureServer/0/query")
    suspend fun getFeatures(
        @Path("serviceName", encoded = true) serviceName: String,
        @Query("where") where: String = "1=1",
        @Query("outFields") outFields: String = "*",
        @Query("f") format: String = "json",
        @Query("resultRecordCount") limit: Int = 2000,
        @Query("outSR") outSR: String = "4326"
    ): GisFeatureResponse
}
