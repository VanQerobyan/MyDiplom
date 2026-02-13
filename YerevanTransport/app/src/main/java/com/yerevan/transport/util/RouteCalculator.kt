package com.yerevan.transport.util

import com.yerevan.transport.data.local.dao.TransportRouteDao
import com.yerevan.transport.data.local.dao.TransportStopDao
import com.yerevan.transport.data.local.entity.RouteStopCrossRef
import com.yerevan.transport.data.local.entity.TransportRoute
import com.yerevan.transport.data.local.entity.TransportStop
import kotlin.math.*

/**
 * Route calculation engine for Yerevan transport.
 *
 * Algorithm overview:
 *
 * 1. DIRECT ROUTES: Find all routes that pass through BOTH the start and end stops.
 *    For each such route, calculate:
 *    - Number of stops between start and end
 *    - Total distance (sum of inter-stop distances)
 *    - Estimated travel time
 *
 * 2. TRANSFER ROUTES: If no direct route exists (or for better options):
 *    a. Find all routes passing through the start stop
 *    b. Find all routes passing through the end stop
 *    c. For each pair of start-route and end-route, find TRANSFER STOPS
 *       (stops that appear on both routes)
 *    d. Calculate total journey: start -> transfer stop (route 1) + transfer stop -> end (route 2)
 *    e. Rank by total travel time
 *
 * 3. WALKING TRANSFERS: If stops are close enough (< 500m), suggest walking
 *    between nearby stops to connect different routes.
 *
 * 4. Results are ranked by:
 *    - Total travel time (primary)
 *    - Number of transfers (secondary)
 *    - Walking distance (tertiary)
 */
