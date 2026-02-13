package com.yerevan.transport.domain

class RoutePlanner {

    data class PlannerStop(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double
    )

    data class PlannerRoute(
        val id: String,
        val name: String,
        val mode: String
    )

    data class Membership(
        val routeId: String,
        val stopId: String
    )

    fun findOptions(
        startStopId: String,
        endStopId: String,
        stops: Map<String, PlannerStop>,
        routes: Map<String, PlannerRoute>,
        memberships: List<Membership>
    ): List<RouteOption> {
        val startStop = stops[startStopId] ?: return emptyList()
        val endStop = stops[endStopId] ?: return emptyList()
        if (startStopId == endStopId) return emptyList()

        val routeIdsByStop = memberships.groupBy { it.stopId }.mapValues { entry ->
            entry.value.map { it.routeId }.toSet()
        }
        val stopIdsByRoute = memberships.groupBy { it.routeId }.mapValues { entry ->
            entry.value.map { it.stopId }.toSet()
        }

        val startRoutes = routeIdsByStop[startStopId].orEmpty()
        val endRoutes = routeIdsByStop[endStopId].orEmpty()
        if (startRoutes.isEmpty() || endRoutes.isEmpty()) return emptyList()

        val directOptions = buildDirectOptions(
            startStop = startStop,
            endStop = endStop,
            routeIds = startRoutes.intersect(endRoutes),
            routes = routes
        )
        if (directOptions.isNotEmpty()) return directOptions.sortedBy { it.estimatedMinutes }

        val transferOptions = mutableListOf<RouteOption.Transfer>()
        startRoutes.forEach { firstRouteId ->
            endRoutes.forEach { secondRouteId ->
                if (firstRouteId == secondRouteId) return@forEach
                val firstRoute = routes[firstRouteId] ?: return@forEach
                val secondRoute = routes[secondRouteId] ?: return@forEach
                val firstRouteStops = stopIdsByRoute[firstRouteId].orEmpty()
                val secondRouteStops = stopIdsByRoute[secondRouteId].orEmpty()
                if (firstRouteStops.isEmpty() || secondRouteStops.isEmpty()) return@forEach

                val commonStops = firstRouteStops.intersect(secondRouteStops)
                if (commonStops.isNotEmpty()) {
                    commonStops.take(8).forEach { transferStopId ->
                        val transferStop = stops[transferStopId] ?: return@forEach
                        val leg1 = estimateRideDistanceMeters(startStop, transferStop, firstRoute.mode)
                        val leg2 = estimateRideDistanceMeters(transferStop, endStop, secondRoute.mode)
                        val total = leg1 + leg2
                        val totalMinutes = estimateRideMinutes(leg1, firstRoute.mode) +
                            estimateRideMinutes(leg2, secondRoute.mode) +
                            transferPenaltyMinutes() +
                            (headwayMinutes(firstRoute.mode) + headwayMinutes(secondRoute.mode)) / 2.0

                        transferOptions += RouteOption.Transfer(
                            firstRouteId = firstRoute.id,
                            firstRouteName = firstRoute.name,
                            secondRouteId = secondRoute.id,
                            secondRouteName = secondRoute.name,
                            transferFromStopId = transferStop.id,
                            transferToStopId = transferStop.id,
                            walkingDistanceMeters = 0.0,
                            headwayMinutes = ((headwayMinutes(firstRoute.mode) + headwayMinutes(secondRoute.mode)) / 2.0).toInt(),
                            totalDistanceMeters = total,
                            estimatedMinutes = totalMinutes
                        )
                    }
                } else {
                    val nearest = findNearestTransferPair(
                        firstRouteStops = firstRouteStops,
                        secondRouteStops = secondRouteStops,
                        stops = stops
                    ) ?: return@forEach
                    if (nearest.walkDistanceMeters > 1_200.0) return@forEach

                    val transferFrom = stops[nearest.firstStopId] ?: return@forEach
                    val transferTo = stops[nearest.secondStopId] ?: return@forEach
                    val leg1 = estimateRideDistanceMeters(startStop, transferFrom, firstRoute.mode)
                    val leg2 = estimateRideDistanceMeters(transferTo, endStop, secondRoute.mode)
                    val total = leg1 + nearest.walkDistanceMeters + leg2
                    val totalMinutes = estimateRideMinutes(leg1, firstRoute.mode) +
                        estimateWalkMinutes(nearest.walkDistanceMeters) +
                        estimateRideMinutes(leg2, secondRoute.mode) +
                        transferPenaltyMinutes() +
                        (headwayMinutes(firstRoute.mode) + headwayMinutes(secondRoute.mode)) / 2.0

                    transferOptions += RouteOption.Transfer(
                        firstRouteId = firstRoute.id,
                        firstRouteName = firstRoute.name,
                        secondRouteId = secondRoute.id,
                        secondRouteName = secondRoute.name,
                        transferFromStopId = transferFrom.id,
                        transferToStopId = transferTo.id,
                        walkingDistanceMeters = nearest.walkDistanceMeters,
                        headwayMinutes = ((headwayMinutes(firstRoute.mode) + headwayMinutes(secondRoute.mode)) / 2.0).toInt(),
                        totalDistanceMeters = total,
                        estimatedMinutes = totalMinutes
                    )
                }
            }
        }

        return transferOptions
            .distinctBy { listOf(it.firstRouteId, it.secondRouteId, it.transferFromStopId, it.transferToStopId).joinToString("|") }
            .sortedBy { it.estimatedMinutes }
            .take(16)
    }

