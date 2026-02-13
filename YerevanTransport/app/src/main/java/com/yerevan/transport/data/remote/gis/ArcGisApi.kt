package com.yerevan.transport.data.remote.gis

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * ArcGIS REST API interface for fetching transport data from Yerevan GIS.
 * The GIS portal (https://gis.yerevan.am) uses ArcGIS services.
 * Pass full URL with query string: .../query?where=1%3D1&outFields=*&returnGeometry=true&f=json
 */
interface ArcGisApi {
    @GET
    suspend fun getFeatureLayer(@Url url: String): ArcGisFeatureResponse
}
