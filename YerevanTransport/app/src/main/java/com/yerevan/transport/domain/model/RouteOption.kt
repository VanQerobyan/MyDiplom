package com.yerevan.transport.domain.model

data class RouteOption(
    val segments: List<Segment>,
    val totalDistanceMeters: Double,
    val totalEstimatedMinutes: Int,
    val transferCount: Int,
    val transferStop: TransportStop? = null
) {
    data class Segment(
        val routeId: String,
        val routeNumber: String,
        val transportType: String,
        val fromStopId: String,
        val toStopId: String,
        val stops: List<TransportStop>,
        val estimatedDistanceMeters: Double,
        val estimatedMinutes: Int
    )
}
