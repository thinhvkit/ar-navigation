package com.ideals.arnav.route

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object SnapToRoute {

    /**
     * Snap user position to the nearest point on the route polyline.
     * Uses projection onto line segments in GPS coordinate space.
     */
    fun snap(userLat: Double, userLng: Double, route: List<LatLng>): SnapResult {
        if (route.size < 2) {
            return SnapResult(
                snappedLat = userLat,
                snappedLng = userLng,
                segmentIndex = 0,
                distanceToRoute = 0f,
                distanceAlongRoute = 0f
            )
        }

        var bestDist = Double.MAX_VALUE
        var bestLat = userLat
        var bestLng = userLng
        var bestSegment = 0
        var distAlong = 0.0

        var cumulativeDistance = 0.0

        for (i in 0 until route.size - 1) {
            val a = route[i]
            val b = route[i + 1]

            // Project point onto segment [a, b]
            val projected = projectOntoSegment(
                userLat, userLng,
                a.lat, a.lng,
                b.lat, b.lng
            )

            val dx = userLat - projected[0]
            val dy = userLng - projected[1]
            val dist = dx * dx + dy * dy

            if (dist < bestDist) {
                bestDist = dist
                bestLat = projected[0]
                bestLng = projected[1]
                bestSegment = i

                // Distance along route to the snapped point
                val segStartToSnap = sqrt(
                    (projected[0] - a.lat) * (projected[0] - a.lat) +
                    (projected[1] - a.lng) * (projected[1] - a.lng)
                )
                val segLen = sqrt(
                    (b.lat - a.lat) * (b.lat - a.lat) +
                    (b.lng - a.lng) * (b.lng - a.lng)
                )
                val fraction = if (segLen > 0) segStartToSnap / segLen else 0.0
                distAlong = cumulativeDistance + fraction * segLengthMeters(a, b)
            }

            cumulativeDistance += segLengthMeters(a, b)
        }

        // Convert GPS-space distance to approximate meters
        val distToRoute = com.ideals.arnav.geo.CoordinateConverter.distanceBetween(
            userLat, userLng, bestLat, bestLng
        )

        return SnapResult(
            snappedLat = bestLat,
            snappedLng = bestLng,
            segmentIndex = bestSegment,
            distanceToRoute = distToRoute,
            distanceAlongRoute = distAlong.toFloat()
        )
    }

    /**
     * Project point P onto line segment AB, clamped to [0, 1].
     * Returns [lat, lng] of the projected point.
     */
    private fun projectOntoSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): DoubleArray {
        val abLat = bLat - aLat
        val abLng = bLng - aLng
        val apLat = pLat - aLat
        val apLng = pLng - aLng

        val ab2 = abLat * abLat + abLng * abLng
        if (ab2 < 1e-20) {
            return doubleArrayOf(aLat, aLng)
        }

        val t = max(0.0, min(1.0, (apLat * abLat + apLng * abLng) / ab2))
        return doubleArrayOf(aLat + t * abLat, aLng + t * abLng)
    }

    private fun segLengthMeters(a: LatLng, b: LatLng): Double {
        return com.ideals.arnav.geo.CoordinateConverter.distanceBetween(
            a.lat, a.lng, b.lat, b.lng
        ).toDouble()
    }
}
