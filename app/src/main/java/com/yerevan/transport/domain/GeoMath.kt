package com.yerevan.transport.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    data class ProjectionResult(
        val distanceToShapeMeters: Double,
        val projectedDistanceMeters: Double
    )

    fun haversineMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val lat1 = Math.toRadians(aLat)
        val lat2 = Math.toRadians(bLat)
        val h = sin(dLat / 2.0).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2.0).pow(2)
        return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    fun webMercatorToWgs84(x: Double, y: Double): GeoPoint {
        val lng = x / 20037508.34 * 180.0
        var lat = y / 20037508.34 * 180.0
        lat = 180.0 / PI * (2.0 * atan(kotlin.math.exp(lat * PI / 180.0)) - PI / 2.0)
        return GeoPoint(lat = lat, lng = lng)
    }

    fun projectPointToParts(point: GeoPoint, parts: List<List<GeoPoint>>): ProjectionResult {
        var bestDistance = Double.MAX_VALUE
        var bestProjected = Double.MAX_VALUE
        var consumedLength = 0.0
        parts.forEachIndexed { partIndex, part ->
            if (part.size < 2) return@forEachIndexed
            val projection = projectPointToPath(point, part)
            val candidateProjected = consumedLength + projection.projectedDistanceMeters
            if (projection.distanceToShapeMeters < bestDistance) {
                bestDistance = projection.distanceToShapeMeters
                bestProjected = candidateProjected + (partIndex * 1_000_000.0)
            }
            consumedLength += pathLengthMeters(part)
        }
        return ProjectionResult(
            distanceToShapeMeters = bestDistance,
            projectedDistanceMeters = bestProjected
        )
    }

    fun pathLengthMeters(path: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 0 until path.lastIndex) {
            total += haversineMeters(path[i].lat, path[i].lng, path[i + 1].lat, path[i + 1].lng)
        }
        return total
    }

    private fun projectPointToPath(point: GeoPoint, path: List<GeoPoint>): ProjectionResult {
        var bestDistance = Double.MAX_VALUE
        var bestProjectedDistance = 0.0
        var walked = 0.0
        for (i in 0 until path.lastIndex) {
            val a = path[i]
            val b = path[i + 1]
            val segmentLength = haversineMeters(a.lat, a.lng, b.lat, b.lng)
            val projection = pointToSegmentProjection(point, a, b)
            if (projection.first < bestDistance) {
                bestDistance = projection.first
                bestProjectedDistance = walked + (projection.second * segmentLength)
            }
            walked += segmentLength
        }
        return ProjectionResult(
            distanceToShapeMeters = bestDistance,
            projectedDistanceMeters = bestProjectedDistance
        )
    }

    private fun pointToSegmentProjection(
        point: GeoPoint,
        start: GeoPoint,
        end: GeoPoint
    ): Pair<Double, Double> {
        val refLat = (start.lat + end.lat + point.lat) / 3.0
        val p = projectToPlane(point, refLat)
        val s = projectToPlane(start, refLat)
        val e = projectToPlane(end, refLat)

        val sx = s.first
        val sy = s.second
        val ex = e.first
        val ey = e.second
        val px = p.first
        val py = p.second

        val vx = ex - sx
        val vy = ey - sy
        val wx = px - sx
        val wy = py - sy
        val vv = vx * vx + vy * vy
        if (vv <= 1e-9) {
            val distance = sqrt((px - sx).pow(2) + (py - sy).pow(2))
            return distance to 0.0
        }

        val t = max(0.0, min(1.0, (wx * vx + wy * vy) / vv))
        val cx = sx + t * vx
        val cy = sy + t * vy
        val distance = sqrt((px - cx).pow(2) + (py - cy).pow(2))
        return distance to t
    }

    private fun projectToPlane(point: GeoPoint, refLat: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(point.lat)
        val lngRad = Math.toRadians(point.lng)
        val refLatRad = Math.toRadians(refLat)
        val x = EARTH_RADIUS_METERS * lngRad * cos(refLatRad)
        val y = EARTH_RADIUS_METERS * latRad
        return x to y
    }
}
