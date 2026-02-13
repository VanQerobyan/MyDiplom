package am.yerevan.transport.utils

import am.yerevan.transport.data.database.RouteDao
import am.yerevan.transport.data.database.RouteStopDao
import am.yerevan.transport.data.database.StopDao
import am.yerevan.transport.data.model.Route
import am.yerevan.transport.data.model.Stop
import am.yerevan.transport.data.repository.DirectRoute
import am.yerevan.transport.data.repository.RouteSearchResult
import am.yerevan.transport.data.repository.TransferRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Route calculation engine using graph-based pathfinding
 */
class RouteCalculator(
    private val stopDao: StopDao,
    private val routeDao: RouteDao,
    private val routeStopDao: RouteStopDao
) {
    companion object {
        private const val AVERAGE_SPEED_KMH = 25.0 // Average transport speed
        private const val WALKING_SPEED_KMH = 5.0  // Average walking speed
        private const val STOP_WAIT_TIME = 2       // Minutes to wait at each stop
        private const val TRANSFER_WAIT_TIME = 5   // Minutes to wait for transfer
        private const val MAX_WALKING_DISTANCE = 500.0 // Maximum walking distance in meters
    }

    /**
     * Find all possible routes between two stops
     */
    suspend fun findRoutes(fromStopId: Long, toStopId: Long): RouteSearchResult = 
        withContext(Dispatchers.IO) {
            val fromStop = stopDao.getStopById(fromStopId) ?: return@withContext RouteSearchResult()
            val toStop = stopDao.getStopById(toStopId) ?: return@withContext RouteSearchResult()

            // Find direct routes
            val directRoutes = findDirectRoutes(fromStopId, toStopId)

            // If no direct routes, find routes with one transfer
            val transferRoutes = if (directRoutes.isEmpty()) {
                findTransferRoutes(fromStopId, toStopId, fromStop, toStop)
            } else {
                emptyList()
            }

            RouteSearchResult(
                directRoutes = directRoutes,
                transferRoutes = transferRoutes,
                hasResults = directRoutes.isNotEmpty() || transferRoutes.isNotEmpty()
            )
        }

    /**
     * Find direct routes between two stops (no transfers)
     */
    private suspend fun findDirectRoutes(fromStopId: Long, toStopId: Long): List<DirectRoute> {
        val commonRoutes = routeDao.getCommonRoutes(fromStopId, toStopId)
        val directRoutes = mutableListOf<DirectRoute>()

        for (route in commonRoutes) {
            // Get all stops for this route
            val routeStops = routeStopDao.getStopsForRouteDetailed(route.id)
            
            // Find the segment between from and to stops
            val fromIndex = routeStops.indexOfFirst { it.id == fromStopId }
            val toIndex = routeStops.indexOfFirst { it.id == toStopId }

            if (fromIndex >= 0 && toIndex > fromIndex) {
                val segmentStops = routeStops.subList(fromIndex, toIndex + 1)
                val distance = calculateTotalDistance(segmentStops)
                val time = calculateTravelTime(distance, segmentStops.size)

                directRoutes.add(
                    DirectRoute(
                        route = route,
                        stops = segmentStops,
                        estimatedTime = time
                    )
                )
            }
        }

        return directRoutes.sortedBy { it.estimatedTime }
    }

    /**
     * Find routes with one transfer
     */
    private suspend fun findTransferRoutes(
        fromStopId: Long, 
        toStopId: Long,
        fromStop: Stop,
        toStop: Stop
    ): List<TransferRoute> {
        val transferRoutes = mutableListOf<TransferRoute>()

        // Get all routes from the starting stop
        val fromRoutes = routeDao.getRoutesForStop(fromStopId)
        
        // Get all routes to the destination stop
        val toRoutes = routeDao.getRoutesForStop(toStopId)

        // Find potential transfer points
        for (fromRoute in fromRoutes) {
            val fromRouteStops = routeStopDao.getStopsForRouteDetailed(fromRoute.id)
            val fromStopIndex = fromRouteStops.indexOfFirst { it.id == fromStopId }
            
            if (fromStopIndex < 0) continue

            // Check each stop on the first route as a potential transfer point
            for (i in fromStopIndex + 1 until fromRouteStops.size) {
                val potentialTransferStop = fromRouteStops[i]

                // Check if any route from the transfer stop goes to destination
                for (toRoute in toRoutes) {
                    if (toRoute.id == fromRoute.id) continue // Skip same route

                    val toRouteStops = routeStopDao.getStopsForRouteDetailed(toRoute.id)
                    val transferStopIndex = toRouteStops.indexOfFirst { it.id == potentialTransferStop.id }
                    val toStopIndex = toRouteStops.indexOfFirst { it.id == toStopId }

                    if (transferStopIndex >= 0 && toStopIndex > transferStopIndex) {
                        // Valid transfer found
                        val firstSegment = fromRouteStops.subList(fromStopIndex, transferStopIndex + 1)
                        val secondSegment = toRouteStops.subList(transferStopIndex, toStopIndex + 1)

                        val firstDistance = calculateTotalDistance(firstSegment)
                        val secondDistance = calculateTotalDistance(secondSegment)
                        val walkingDistance = 0.0 // Assuming transfer at same stop

                        val totalTime = calculateTravelTime(firstDistance, firstSegment.size) +
                                TRANSFER_WAIT_TIME +
                                calculateTravelTime(secondDistance, secondSegment.size)

                        transferRoutes.add(
                            TransferRoute(
                                firstRoute = fromRoute,
                                secondRoute = toRoute,
                                transferStop = potentialTransferStop,
                                firstSegmentStops = firstSegment,
                                secondSegmentStops = secondSegment,
                                walkingDistance = walkingDistance,
                                estimatedTime = totalTime
                            )
                        )
                    }
                }

                // Also check for nearby stops as transfer points
                val nearbyStops = stopDao.getNearbyStops(
                    potentialTransferStop.latitude,
                    potentialTransferStop.longitude,
                    limit = 5
                ).filter { it.id != potentialTransferStop.id }

                for (nearbyStop in nearbyStops) {
                    val walkDistance = potentialTransferStop.distanceTo(nearbyStop)
                    if (walkDistance > MAX_WALKING_DISTANCE) continue

                    for (toRoute in toRoutes) {
                        if (toRoute.id == fromRoute.id) continue

                        val toRouteStops = routeStopDao.getStopsForRouteDetailed(toRoute.id)
                        val nearbyStopIndex = toRouteStops.indexOfFirst { it.id == nearbyStop.id }
                        val toStopIndex = toRouteStops.indexOfFirst { it.id == toStopId }

                        if (nearbyStopIndex >= 0 && toStopIndex > nearbyStopIndex) {
                            val firstSegment = fromRouteStops.subList(fromStopIndex, i + 1)
                            val secondSegment = toRouteStops.subList(nearbyStopIndex, toStopIndex + 1)

                            val firstDistance = calculateTotalDistance(firstSegment)
                            val secondDistance = calculateTotalDistance(secondSegment)
                            val walkingTime = ((walkDistance / 1000.0) / WALKING_SPEED_KMH * 60).toInt()

                            val totalTime = calculateTravelTime(firstDistance, firstSegment.size) +
                                    TRANSFER_WAIT_TIME +
                                    walkingTime +
                                    calculateTravelTime(secondDistance, secondSegment.size)

                            transferRoutes.add(
                                TransferRoute(
                                    firstRoute = fromRoute,
                                    secondRoute = toRoute,
                                    transferStop = potentialTransferStop,
                                    firstSegmentStops = firstSegment,
                                    secondSegmentStops = secondSegment,
                                    walkingDistance = walkDistance,
                                    estimatedTime = totalTime
                                )
                            )
                        }
                    }
                }
            }
        }

        return transferRoutes.sortedBy { it.estimatedTime }.take(5) // Return top 5 fastest routes
    }

    /**
     * Calculate total distance along a path of stops
     */
    private fun calculateTotalDistance(stops: List<Stop>): Double {
        var totalDistance = 0.0
        for (i in 0 until stops.size - 1) {
            totalDistance += stops[i].distanceTo(stops[i + 1])
        }
        return totalDistance
    }

    /**
     * Calculate estimated travel time
     */
    private fun calculateTravelTime(distanceMeters: Double, stopCount: Int): Int {
        val distanceKm = distanceMeters / 1000.0
        val travelMinutes = (distanceKm / AVERAGE_SPEED_KMH * 60).toInt()
        val waitMinutes = (stopCount - 1) * STOP_WAIT_TIME
        return travelMinutes + waitMinutes
    }
}
