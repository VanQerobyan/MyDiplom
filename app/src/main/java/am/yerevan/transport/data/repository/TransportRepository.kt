package am.yerevan.transport.data.repository

import androidx.lifecycle.LiveData
import am.yerevan.transport.data.database.RouteDao
import am.yerevan.transport.data.database.RouteStopDao
import am.yerevan.transport.data.database.StopDao
import am.yerevan.transport.data.model.Route
import am.yerevan.transport.data.model.RouteStop
import am.yerevan.transport.data.model.Stop
import am.yerevan.transport.utils.RouteCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for accessing transport data
 */
class TransportRepository(
    private val stopDao: StopDao,
    private val routeDao: RouteDao,
    private val routeStopDao: RouteStopDao
) {
    val allStops: LiveData<List<Stop>> = stopDao.getAllStops()
    val allRoutes: LiveData<List<Route>> = routeDao.getAllRoutes()

    // Stop operations
    suspend fun searchStops(query: String): List<Stop> = withContext(Dispatchers.IO) {
        stopDao.searchStops(query)
    }

    suspend fun getStopById(stopId: Long): Stop? = withContext(Dispatchers.IO) {
        stopDao.getStopById(stopId)
    }

    suspend fun getNearbyStops(latitude: Double, longitude: Double, limit: Int = 10): List<Stop> = 
        withContext(Dispatchers.IO) {
            stopDao.getNearbyStops(latitude, longitude, limit)
        }

    // Route operations
    suspend fun getRouteById(routeId: Long): Route? = withContext(Dispatchers.IO) {
        routeDao.getRouteById(routeId)
    }

    suspend fun getRoutesForStop(stopId: Long): List<Route> = withContext(Dispatchers.IO) {
        routeDao.getRoutesForStop(stopId)
    }

    suspend fun getCommonRoutes(stopId1: Long, stopId2: Long): List<Route> = 
        withContext(Dispatchers.IO) {
            routeDao.getCommonRoutes(stopId1, stopId2)
        }

    // RouteStop operations
    suspend fun getStopsForRoute(routeId: Long): List<Stop> = withContext(Dispatchers.IO) {
        routeStopDao.getStopsForRouteDetailed(routeId)
    }

    suspend fun getStopsForRouteDirection(routeId: Long, direction: Int): List<Stop> = 
        withContext(Dispatchers.IO) {
            routeStopDao.getStopsForRouteDirection(routeId, direction)
        }

    /**
     * Find routes between two stops
     */
    suspend fun findRoutes(fromStopId: Long, toStopId: Long): RouteSearchResult = 
        withContext(Dispatchers.IO) {
            val calculator = RouteCalculator(stopDao, routeDao, routeStopDao)
            calculator.findRoutes(fromStopId, toStopId)
        }

    /**
     * Get database statistics
     */
    suspend fun getDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
        DatabaseStats(
            stopCount = stopDao.getStopCount(),
            routeCount = routeDao.getRouteCount(),
            routeStopCount = routeStopDao.getRouteStopCount()
        )
    }
}

/**
 * Result of route search
 */
data class RouteSearchResult(
    val directRoutes: List<DirectRoute> = emptyList(),
    val transferRoutes: List<TransferRoute> = emptyList(),
    val hasResults: Boolean = false
)

/**
 * Direct route between two stops
 */
data class DirectRoute(
    val route: Route,
    val stops: List<Stop>,
    val estimatedTime: Int // in minutes
)

/**
 * Route with one transfer
 */
data class TransferRoute(
    val firstRoute: Route,
    val secondRoute: Route,
    val transferStop: Stop,
    val firstSegmentStops: List<Stop>,
    val secondSegmentStops: List<Stop>,
    val walkingDistance: Double, // in meters
    val estimatedTime: Int // in minutes
)

/**
 * Database statistics
 */
data class DatabaseStats(
    val stopCount: Int,
    val routeCount: Int,
    val routeStopCount: Int
)