class RouteCalculator(
    private val stopDao: TransportStopDao,
    private val routeDao: TransportRouteDao
) {
    companion object {
        const val MAX_WALKING_DISTANCE_METERS = 500.0
        const val WALKING_SPEED_MPS = 1.4 // ~5 km/h walking speed
        const val TRANSFER_WAIT_SECONDS = 300 // 5 min average wait at transfer
        const val MAX_TRANSFER_RESULTS = 10
    }

    /**
     * Calculate all possible routes between two stops.
     * Returns a list of RouteResult sorted by efficiency.
     */
    suspend fun calculateRoutes(
        fromStopId: Long,
        toStopId: Long
    ): List<RouteResult> {
        val results = mutableListOf<RouteResult>()

        val fromStop = stopDao.getStopById(fromStopId) ?: return emptyList()
        val toStop = stopDao.getStopById(toStopId) ?: return emptyList()

        // 1. Find direct routes
        val directRoutes = routeDao.getDirectRoutes(fromStopId, toStopId)
        for (route in directRoutes) {
            val directResult = calculateDirectRoute(route, fromStop, toStop)
            if (directResult != null) {
                results.add(directResult)
            }
        }

        // 2. Find transfer routes
        val fromRoutes = routeDao.getRoutesForStop(fromStopId)
        val toRoutes = routeDao.getRoutesForStop(toStopId)

        for (fromRoute in fromRoutes) {
            for (toRoute in toRoutes) {
                if (fromRoute.id == toRoute.id) continue // Already handled as direct

                // Find transfer stops between these two routes
                val transferStops = routeDao.getTransferStops(fromRoute.id, toRoute.id)
                for (transferStop in transferStops) {
                    val transferResult = calculateTransferRoute(
                        fromRoute, toRoute, fromStop, toStop, transferStop
                    )
                    if (transferResult != null) {
                        results.add(transferResult)
                    }
                }

                // Also check for walking transfers (nearby stops)
                if (transferStops.isEmpty()) {
                    val walkingResult = findWalkingTransfer(
                        fromRoute, toRoute, fromStop, toStop
                    )
                    if (walkingResult != null) {
                        results.add(walkingResult)
                    }
                }
            }
        }

        // Sort by total time, then by number of transfers
        return results
            .distinctBy { "${it.segments.map { s -> "${s.route?.id}-${s.fromStop.id}-${s.toStop.id}" }}" }
            .sortedWith(compareBy({ it.totalTimeSeconds }, { it.transferCount }, { it.totalWalkingMeters }))
            .take(MAX_TRANSFER_RESULTS)
    }

    /**
     * Calculate a direct route between two stops on the same route.
     */
    private suspend fun calculateDirectRoute(
        route: TransportRoute,
        fromStop: TransportStop,
        toStop: TransportStop
    ): RouteResult? {
        val fromOrder = routeDao.getStopOrderInRoute(route.id, fromStop.id) ?: return null
        val toOrder = routeDao.getStopOrderInRoute(route.id, toStop.id) ?: return null

        val crossRefs = routeDao.getRouteStopCrossRefs(route.id)
        val orderedStops = routeDao.getOrderedStopsForRoute(route.id)

        val minOrder = minOf(fromOrder, toOrder)
        val maxOrder = maxOf(fromOrder, toOrder)

        // Calculate distance and time for this segment
        var totalDistance = 0
        var totalTime = 0
        var stopCount = 0

        val relevantRefs = crossRefs.filter { it.stopOrder in (minOrder + 1)..maxOrder }
        for (ref in relevantRefs) {
            totalDistance += ref.distanceFromPrevMeters
            totalTime += ref.timeFromPrevSeconds
            stopCount++
        }

        // If no calculated time, estimate from distance
        if (totalTime == 0 && totalDistance > 0) {
            totalTime = (totalDistance / 5.0).toInt() // ~18 km/h
        }

        // If still no data, estimate from coordinates
        if (totalDistance == 0) {
            totalDistance = haversineDistance(
                fromStop.latitude, fromStop.longitude,
                toStop.latitude, toStop.longitude
            ).toInt()
            totalTime = (totalDistance / 5.0).toInt()
        }

        val segment = RouteSegment(
            route = route,
            fromStop = fromStop,
            toStop = toStop,
            stops = orderedStops.filter {
                val order = crossRefs.find { ref -> ref.stopId == it.id }?.stopOrder ?: -1
                order in minOrder..maxOrder
            },
            distanceMeters = totalDistance,
            timeSeconds = totalTime,
            segmentType = SegmentType.TRANSIT
        )

        return RouteResult(
            segments = listOf(segment),
            totalDistanceMeters = totalDistance,
            totalTimeSeconds = totalTime + route.avgIntervalMinutes * 30, // Add half interval as avg wait
            transferCount = 0,
            totalWalkingMeters = 0
        )
    }

    /**
     * Calculate a route with one transfer.
     */
    private suspend fun calculateTransferRoute(
        fromRoute: TransportRoute,
        toRoute: TransportRoute,
        fromStop: TransportStop,
        toStop: TransportStop,
        transferStop: TransportStop
    ): RouteResult? {
        // First segment: fromStop -> transferStop on fromRoute
        val seg1 = calculateDirectRoute(fromRoute, fromStop, transferStop) ?: return null
        // Second segment: transferStop -> toStop on toRoute
        val seg2 = calculateDirectRoute(toRoute, transferStop, toStop) ?: return null

        val totalDistance = seg1.totalDistanceMeters + seg2.totalDistanceMeters
        val totalTime = seg1.totalTimeSeconds + TRANSFER_WAIT_SECONDS + seg2.totalTimeSeconds

        return RouteResult(
            segments = seg1.segments + seg2.segments,
            totalDistanceMeters = totalDistance,
            totalTimeSeconds = totalTime,
            transferCount = 1,
            totalWalkingMeters = 0,
            transferStops = listOf(transferStop)
        )
    }

    /**
     * Find a walking transfer between two routes.
     * Looks for stops on each route that are close enough to walk between.
     */
    private suspend fun findWalkingTransfer(
        fromRoute: TransportRoute,
        toRoute: TransportRoute,
        fromStop: TransportStop,
        toStop: TransportStop
    ): RouteResult? {
        val fromRouteStops = routeDao.getOrderedStopsForRoute(fromRoute.id)
        val toRouteStops = routeDao.getOrderedStopsForRoute(toRoute.id)

        var bestResult: RouteResult? = null
        var bestTime = Int.MAX_VALUE

        for (frs in fromRouteStops) {
            for (trs in toRouteStops) {
                val walkDist = haversineDistance(
                    frs.latitude, frs.longitude,
                    trs.latitude, trs.longitude
                )
                if (walkDist <= MAX_WALKING_DISTANCE_METERS) {
                    val walkTime = (walkDist / WALKING_SPEED_MPS).toInt()

                    val seg1 = calculateDirectRoute(fromRoute, fromStop, frs) ?: continue
                    val seg2 = calculateDirectRoute(toRoute, trs, toStop) ?: continue

                    val walkSegment = RouteSegment(
                        route = null,
                        fromStop = frs,
                        toStop = trs,
                        stops = listOf(frs, trs),
                        distanceMeters = walkDist.toInt(),
                        timeSeconds = walkTime,
                        segmentType = SegmentType.WALKING
                    )

                    val totalTime = seg1.totalTimeSeconds + walkTime + TRANSFER_WAIT_SECONDS + seg2.totalTimeSeconds

                    if (totalTime < bestTime) {
                        bestTime = totalTime
                        bestResult = RouteResult(
                            segments = seg1.segments + walkSegment + seg2.segments,
                            totalDistanceMeters = seg1.totalDistanceMeters + walkDist.toInt() + seg2.totalDistanceMeters,
                            totalTimeSeconds = totalTime,
                            transferCount = 1,
                            totalWalkingMeters = walkDist.toInt(),
                            transferStops = listOf(frs, trs)
                        )
                    }
                }
            }
        }

        return bestResult
    }

    /**
     * Find nearest stops to a given location.
     */
    suspend fun findNearestStops(lat: Double, lon: Double, limit: Int = 5): List<Pair<TransportStop, Double>> {
        val allStops = stopDao.getAllStopsList()
        return allStops.map { stop ->
            stop to haversineDistance(lat, lon, stop.latitude, stop.longitude)
        }.sortedBy { it.second }.take(limit)
    }
}

