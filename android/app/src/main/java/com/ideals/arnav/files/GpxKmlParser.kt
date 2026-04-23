package com.ideals.arnav.files

import android.util.Xml
import com.ideals.arnav.geo.CoordinateConverter
import com.ideals.arnav.route.LatLng
import org.xmlpull.v1.XmlPullParser

object GpxKmlParser {

    data class ParsedRoute(
        val waypoints: List<LatLng>,
        val name: String,
        val description: String? = null,
        val distanceMeters: Float = 0f,
        val elevationGainMeters: Float = 0f,
        val elevationLossMeters: Float = 0f,
        val minElevationMeters: Float = 0f,
        val maxElevationMeters: Float = 0f,
        val elevationsMeters: List<Float> = emptyList()
    )

    /**
     * Parse GPX file content to extract waypoints.
     * Looks for <trkpt> elements with lat/lon attributes within <trkseg>.
     * Also extracts elevation (<ele>) if available.
     */
    fun parseGpx(xmlContent: String): ParsedRoute? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(xmlContent.byteInputStream(), "UTF-8")

            var name = "GPX Track"
            var description: String? = null
            val waypoints = mutableListOf<LatLng>()
            val elevations = mutableListOf<Float>()

            var eventType = parser.eventType
            var currentElevation: Float? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "name" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    name = parser.text
                                }
                            }
                            "desc" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    description = parser.text
                                }
                            }
                            "trkpt" -> {
                                val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    waypoints.add(LatLng(lat, lon))
                                    currentElevation = null
                                }
                            }
                            "ele" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    currentElevation = parser.text.toFloatOrNull()
                                    if (currentElevation != null) {
                                        elevations.add(currentElevation!!)
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
                eventType = parser.next()
            }

            if (waypoints.isEmpty()) return null

            val distanceMeters = computeDistance(waypoints)
            val elevStats = computeElevationStats(elevations)
            ParsedRoute(
                waypoints = waypoints,
                name = name,
                description = description,
                distanceMeters = distanceMeters,
                elevationGainMeters = elevStats.gain,
                elevationLossMeters = elevStats.loss,
                minElevationMeters = elevStats.min,
                maxElevationMeters = elevStats.max,
                elevationsMeters = elevations
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse KML file content to extract waypoints.
     * Handles <LineString> coordinates (lon,lat[,alt] space-separated).
     */
    fun parseKml(xmlContent: String): ParsedRoute? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlContent.byteInputStream(), "UTF-8")

            var name = "KML Route"
            var description: String? = null
            val waypoints = mutableListOf<LatLng>()
            val elevations = mutableListOf<Float>()

            var eventType = parser.eventType
            var inCoordinates = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "name" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    name = parser.text
                                }
                            }
                            "description" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    description = parser.text
                                }
                            }
                            "coordinates" -> {
                                inCoordinates = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inCoordinates) {
                            // Parse coordinates: "lon,lat[,alt] lon,lat[,alt] ..."
                            val coordText = parser.text.trim()
                            coordText.split(Regex("\\s+")).forEach { coordPair ->
                                val parts = coordPair.split(",")
                                if (parts.size >= 2) {
                                    val lon = parts[0].toDoubleOrNull()
                                    val lat = parts[1].toDoubleOrNull()
                                    if (lat != null && lon != null) {
                                        waypoints.add(LatLng(lat, lon))
                                        // Extract elevation if available (3rd coordinate)
                                        if (parts.size >= 3) {
                                            val elev = parts[2].toFloatOrNull()
                                            if (elev != null) {
                                                elevations.add(elev)
                                            }
                                        }
                                    }
                                }
                            }
                            inCoordinates = false
                        }
                    }
                    else -> {}
                }
                eventType = parser.next()
            }

            if (waypoints.isEmpty()) return null

            val distanceMeters = computeDistance(waypoints)
            val elevStats = computeElevationStats(elevations)
            ParsedRoute(
                waypoints = waypoints,
                name = name,
                description = description,
                distanceMeters = distanceMeters,
                elevationGainMeters = elevStats.gain,
                elevationLossMeters = elevStats.loss,
                minElevationMeters = elevStats.min,
                maxElevationMeters = elevStats.max,
                elevationsMeters = elevations
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute total route distance using flat-earth approximation.
     */
    private fun computeDistance(waypoints: List<LatLng>): Float {
        if (waypoints.size < 2) return 0f
        var distance = 0f
        for (i in 0 until waypoints.size - 1) {
            distance += CoordinateConverter.distanceBetween(
                waypoints[i].lat, waypoints[i].lng,
                waypoints[i + 1].lat, waypoints[i + 1].lng
            )
        }
        return distance
    }

    /**
     * Compute elevation statistics: gain, loss, min, max.
     * Returns (gainMeters, lossMeters, minMeters, maxMeters)
     */
    private fun computeElevationStats(elevations: List<Float>): Quad {
        if (elevations.isEmpty()) {
            return Quad(0f, 0f, 0f, 0f)
        }

        var gain = 0f
        var loss = 0f
        var minElev = elevations[0]
        var maxElev = elevations[0]

        for (i in 1 until elevations.size) {
            val prevElev = elevations[i - 1]
            val currentElev = elevations[i]
            val diff = currentElev - prevElev

            if (diff > 0) {
                gain += diff
            } else {
                loss -= diff
            }

            minElev = minOf(minElev, currentElev)
            maxElev = maxOf(maxElev, currentElev)
        }

        return Quad(gain, loss, minElev, maxElev)
    }

    data class Quad(val gain: Float, val loss: Float, val min: Float, val max: Float)
}
