package com.yerevan.transport.data.repository

import com.yerevan.transport.data.local.TransportDatabase
import com.yerevan.transport.data.local.entity.RouteEntity
import com.yerevan.transport.data.local.entity.RouteStopEntity
import com.yerevan.transport.data.local.entity.StopEntity
import com.yerevan.transport.data.remote.GisDataFetcher
import com.yerevan.transport.domain.model.RouteOption
import com.yerevan.transport.domain.model.TransportStop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransportRepository @Inject constructor(
    private val database: TransportDatabase,
    private val gisDataFetcher: GisDataFetcher
) {
    private val stopDao get() = database.stopDao()
    private val routeDao get() = database.routeDao()
    private val routeStopDao get() = database.routeStopDao()

    fun getAllStops(): Flow<List<TransportStop>> =
        stopDao.getAllStops().map { it.map { e -> e.toDomain() } }

    suspend fun searchStops(query: String): List<TransportStop> =
        stopDao.searchStops(query).map { it.toDomain() }

    suspend fun getStopById(id: String): TransportStop? =
        stopDao.getStopById(id)?.toDomain()

    suspend fun getStopByName(name: String): TransportStop? =
        stopDao.getStopByName(name)?.toDomain()

    suspend fun isDatabasePopulated(): Boolean =
        stopDao.getStopCount() > 0

    suspend fun syncDataFromGis(): Result<String> {
        return try {
            val result = gisDataFetcher.fetchAndParseTransportData()
            stopDao.deleteAll()
            routeDao.deleteAll()
            routeStopDao.deleteAll()
            stopDao.insertAll(result.stops)
            routeDao.insertAll(result.routes)
            routeStopDao.insertAll(result.routeStops)
            Result.success("Loaded ${result.stops.size} stops, ${result.routes.size} routes from ${result.source}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Route calculation algorithm:
     * 1. Find direct routes: routes that serve both start and end stops
     * 2. If no direct route: find transfer options
     *    - Get all routes from start stop
     *    - Get all routes from end stop
     *    - Find common stops between these routes (transfer points)
     *    - For each transfer point, calculate distance and estimate time
     *    - Return options sorted by total segments (prefer 1 transfer over 2)
     */
    suspend fun calculateRoutes(fromStopId: String, toStopId: String): List<RouteOption> {
        val fromStop = stopDao.getStopById(fromStopId) ?: return emptyList()
        val toStop = stopDao.getStopById(toStopId) ?: return emptyList()

        val directRoutes = findDirectRoutes(fromStopId, toStopId)
        if (directRoutes.isNotEmpty()) {
            return directRoutes
        }

        return findTransferRoutes(fromStop, toStop)
    }

    private suspend fun findDirectRoutes(fromId: String, toId: String): List<RouteOption> {
        val fromRoutes = routeStopDao.getRouteIdsForStop(fromId).toSet()
        val toRoutes = routeStopDao.getRouteIdsForStop(toId).toSet()
        val commonRoutes = fromRoutes intersect toRoutes

        val options = mutableListOf<RouteOption>()
        for (routeId in commonRoutes) {
            val fromSeq = routeStopDao.getRouteStops(routeId).indexOfFirst { it.stopId == fromId }
            val toSeq = routeStopDao.getRouteStops(routeId).indexOfFirst { it.stopId == toId }
            if (fromSeq >= 0 && toSeq >= 0 && fromSeq < toSeq) {
                val route = routeDao.getRouteById(routeId)!!
                val stopIds = routeStopDao.getStopIdsForRoute(routeId)
                val stops = stopDao.getStopsByIds(stopIds.subList(fromSeq, toSeq + 1))
                val distance = calculatePathDistance(stops)
                options.add(RouteOption(
                    segments = listOf(RouteOption.Segment(
                        routeId = routeId,
                        routeNumber = route.number,
                        transportType = route.transportType,
                        fromStopId = fromId,
                        toStopId = toId,
                        stops = stops.map { it.toDomain() },
                        estimatedDistanceMeters = distance,
                        estimatedMinutes = (distance / 400).toInt().coerceAtLeast(1)
                    )),
                    totalDistanceMeters = distance,
                    totalEstimatedMinutes = (distance / 400).toInt().coerceAtLeast(1),
                    transferCount = 0
                ))
            }
        }
        return options.sortedBy { it.totalEstimatedMinutes }
    }

    private suspend fun findTransferRoutes(fromStop: StopEntity, toStop: StopEntity): List<RouteOption> {
        val fromRouteIds = routeStopDao.getRouteIdsForStop(fromStop.id)
        val toRouteIds = routeStopDao.getRouteIdsForStop(toStop.id)

        val options = mutableListOf<RouteOption>()

        for (route1Id in fromRouteIds) {
            val route1Stops = routeStopDao.getStopIdsForRoute(route1Id)
            val fromSeq = route1Stops.indexOf(fromStop.id)
            if (fromSeq < 0) continue

            for (route2Id in toRouteIds) {
                if (route1Id == route2Id) continue
                val route2Stops = routeStopDao.getStopIdsForRoute(route2Id)
                val toSeq = route2Stops.indexOf(toStop.id)
                if (toSeq < 0) continue

                val route1StopSet = route1Stops.subList(fromSeq, route1Stops.size).toSet()
                val route2StopSet = route2Stops.subList(0, toSeq + 1).toSet()
                val transferStops = route1StopSet intersect route2StopSet

                for (transferStopId in transferStops) {
                    val transferStop = stopDao.getStopById(transferStopId) ?: continue
                    val seg1Stops = stopDao.getStopsByIds(route1Stops.subList(fromSeq, route1Stops.indexOf(transferStopId) + 1))
                    val seg2Stops = stopDao.getStopsByIds(route2Stops.subList(route2Stops.indexOf(transferStopId), toSeq + 1))

                    val route1 = routeDao.getRouteById(route1Id)!!
                    val route2 = routeDao.getRouteById(route2Id)!!
                    val dist1 = calculatePathDistance(seg1Stops)
                    val dist2 = calculatePathDistance(seg2Stops)
                    val totalDist = dist1 + dist2

                    options.add(RouteOption(
                        segments = listOf(
                            RouteOption.Segment(route1Id, route1.number, route1.transportType,
                                fromStop.id, transferStopId, seg1Stops.map { it.toDomain() }, dist1, (dist1 / 400).toInt().coerceAtLeast(1)),
                            RouteOption.Segment(route2Id, route2.number, route2.transportType,
                                transferStopId, toStop.id, seg2Stops.map { it.toDomain() }, dist2, (dist2 / 400).toInt().coerceAtLeast(1))
                        ),
                        totalDistanceMeters = totalDist,
                        totalEstimatedMinutes = (totalDist / 400).toInt().coerceAtLeast(2),
                        transferCount = 1,
                        transferStop = transferStop.toDomain()
                    ))
                }
            }
        }
        return options.sortedBy { it.totalEstimatedMinutes }.take(5)
    }

    private fun calculatePathDistance(stops: List<StopEntity>): Double {
        if (stops.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until stops.size - 1) {
            total += haversineDistance(
                stops[i].latitude, stops[i].longitude,
                stops[i + 1].latitude, stops[i + 1].longitude
            )
        }
        return total
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    suspend fun getStopsForMap(): List<TransportStop> =
        stopDao.getAllStopsOnce().map { it.toDomain() }

    suspend fun getRoutePath(routeId: String): List<TransportStop> {
        val stopIds = routeStopDao.getStopIdsForRoute(routeId)
        return stopDao.getStopsByIds(stopIds).map { it.toDomain() }
    }
}