/**
 * Haversine formula to calculate distance between two points in meters.
 */
fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0 // Earth radius in meters
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val a = sin(deltaPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Represents a complete route result from origin to destination.
 */
data class RouteResult(
    val segments: List<RouteSegment>,
    val totalDistanceMeters: Int,
    val totalTimeSeconds: Int,
    val transferCount: Int,
    val totalWalkingMeters: Int = 0,
    val transferStops: List<TransportStop> = emptyList()
) {
    val totalTimeMinutes: Int get() = totalTimeSeconds / 60
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0

    fun getFormattedTime(): String {
        val hours = totalTimeMinutes / 60
        val mins = totalTimeMinutes % 60
        return if (hours > 0) "${hours}h ${mins}min" else "${mins} min"
    }

    fun getFormattedDistance(): String {
        return if (totalDistanceKm >= 1.0) {
            "%.1f km".format(totalDistanceKm)
        } else {
            "$totalDistanceMeters m"
        }
    }

    fun getSummary(): String {
        val routeNumbers = segments
            .filter { it.segmentType == SegmentType.TRANSIT }
            .mapNotNull { it.route?.routeNumber }
        return if (transferCount == 0) {
            "Route ${routeNumbers.firstOrNull() ?: "?"}"
        } else {
            routeNumbers.joinToString(" → ") { "Route $it" }
        }
    }
}

/**
 * Represents one segment of a journey (either transit or walking).
 */
data class RouteSegment(
    val route: TransportRoute?,
    val fromStop: TransportStop,
    val toStop: TransportStop,
    val stops: List<TransportStop>,
    val distanceMeters: Int,
    val timeSeconds: Int,
    val segmentType: SegmentType
)

enum class SegmentType {
    TRANSIT,
    WALKING
}
