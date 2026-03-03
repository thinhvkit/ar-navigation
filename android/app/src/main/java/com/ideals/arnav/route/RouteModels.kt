package com.ideals.arnav.route

data class LatLng(
    val lat: Double,
    val lng: Double
)

data class TurnStep(
    val maneuverType: String,       // "turn", "depart", "arrive", "continue", etc.
    val maneuverModifier: String?,  // "left", "right", "slight left", "uturn", etc.
    val instruction: String,        // "Turn left onto Main St"
    val distance: Double,           // meters for this step
    val duration: Double,           // seconds for this step
    val location: LatLng            // where maneuver occurs
)

data class RouteInfo(
    val waypoints: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<TurnStep> = emptyList()
)

/** Result of snapping user position to route. */
data class SnapResult(
    val snappedLat: Double,
    val snappedLng: Double,
    val segmentIndex: Int,
    val distanceToRoute: Float,
    val distanceAlongRoute: Float
)
