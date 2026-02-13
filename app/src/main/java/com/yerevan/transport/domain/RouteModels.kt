package com.yerevan.transport.domain

data class GeoPoint(
    val lat: Double,
    val lng: Double
)

data class MapRouteShape(
    val routeId: String,
    val routeName: String,
    val mode: String,
    val geometryType: String,
    val parts: List<List<GeoPoint>>
)

sealed interface RouteOption {
    val totalDistanceMeters: Double
    val estimatedMinutes: Double

    data class Direct(
        val routeId: String,
        val routeName: String,
        val mode: String,
        val headwayMinutes: Int,
        override val totalDistanceMeters: Double,
        override val estimatedMinutes: Double
    ) : RouteOption

    data class Transfer(
        val firstRouteId: String,
        val firstRouteName: String,
        val secondRouteId: String,
        val secondRouteName: String,
        val transferFromStopId: String,
        val transferToStopId: String,
        val walkingDistanceMeters: Double,
        val headwayMinutes: Int,
        override val totalDistanceMeters: Double,
        override val estimatedMinutes: Double
    ) : RouteOption
}
