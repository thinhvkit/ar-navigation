package com.ideals.arnav.navigation

import com.ideals.arnav.ar.ArrowMeshFactory
import com.ideals.arnav.route.LatLng
import com.ideals.arnav.route.TurnStep

data class NavigationState(
    val phase: Phase = Phase.WAITING_FOR_GPS,
    val userLat: Double = 0.0,
    val userLng: Double = 0.0,
    val smoothedLat: Double = 0.0,
    val smoothedLng: Double = 0.0,
    val heading: Double = 0.0,
    val gpsAccuracy: Float = 0f,
    val route: List<LatLng> = emptyList(),
    val routeWorldPositions: List<FloatArray> = emptyList(),
    val cachedPathPlacements: List<ArrowMeshFactory.ArrowPlacement> = emptyList(),
    val cachedChevronSegments: ArrowMeshFactory.RouteSegmentData? = null,
    val currentSegment: Int = 0,
    val distanceToRoute: Float = 0f,
    val isTracking: Boolean = false,
    val statusMessage: String = "Waiting for GPS...",
    // Turn-by-turn navigation fields
    val turnSteps: List<TurnStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val distanceToNextTurn: Float = 0f,
    val totalRouteDistance: Float = 0f,
    val distanceAlongRoute: Float = 0f,
    val distanceRemaining: Float = 0f,
    val etaSeconds: Double = 0.0,
    val isOffRoute: Boolean = false,
    val gpsSignalLost: Boolean = false,
    // ARCore heading calibration
    val calibrationAngle: Double = 0.0,
    val calibrationInitialized: Boolean = false,
    val demoMode: Boolean = false
) {
    enum class Phase {
        WAITING_FOR_GPS,
        SELECTING_DESTINATION,
        FETCHING_ROUTE,
        NAVIGATING,
        RECALCULATING,
        ERROR
    }
}