    private fun buildDirectOptions(
        startStop: PlannerStop,
        endStop: PlannerStop,
        routeIds: Set<String>,
        routes: Map<String, PlannerRoute>
    ): List<RouteOption.Direct> {
        return routeIds.mapNotNull { routeId ->
            val route = routes[routeId] ?: return@mapNotNull null
            val distance = estimateRideDistanceMeters(startStop, endStop, route.mode)
            val estimated = estimateRideMinutes(distance, route.mode) + headwayMinutes(route.mode) / 2.0
            RouteOption.Direct(
                routeId = route.id,
                routeName = route.name,
                mode = route.mode,
                headwayMinutes = headwayMinutes(route.mode),
                totalDistanceMeters = distance,
                estimatedMinutes = estimated
            )
        }
    }

    private fun estimateRideDistanceMeters(a: PlannerStop, b: PlannerStop, mode: String): Double {
        val base = GeoMath.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        val multiplier = when (mode) {
            "METRO" -> 1.18
            "MONORAIL" -> 1.22
            "RAIL" -> 1.16
            else -> 1.30
        }
        return base * multiplier
    }

    private fun estimateRideMinutes(distanceMeters: Double, mode: String): Double {
        val speedKmh = when (mode) {
            "METRO" -> 32.0
            "MONORAIL" -> 26.0
            "RAIL" -> 40.0
            else -> 24.0
        }
        val metersPerMinute = speedKmh * 1000.0 / 60.0
        return distanceMeters / metersPerMinute
    }

    private fun estimateWalkMinutes(distanceMeters: Double): Double {
        val walkingMetersPerMinute = 4.8 * 1000.0 / 60.0
        return distanceMeters / walkingMetersPerMinute
    }

    private fun headwayMinutes(mode: String): Int {
        return when (mode) {
            "METRO" -> 8
            "MONORAIL" -> 10
            "RAIL" -> 18
            else -> 12
        }
    }

    private fun transferPenaltyMinutes(): Double = 4.0

    private fun findNearestTransferPair(
        firstRouteStops: Set<String>,
        secondRouteStops: Set<String>,
        stops: Map<String, PlannerStop>
    ): TransferPair? {
        var nearest: TransferPair? = null
        firstRouteStops.forEach { firstId ->
            val firstStop = stops[firstId] ?: return@forEach
            secondRouteStops.forEach { secondId ->
                val secondStop = stops[secondId] ?: return@forEach
                val distance = GeoMath.haversineMeters(
                    firstStop.lat,
                    firstStop.lng,
                    secondStop.lat,
                    secondStop.lng
                )
                if (nearest == null || distance < nearest!!.walkDistanceMeters) {
                    nearest = TransferPair(firstId, secondId, distance)
                }
            }
        }
        return nearest
    }

    private data class TransferPair(
        val firstStopId: String,
        val secondStopId: String,
        val walkDistanceMeters: Double
    )
}
