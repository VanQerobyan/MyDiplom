package com.yerevan.transport.data.repository

import androidx.room.withTransaction
import com.yerevan.transport.data.local.AppDatabase
import com.yerevan.transport.data.local.entity.RouteEntity
import com.yerevan.transport.data.local.entity.StopEntity
import com.yerevan.transport.data.local.entity.SyncMetadataEntity
import com.yerevan.transport.data.remote.ArcGisSyncService
import com.yerevan.transport.domain.GeoPoint
import com.yerevan.transport.domain.MapRouteShape
import com.yerevan.transport.domain.RouteOption
import com.yerevan.transport.domain.RoutePlanner

data class MapData(
    val stops: List<StopEntity>,
    val routes: List<MapRouteShape>
)

class TransportRepository(
    private val database: AppDatabase,
    private val syncService: ArcGisSyncService,
    private val planner: RoutePlanner
) {

    suspend fun ensureSynced(force: Boolean = false): SyncMetadataEntity {
        val existing = database.syncMetadataDao().get()
        if (!force && existing != null) return existing

        val payload = syncService.downloadTransportNetwork()
        database.withTransaction {
            database.routeStopDao().clear()
            database.routeShapePointDao().clear()
            database.routeDao().clear()
            database.stopDao().clear()
            database.syncMetadataDao().clear()

            database.stopDao().insertAll(payload.stops)
            database.routeDao().insertAll(payload.routes)
            database.routeShapePointDao().insertAll(payload.routeShapes)
            database.routeStopDao().insertAll(payload.routeStops)
            database.syncMetadataDao().upsert(payload.metadata)
        }
        return payload.metadata
    }

    suspend fun searchStops(query: String, limit: Int = 12): List<StopEntity> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()
        return database.stopDao().search("%$cleanQuery%", limit)
    }

    suspend fun loadMapData(): MapData {
        val routes = database.routeDao().getAll()
        val points = database.routeShapePointDao().getAll()
        val groupedByRoute = points.groupBy { it.routeId }
        val routeShapes = routes.mapNotNull { route ->
            val shapePoints = groupedByRoute[route.id].orEmpty()
            if (shapePoints.isEmpty()) return@mapNotNull null
            val parts = shapePoints
                .groupBy { it.partIndex }
                .toSortedMap()
                .values
                .map { part ->
                    part.sortedBy { it.pointIndex }.map { GeoPoint(it.lat, it.lng) }
                }
            MapRouteShape(
                routeId = route.id,
                routeName = route.name,
                mode = route.mode,
                geometryType = route.geometryType,
                parts = parts
            )
        }
        val stops = database.stopDao().getAll()
        return MapData(stops = stops, routes = routeShapes)
    }

    suspend fun getStopById(id: String): StopEntity? = database.stopDao().getById(id)

    suspend fun findRouteOptions(startStopId: String, endStopId: String): List<RouteOption> {
        val stops = database.stopDao().getAll()
        val routes = database.routeDao().getAll()
        val memberships = database.routeStopDao().getAll()

        val stopMap = stops.associate { stop ->
            stop.id to RoutePlanner.PlannerStop(
                id = stop.id,
                name = stop.name,
                lat = stop.lat,
                lng = stop.lng
            )
        }
        val routeMap = routes.associate { route ->
            route.id to RoutePlanner.PlannerRoute(
                id = route.id,
                name = route.name,
                mode = route.mode
            )
        }
        val plannerMemberships = memberships.map {
            RoutePlanner.Membership(routeId = it.routeId, stopId = it.stopId)
        }
        return planner.findOptions(
            startStopId = startStopId,
            endStopId = endStopId,
            stops = stopMap,
            routes = routeMap,
            memberships = plannerMemberships
        )
    }

    suspend fun routeById(routeId: String): RouteEntity? = database.routeDao().getById(routeId)
}
