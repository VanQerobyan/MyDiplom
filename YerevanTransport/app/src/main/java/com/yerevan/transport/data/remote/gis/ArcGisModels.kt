package com.yerevan.transport.data.remote.gis

import com.google.gson.annotations.SerializedName

data class ArcGisFeatureResponse(
    @SerializedName("features") val features: List<ArcGisFeature>? = emptyList(),
    @SerializedName("error") val error: ArcGisError? = null
)

data class ArcGisFeature(
    @SerializedName("attributes") val attributes: Map<String, Any?>? = null,
    @SerializedName("geometry") val geometry: ArcGisGeometry? = null
)

data class ArcGisGeometry(
    @SerializedName("x") val x: Double? = null,
    @SerializedName("y") val y: Double? = null,
    @SerializedName("paths") val paths: List<List<List<Double>>>? = null,
    @SerializedName("rings") val rings: List<List<List<Double>>>? = null
)

data class ArcGisError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String
)

data class ArcGisExperienceConfig(
    @SerializedName("dataSources") val dataSources: Map<String, ArcGisDataSource>? = null,
    @SerializedName("widgets") val widgets: Map<String, Any>? = null
)

data class ArcGisDataSource(
    @SerializedName("type") val type: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("itemId") val itemId: String? = null
)
