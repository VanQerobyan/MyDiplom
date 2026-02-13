package com.yerevan.transport.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Response model for ArcGIS Feature Service queries.
 */
data class GisFeatureResponse(
    @SerializedName("features")
    val features: List<GisFeature> = emptyList(),

    @SerializedName("exceededTransferLimit")
    val exceededTransferLimit: Boolean = false,

    @SerializedName("objectIdFieldName")
    val objectIdFieldName: String = "",

    @SerializedName("geometryType")
    val geometryType: String = ""
)

data class GisFeature(
    @SerializedName("attributes")
    val attributes: Map<String, Any?> = emptyMap(),

    @SerializedName("geometry")
    val geometry: GisGeometry? = null
)

data class GisGeometry(
    @SerializedName("x")
    val x: Double? = null,

    @SerializedName("y")
    val y: Double? = null,

    @SerializedName("rings")
    val rings: List<List<List<Double>>>? = null,

    @SerializedName("paths")
    val paths: List<List<List<Double>>>? = null
)
