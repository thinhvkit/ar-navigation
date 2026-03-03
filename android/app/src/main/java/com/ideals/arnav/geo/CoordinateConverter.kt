package com.ideals.arnav.geo

import kotlin.math.cos

/**
 * Converts GPS coordinates (lat/lng) to local meter-based coordinates.
 * Uses flat-earth approximation which is accurate within AR range (~1km).
 *
 * Coordinate system (matches ARCore): right-handed, Y-up, -Z forward.
 * - x = east/west offset in meters
 * - y = always 0 (ground plane)
 * - z = negated north/south offset in meters (north = -z)
 */
object CoordinateConverter {

    private const val EARTH_RADIUS = 6378137.0

    private var originLat = 0.0
    private var originLng = 0.0
    private var cosOriginLat = 1.0
    var originSet = false
        private set

    fun setOrigin(lat: Double, lng: Double) {
        originLat = lat
        originLng = lng
        cosOriginLat = cos(Math.toRadians(lat))
        originSet = true
    }

    /**
     * Convert GPS to local XZ coordinates in meters.
     * Returns [x, y, z] where y is always 0.
     */
    fun gpsToLocal(lat: Double, lng: Double): FloatArray {
        if (!originSet) return floatArrayOf(0f, 0f, 0f)

        val dlat = Math.toRadians(lat - originLat)
        val dlng = Math.toRadians(lng - originLng)

        val x = (dlng * EARTH_RADIUS * cosOriginLat).toFloat()
        val z = -(dlat * EARTH_RADIUS).toFloat()

        return floatArrayOf(x, 0f, z)
    }

    /**
     * Distance between two GPS points in meters (flat-earth approximation).
     */
    fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val dlat = Math.toRadians(lat2 - lat1)
        val dlng = Math.toRadians(lng2 - lng1)
        val cosLat = cos(Math.toRadians((lat1 + lat2) / 2.0))

        val dx = dlng * EARTH_RADIUS * cosLat
        val dz = dlat * EARTH_RADIUS

        return Math.sqrt(dx * dx + dz * dz).toFloat()
    }

    fun reset() {
        originSet = false
        originLat = 0.0
        originLng = 0.0
        cosOriginLat = 1.0
    }
}
