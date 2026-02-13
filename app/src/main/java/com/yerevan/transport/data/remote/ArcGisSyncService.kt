package com.yerevan.transport.data.remote

import com.yerevan.transport.data.local.entity.RouteEntity
import com.yerevan.transport.data.local.entity.RouteShapePointEntity
import com.yerevan.transport.data.local.entity.RouteStopEntity
import com.yerevan.transport.data.local.entity.StopEntity
import com.yerevan.transport.data.local.entity.SyncMetadataEntity
import com.yerevan.transport.domain.GeoMath
import com.yerevan.transport.domain.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

data class SyncPayload(
    val stops: List<StopEntity>,
    val routes: List<RouteEntity>,
    val routeShapes: List<RouteShapePointEntity>,
    val routeStops: List<RouteStopEntity>,
    val metadata: SyncMetadataEntity
)

class ArcGisSyncService(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        private const val PORTAL_BASE = "https://gis.yerevan.am/portal"
        private const val EXPERIENCE_ITEM_ID = "13c109e913644a8d877db51465ace1f2"
        private const val PAGE_SIZE = 2000
    }

    suspend fun downloadTransportNetwork(): SyncPayload = withContext(Dispatchers.IO) {
        val experienceData = fetchJson(
            "$PORTAL_BASE/sharing/rest/content/items/$EXPERIENCE_ITEM_ID/data?f=pjson"
        )
        val webMapIds = extractWebMapIds(experienceData)
        require(webMapIds.isNotEmpty()) {
            "Could not resolve any web map IDs from Experience Builder item $EXPERIENCE_ITEM_ID"
        }

        val layerRefs = linkedSetOf<LayerRef>()
        webMapIds.forEach { mapId ->
            val webMapData = fetchJson("$PORTAL_BASE/sharing/rest/content/items/$mapId/data?f=pjson")
            collectLayerRefs(webMapData["operationalLayers"], null, layerRefs)
        }

        val stopEntities = mutableListOf<StopEntity>()
        val routeEntities = mutableListOf<RouteEntity>()
        val routeShapePoints = mutableListOf<RouteShapePointEntity>()
        val routeGeometryById = mutableMapOf<String, List<List<GeoPoint>>>()

        layerRefs.forEach { layer ->
            val layerMeta = fetchLayerMetadata(layer.url) ?: return@forEach
            when {
                shouldTreatAsStopLayer(layer, layerMeta) -> {
                    val features = queryAllFeatures(layer.url)
                    features.forEach { feature ->
                        val attrs = feature["attributes"]?.jsonObject ?: JsonObject(emptyMap())
                        val geometry = feature["geometry"]?.jsonObject ?: return@forEach
                        val stopPoint = parsePointGeometry(geometry) ?: return@forEach
                        val objectId = extractObjectId(attrs, layerMeta.objectIdField) ?: return@forEach
                        val stopName = extractStopName(attrs) ?: "${layer.title} #$objectId"
                        val stopId = buildStableId(layer.url, objectId)
                        stopEntities += StopEntity(
                            id = stopId,
                            name = stopName,
                            lat = stopPoint.lat,
                            lng = stopPoint.lng,
                            sourceLayerTitle = layer.title,
                            sourceLayerUrl = layer.url,
                            rawAttributesJson = attrs.toString()
                        )
                    }
                }

                shouldTreatAsRouteLayer(layer, layerMeta) -> {
                    val features = queryAllFeatures(layer.url)
                    features.forEach { feature ->
                        val attrs = feature["attributes"]?.jsonObject ?: JsonObject(emptyMap())
                        val geometry = feature["geometry"]?.jsonObject ?: return@forEach
                        val objectId = extractObjectId(attrs, layerMeta.objectIdField) ?: return@forEach
                        val routeId = buildStableId(layer.url, objectId)
                        val routeName = extractRouteName(attrs) ?: "${layer.title} #$objectId"
                        val mode = inferMode(layer.title, layer.parentTitle)
                        val parts = parseRouteParts(
                            geometry = geometry,
                            geometryType = layerMeta.geometryType
                        )
                        if (parts.isEmpty()) return@forEach

                        routeEntities += RouteEntity(
                            id = routeId,
                            name = routeName,
                            mode = mode,
                            geometryType = layerMeta.geometryType,
                            sourceLayerTitle = layer.title,
                            sourceLayerUrl = layer.url,
                            rawAttributesJson = attrs.toString()
                        )
                        routeGeometryById[routeId] = parts
                        parts.forEachIndexed { partIndex, part ->
                            part.forEachIndexed { pointIndex, point ->
                                routeShapePoints += RouteShapePointEntity(
                                    routeId = routeId,
                                    partIndex = partIndex,
                                    pointIndex = pointIndex,
                                    lat = point.lat,
                                    lng = point.lng,
                                    geometryRole = if (layerMeta.geometryType == "esriGeometryPolygon") {
                                        "RING"
                                    } else {
                                        "PATH"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        val dedupedStops = stopEntities.distinctBy { it.id }
        val dedupedRoutes = routeEntities.distinctBy { it.id }
        val routeStops = buildRouteStopMemberships(
            stops = dedupedStops,
            routes = dedupedRoutes,
            routeGeometryById = routeGeometryById
        )

        SyncPayload(
            stops = dedupedStops.sortedBy { it.name },
            routes = dedupedRoutes.sortedBy { it.name },
            routeShapes = routeShapePoints.distinctBy { Triple(it.routeId, it.partIndex, it.pointIndex) },
            routeStops = routeStops,
            metadata = SyncMetadataEntity(
                experienceItemId = EXPERIENCE_ITEM_ID,
                syncedAtEpochMs = System.currentTimeMillis(),
                stopCount = dedupedStops.size,
                routeCount = dedupedRoutes.size
            )
        )
    }

    private fun buildRouteStopMemberships(
        stops: List<StopEntity>,
        routes: List<RouteEntity>,
        routeGeometryById: Map<String, List<List<GeoPoint>>>
    ): List<RouteStopEntity> {
        val memberships = mutableListOf<RouteStopEntity>()
        routes.forEach { route ->
            val parts = routeGeometryById[route.id].orEmpty()
            if (parts.isEmpty()) return@forEach
            val thresholdMeters = when (route.mode) {
                "METRO" -> 220.0
                "MONORAIL" -> 260.0
                "RAIL" -> 280.0
                else -> 220.0
            }
            stops.forEach { stop ->
                val projection = GeoMath.projectPointToParts(
                    point = GeoPoint(stop.lat, stop.lng),
                    parts = parts
                )
                if (projection.distanceToShapeMeters <= thresholdMeters) {
                    memberships += RouteStopEntity(
                        routeId = route.id,
                        stopId = stop.id,
                        distanceToRouteMeters = projection.distanceToShapeMeters,
                        projectedDistanceMeters = projection.projectedDistanceMeters
                    )
                }
            }
        }
        return memberships
    }

    private fun shouldTreatAsStopLayer(layer: LayerRef, meta: LayerMetadata): Boolean {
        if (meta.geometryType != "esriGeometryPoint") return false
        val text = "${layer.parentTitle.orEmpty()} ${layer.title}".lowercase()
        val keywordMatch = listOf(
            "կայարան",
            "մետրո",
            "միառելս",
            "station",
            "stop",
            "transport"
        ).any(text::contains)
        return keywordMatch || meta.fieldNames.any { it.lowercase().contains("կայան") }
    }

    private fun shouldTreatAsRouteLayer(layer: LayerRef, meta: LayerMetadata): Boolean {
        if (meta.geometryType !in setOf("esriGeometryPolyline", "esriGeometryPolygon")) return false
        val text = "${layer.parentTitle.orEmpty()} ${layer.title}".lowercase()
        val includeByKeyword = listOf(
            "մետրո",
            "միառելս",
            "երկաթ",
            "գիծ",
            "line",
            "rail",
            "route"
        ).any(text::contains)
        val excluded = listOf("սահման", "administrative", "մայթեր", "փողոցներ", "ճանապարհներ").any(text::contains)
        return includeByKeyword && !excluded
    }

    private fun inferMode(layerTitle: String, parentTitle: String?): String {
        val text = "$layerTitle ${parentTitle.orEmpty()}".lowercase()
        return when {
            "մետրո" in text -> "METRO"
            "միառելս" in text -> "MONORAIL"
            "երկաթ" in text -> "RAIL"
            else -> "TRANSPORT"
        }
    }

    private fun extractStopName(attributes: JsonObject): String? {
        return findAttributeString(
            attributes,
            keys = listOf("մետրո_կայան", "station_name", "stop_name", "անվանում", "name", "layer")
        )
    }

    private fun extractRouteName(attributes: JsonObject): String? {
        return findAttributeString(
            attributes,
            keys = listOf("անվանում", "layer", "name", "route_name", "line_name")
        )
    }

    private fun parseRouteParts(geometry: JsonObject, geometryType: String): List<List<GeoPoint>> {
        val sourceArray = when (geometryType) {
            "esriGeometryPolyline" -> geometry["paths"]?.jsonArray
            "esriGeometryPolygon" -> geometry["rings"]?.jsonArray
            else -> null
        } ?: return emptyList()

        return sourceArray.mapNotNull { partElement ->
            val part = partElement as? JsonArray ?: return@mapNotNull null
            val points = part.mapNotNull { vertex ->
                val coords = vertex as? JsonArray ?: return@mapNotNull null
                if (coords.size < 2) return@mapNotNull null
                val x = coords[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val y = coords[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                if (kotlin.math.abs(x) > 180 || kotlin.math.abs(y) > 90) {
                    GeoMath.webMercatorToWgs84(x, y)
                } else {
                    GeoPoint(lat = y, lng = x)
                }
            }
            points.takeIf { it.size >= 2 }
        }
    }

    private fun parsePointGeometry(geometry: JsonObject): GeoPoint? {
        val x = geometry["x"]?.jsonPrimitive?.doubleOrNull ?: return null
        val y = geometry["y"]?.jsonPrimitive?.doubleOrNull ?: return null
        return if (kotlin.math.abs(x) > 180 || kotlin.math.abs(y) > 90) {
            GeoMath.webMercatorToWgs84(x, y)
        } else {
            GeoPoint(lat = y, lng = x)
        }
    }

    private fun extractObjectId(attributes: JsonObject, objectIdField: String?): String? {
        val preferred = objectIdField?.let { attributes[it] }?.jsonPrimitive?.contentOrNull
        if (!preferred.isNullOrBlank()) return preferred
        return listOf("objectid", "OBJECTID", "fid", "id").firstNotNullOfOrNull { key ->
            attributes[key]?.jsonPrimitive?.contentOrNull
        }
    }

    private fun fetchLayerMetadata(layerUrl: String): LayerMetadata? {
        val meta = fetchJson("${layerUrl.trimEnd('/')}?f=pjson")
        val geometryType = meta["geometryType"]?.jsonPrimitive?.contentOrNull ?: return null
        val objectIdField = meta["objectIdField"]?.jsonPrimitive?.contentOrNull
        val fieldNames = meta["fields"]?.jsonArray
            ?.mapNotNull { fieldEl ->
                fieldEl.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            }
            .orEmpty()
        return LayerMetadata(
            geometryType = geometryType,
            objectIdField = objectIdField,
            fieldNames = fieldNames
        )
    }

    private fun queryAllFeatures(layerUrl: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var offset = 0
        while (true) {
            val response = fetchJson(
                "${layerUrl.trimEnd('/')}/query?" +
                    "where=1%3D1&outFields=*&returnGeometry=true&outSR=4326&f=pjson" +
                    "&resultOffset=$offset&resultRecordCount=$PAGE_SIZE"
            )
            if (response["error"] != null && offset > 0) break
            val features = response["features"]?.jsonArray
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            if (features.isEmpty()) break
            all += features
            val exceeded = response["exceededTransferLimit"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!exceeded && features.size < PAGE_SIZE) break
            offset += features.size
            if (offset > 200_000) break
        }
        return all
    }

    private fun extractWebMapIds(experienceData: JsonObject): Set<String> {
        val mapIds = linkedSetOf<String>()
        val dataSources = experienceData["dataSources"]?.jsonObject.orEmpty()
        dataSources.values.forEach { source ->
            val itemId = source.jsonObject["itemId"]?.jsonPrimitive?.contentOrNull
            if (!itemId.isNullOrBlank()) mapIds += itemId
        }
        return mapIds
    }

    private fun collectLayerRefs(
        element: JsonElement?,
        parentTitle: String?,
        target: MutableSet<LayerRef>
    ) {
        val layersArray = element as? JsonArray ?: return
        layersArray.forEach { rawLayer ->
            val layerObject = rawLayer.jsonObject
            val title = layerObject["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val url = layerObject["url"]?.jsonPrimitive?.contentOrNull
            if (!url.isNullOrBlank()) {
                target += LayerRef(
                    title = title.ifBlank { "Layer" },
                    url = url,
                    parentTitle = parentTitle
                )
            }
            val nextParent = if (title.isBlank()) parentTitle else title
            collectLayerRefs(layerObject["layers"], nextParent, target)
        }
    }

    private fun buildStableId(layerUrl: String, objectId: String): String {
        val pathToken = layerUrl
            .substringAfter("/services/", missingDelimiterValue = layerUrl)
            .replace("/", "_")
            .replace(Regex("[^A-Za-z0-9_:-]"), "_")
        return "$pathToken:$objectId"
    }

    private fun findAttributeString(attributes: JsonObject, keys: List<String>): String? {
        keys.forEach { key ->
            val value = attributes[key]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrBlank() && value.lowercase() != "null") return value.trim()
        }
        val lowered = attributes.mapKeys { it.key.lowercase() }
        keys.forEach { key ->
            val value = lowered[key.lowercase()]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrBlank() && value.lowercase() != "null") return value.trim()
        }
        return null
    }

    private fun fetchJson(url: String): JsonObject {
        val request = Request.Builder()
            .url(URI(url).toASCIIString())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for $url" }
            val body = response.body?.string().orEmpty()
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private data class LayerRef(
        val title: String,
        val url: String,
        val parentTitle: String?
    )

    private data class LayerMetadata(
        val geometryType: String,
        val objectIdField: String?,
        val fieldNames: List<String>
    )
}
