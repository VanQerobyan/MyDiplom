package com.yerevan.transport.data.repository

import com.yerevan.transport.data.local.dao.TransportRouteDao
import com.yerevan.transport.data.local.dao.TransportStopDao
import com.yerevan.transport.data.local.entity.*
import com.yerevan.transport.util.RouteCalculator
import com.yerevan.transport.util.RouteResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that provides transport data to the rest of the application.
 * Acts as the single source of truth following MVVM architecture.
 */
@Singleton
class TransportRepository @Inject constructor(
    private val stopDao: TransportStopDao,
    private val routeDao: TransportRouteDao,
    private val routeCalculator: RouteCalculator
) {
    // ===== Stops =====

    fun getAllStops(): Flow<List<TransportStop>> = stopDao.getAllStops()

    suspend fun getAllStopsList(): List<TransportStop> = stopDao.getAllStopsList()

    suspend fun getStopById(id: Long): TransportStop? = stopDao.getStopById(id)

    fun searchStops(query: String): Flow<List<TransportStop>> = stopDao.searchStops(query)

    suspend fun searchStopsList(query: String): List<TransportStop> = stopDao.searchStopsList(query)

    fun getStopsByType(type: StopType): Flow<List<TransportStop>> = stopDao.getStopsByType(type)

    fun getStopsByCommunity(community: String): Flow<List<TransportStop>> =
        stopDao.getStopsByCommunity(community)

    fun getAllCommunities(): Flow<List<String>> = stopDao.getAllCommunities()

    suspend fun getStopWithRoutes(stopId: Long): StopWithRoutes? =
        stopDao.getStopWithRoutes(stopId)

    suspend fun getStopCount(): Int = stopDao.getStopCount()

    suspend fun getStopsInBounds(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<TransportStop> = stopDao.getStopsInBounds(minLat, maxLat, minLon, maxLon)

    // ===== Routes =====

    fun getAllRoutes(): Flow<List<TransportRoute>> = routeDao.getAllRoutes()

    suspend fun getAllRoutesList(): List<TransportRoute> = routeDao.getAllRoutesList()

    suspend fun getRouteById(id: Long): TransportRoute? = routeDao.getRouteById(id)

    fun getRoutesByType(type: StopType): Flow<List<TransportRoute>> = routeDao.getRoutesByType(type)

    suspend fun getRouteWithStops(routeId: Long): RouteWithStops? =
        routeDao.getRouteWithStops(routeId)

    suspend fun getRoutesForStop(stopId: Long): List<TransportRoute> =
        routeDao.getRoutesForStop(stopId)

    suspend fun getOrderedStopsForRoute(routeId: Long): List<TransportStop> =
        routeDao.getOrderedStopsForRoute(routeId)

    suspend fun getRouteCount(): Int = routeDao.getRouteCount()

    // ===== Route Calculation =====

    suspend fun calculateRoutes(fromStopId: Long, toStopId: Long): List<RouteResult> =
        routeCalculator.calculateRoutes(fromStopId, toStopId)

    suspend fun findNearestStops(
        lat: Double, lon: Double, limit: Int = 5
    ): List<Pair<TransportStop, Double>> =
        routeCalculator.findNearestStops(lat, lon, limit)
}
