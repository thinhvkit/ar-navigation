package com.ideals.arnav.navigation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideals.arnav.ar.ArrowMeshFactory

import com.ideals.arnav.ar.ArSessionManager
import com.ideals.arnav.files.GpxKmlFile
import com.ideals.arnav.files.GpxKmlParser
import com.ideals.arnav.files.SelectedFileManager
import com.ideals.arnav.geo.CoordinateConverter
import com.ideals.arnav.location.LocationManager
import com.ideals.arnav.location.LocationUpdate
import com.ideals.arnav.route.GeocodingService
import com.ideals.arnav.route.LatLng
import com.ideals.arnav.route.RouteInfo
import com.ideals.arnav.route.RouteRepository
import com.ideals.arnav.route.SearchResult
import com.ideals.arnav.route.SnapToRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    val locationManager = LocationManager(application)
    val arSessionManager = ArSessionManager()

    private val mapboxToken = getMapboxToken(application)
    private val routeRepository = RouteRepository(mapboxToken)
    val geocodingService = GeocodingService(mapboxToken)

    private var destLat = 0.0
    private var destLng = 0.0
    private var routeRequested = false

    // ARCore heading calibration: bridges ARCore yaw to geographic north
    @Volatile var latestArCoreYaw: Double = 0.0
    @Volatile var arCoreYawReady: Boolean = false
    private var calibrationUpdateCount = 0

    // Precomputed cumulative distance-along-route for each step location
    private var stepDistancesAlongRoute: List<Float> = emptyList()

    // Auto-walk simulation
    var autoWalkEnabled = false
        private set
    private var walkIndex = 0
    private var autoWalkJob: Job? = null

    private fun getMapboxToken(context: android.app.Application): String {
        return try {
            val resourceId = context.resources.getIdentifier(
                "mapbox_access_token", "string", context.packageName
            )
            if (resourceId != 0) context.getString(resourceId) else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun startNavigation() {
        viewModelScope.launch {
            locationManager.locationUpdates().collect { loc ->
                onLocationUpdate(loc)
            }
        }
    }

    private fun onLocationUpdate(locationUpdate: LocationUpdate) {
        if (_state.value.demoMode) return

        val lat = locationUpdate.lat
        val lng = locationUpdate.lng
        val accuracy = locationUpdate.accuracy
        val heading = locationUpdate.heading
        val gpsStale = locationUpdate.gpsStale

        // Update GPS signal lost state
        _state.update { it.copy(gpsSignalLost = gpsStale) }

        // Don't trigger rerouting or tracking updates on stale positions
        if (gpsStale) return

        // Update ARCore heading calibration
        updateCalibration(heading)

        val currentPhase = _state.value.phase

        when (currentPhase) {
            NavigationState.Phase.WAITING_FOR_GPS -> {
                _state.update {
                    it.copy(
                        userLat = lat,
                        userLng = lng,
                        heading = heading,
                        gpsAccuracy = accuracy,
                        statusMessage = if (accuracy <= REQUIRED_ACCURACY)
                            "GPS ready (±%.0fm)".format(accuracy)
                        else
                            "Acquiring GPS (±%.0fm)...".format(accuracy)
                    )
                }
                // Transition to SELECTING_DESTINATION when GPS accuracy is good enough
                if (accuracy <= REQUIRED_ACCURACY) {
                    CoordinateConverter.setOrigin(lat, lng)
                    _state.update {
                        it.copy(
                            phase = NavigationState.Phase.SELECTING_DESTINATION,
                            statusMessage = "GPS ready — set a destination"
                        )
                    }
                    Log.d(TAG, "GPS ready, origin set: $lat, $lng (accuracy: $accuracy)")
                }
            }

            NavigationState.Phase.SELECTING_DESTINATION -> {
                _state.update {
                    it.copy(
                        userLat = lat,
                        userLng = lng,
                        heading = heading,
                        gpsAccuracy = accuracy
                    )
                }
                // Keep improving origin while waiting for destination selection
                if (accuracy < _state.value.gpsAccuracy || !CoordinateConverter.originSet) {
                    CoordinateConverter.setOrigin(lat, lng)
                    Log.d(TAG, "Origin improved: $lat, $lng (accuracy: $accuracy)")
                }
            }

            NavigationState.Phase.NAVIGATING, NavigationState.Phase.RECALCULATING -> {
                val factor = SMOOTHING_FACTOR
                _state.update {
                    it.copy(
                        userLat = lat,
                        userLng = lng,
                        heading = heading,
                        gpsAccuracy = accuracy,
                        // Initialize smoothed coords from first valid fix immediately
                        smoothedLat = if (it.smoothedLat == 0.0) lat else it.smoothedLat * (1.0 - factor) + lat * factor,
                        smoothedLng = if (it.smoothedLng == 0.0) lng else it.smoothedLng * (1.0 - factor) + lng * factor
                    )
                }
                val currentState = _state.value

                // Robust origin initialization: set it on first good fix in any phase
                if (!CoordinateConverter.originSet && accuracy <= REQUIRED_ACCURACY) {
                    CoordinateConverter.setOrigin(lat, lng)
                    Log.d(TAG, "Origin initialized: $lat, $lng (accuracy: $accuracy)")
                }

                // Transitions for WAITING_FOR_GPS (actually this block is only hit if currentPhase matches)
                // But the when condition is currentPhase. So NAVIGATING/RECALCULATING logic goes here:
                updateRouteTracking(lat, lng)

                // Adaptive GPS interval
                val interval = if (
                    locationUpdate.speed > ON_ROUTE_SPEED_THRESHOLD &&
                    currentState.distanceToRoute < ON_ROUTE_DIST_THRESHOLD
                ) {
                    GPS_INTERVAL_ON_ROUTE
                } else {
                    GPS_INTERVAL_OFF_ROUTE
                }
                locationManager.setUpdateInterval(interval)
            }

            else -> {
                _state.update {
                    it.copy(
                        userLat = lat,
                        userLng = lng,
                        heading = heading,
                        gpsAccuracy = accuracy
                    )
                }
            }
        }
    }

    /**
     * Smoothly calibrate offset between ARCore yaw and GPS heading.
     * Uses exponential smoothing with angular wrap handling.
     * Fast adaptation (0.2) for first 10 updates, then slow (0.05).
     */
    private fun updateCalibration(gpsHeading: Double) {
        // Don't calibrate until ARCore render loop has set latestArCoreYaw at least once
        if (!arCoreYawReady) return

        val arcoreYaw = latestArCoreYaw
        val rawCalibration = gpsHeading - arcoreYaw

        val currentState = _state.value
        if (!currentState.calibrationInitialized) {
            // First calibration: snap directly
            _state.update {
                it.copy(
                    calibrationAngle = normalizeAngle(rawCalibration),
                    calibrationInitialized = true
                )
            }
            calibrationUpdateCount = 1
            return
        }

        calibrationUpdateCount++
        val factor = if (calibrationUpdateCount < 10) 0.2 else 0.05
        val currentCalibration = currentState.calibrationAngle
        val diff = angleDiff(rawCalibration, currentCalibration)
        val newCalibration = normalizeAngle(currentCalibration + diff * factor)

        _state.update { it.copy(calibrationAngle = newCalibration) }
    }

    companion object {
        private const val TAG = "NavigationVM"
        private const val ARROW_SPACING = 1.5f
        private const val PATH_SPACING = 1.2f // must match ArScreen's PATH_SPACING
        private const val REROUTE_THRESHOLD = 20f // meters off-route before refetch
        private const val REQUIRED_ACCURACY = 20f // meters — GPS must be this good before navigating
        private const val GPS_INTERVAL_ON_ROUTE = 2000L  // ms — slower polling when on-route, walking
        private const val GPS_INTERVAL_OFF_ROUTE = 1000L // ms — faster polling when off-route or low accuracy
        private const val ON_ROUTE_SPEED_THRESHOLD = 0.5f // m/s
        private const val ON_ROUTE_DIST_THRESHOLD = 10f  // meters
        private const val SMOOTHING_FACTOR = 0.1 // EMA factor for position smoothing

        /** Signed angular difference in [-180, 180) */
        fun angleDiff(a: Double, b: Double): Double {
            var d = (a - b) % 360.0
            if (d > 180.0) d -= 360.0
            if (d <= -180.0) d += 360.0
            return d
        }

        /** Normalize angle to [0, 360) */
        fun normalizeAngle(a: Double): Double {
            var n = a % 360.0
            if (n < 0) n += 360.0
            return n
        }
    }

    private fun updateRouteTracking(lat: Double, lng: Double) {
        val currentState = _state.value
        val route = currentState.route
        if (route.size < 2) return

        val snap = SnapToRoute.snap(lat, lng, route)
        val distAlong = snap.distanceAlongRoute
        val totalDist = currentState.totalRouteDistance
        val remaining = (totalDist - distAlong).coerceAtLeast(0f)

        // Determine current step index: last step whose cumulative distance <= user's distanceAlongRoute (+10m tolerance)
        var stepIdx = currentState.currentStepIndex
        if (stepDistancesAlongRoute.isNotEmpty()) {
            stepIdx = 0
            for (i in stepDistancesAlongRoute.indices) {
                if (stepDistancesAlongRoute[i] <= distAlong + 10f) {
                    stepIdx = i
                }
            }
        }

        // Distance to next turn
        val distToNext = if (stepIdx + 1 < stepDistancesAlongRoute.size) {
            (stepDistancesAlongRoute[stepIdx + 1] - distAlong).coerceAtLeast(0f)
        } else {
            remaining
        }

        // ETA: sum duration of remaining steps
        val steps = currentState.turnSteps
        val eta = if (stepIdx < steps.size) {
            // Fraction of current step remaining
            val currentStepDist = steps[stepIdx].distance
            val currentStepDistAlong = stepDistancesAlongRoute.getOrElse(stepIdx) { 0f }
            val progressInStep = if (currentStepDist > 0) {
                ((distAlong - currentStepDistAlong) / currentStepDist).toDouble().coerceIn(0.0, 1.0)
            } else 0.0
            val currentStepEta = steps[stepIdx].duration * (1.0 - progressInStep)
            currentStepEta + steps.drop(stepIdx + 1).sumOf { it.duration }
        } else 0.0

        _state.update {
            it.copy(
                currentSegment = snap.segmentIndex,
                distanceToRoute = snap.distanceToRoute,
                distanceAlongRoute = distAlong,
                distanceRemaining = remaining,
                currentStepIndex = stepIdx,
                distanceToNextTurn = distToNext,
                etaSeconds = eta
            )
        }

        // Re-route if user deviates too far
        if (snap.distanceToRoute > REROUTE_THRESHOLD && !routeRequested) {
            Log.d(TAG, "User off-route by ${snap.distanceToRoute}m, recalculating...")
            routeRequested = true
            _state.update {
                it.copy(
                    phase = NavigationState.Phase.RECALCULATING,
                    isOffRoute = true,
                    statusMessage = "Recalculating..."
                )
            }
            fetchRoute(lat, lng)
        }
    }

    /**
     * Demo mode: inject a mock polyline in local coords so all AR visuals
     * (chevrons, path, glow, particles, distance-based scale, leader pulse)
     * render immediately without GPS or a fetched route.
     */
    fun enableDemoMode() {
        CoordinateConverter.setOrigin(0.0, 0.0)

        // Curved path in local coords: -Z is forward, X is right
        val worldPositions = listOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1.0f, 0f, -4f),
            floatArrayOf(0.5f, 0f, -8f),
            floatArrayOf(-1.0f, 0f, -13f),
            floatArrayOf(-0.5f, 0f, -18f),
            floatArrayOf(1.5f, 0f, -23f),
            floatArrayOf(0f, 0f, -28f),
            floatArrayOf(-1.0f, 0f, -33f),
        )

        val chevronSegments = ArrowMeshFactory.precomputeSegments(worldPositions)
        val pathPlacements = ArrowMeshFactory.computeArrowPlacements(worldPositions, PATH_SPACING)

        _state.update {
            it.copy(
                phase = NavigationState.Phase.NAVIGATING,
                demoMode = true,
                routeWorldPositions = worldPositions,
                cachedChevronSegments = chevronSegments,
                cachedPathPlacements = pathPlacements,
                smoothedLat = 0.0,
                smoothedLng = 0.0,
                statusMessage = "Demo mode"
            )
        }
    }

    /**
     * Set a specific destination (called from UI when user picks a place).
     */
    fun setDestination(lat: Double, lng: Double) {
        destLat = lat
        destLng = lng

        // Stop any existing auto-walk
        autoWalkJob?.cancel()
        autoWalkJob = null
        walkIndex = 0


        // Re-anchor origin to latest GPS position
        val userLat = _state.value.userLat
        val userLng = _state.value.userLng
        if (userLat != 0.0) {
            CoordinateConverter.setOrigin(userLat, userLng)
            Log.d(TAG, "Origin re-anchored: $userLat, $userLng")
        }

        routeRequested = true
        _state.update {
            it.copy(
                phase = NavigationState.Phase.FETCHING_ROUTE,
                demoMode = false,
                route = emptyList(),
                routeWorldPositions = emptyList(),
                statusMessage = "Fetching route..."
            )
        }
        fetchRoute(userLat, userLng)
    }

    /**
     * Load and navigate using a trail file (GPX/KML).
     * Bypasses Mapbox API; routes directly to applyRoute().
     */
    fun setRouteFromFile(file: GpxKmlFile, parsedRoute: GpxKmlParser.ParsedRoute) {
        if (parsedRoute.waypoints.isEmpty()) {
            _state.update {
                it.copy(
                    phase = NavigationState.Phase.ERROR,
                    statusMessage = "Route has no waypoints"
                )
            }
            return
        }

        destLat = parsedRoute.waypoints.last().lat
        destLng = parsedRoute.waypoints.last().lng

        // Stop any existing auto-walk
        autoWalkJob?.cancel()
        autoWalkJob = null
        walkIndex = 0

        // Re-anchor origin to latest GPS position
        val userLat = _state.value.userLat
        val userLng = _state.value.userLng
        if (userLat != 0.0) {
            CoordinateConverter.setOrigin(userLat, userLng)
            Log.d(TAG, "Origin re-anchored for file route: $userLat, $userLng")
        }

        // Create RouteInfo from parsed route
        val routeInfo = RouteInfo(
            waypoints = parsedRoute.waypoints,
            distanceMeters = parsedRoute.distanceMeters.toDouble(),
            durationSeconds = 0.0,  // Not available from file
            steps = emptyList()     // Turn-by-turn not available from file
        )

        _state.update {
            it.copy(
                phase = NavigationState.Phase.FETCHING_ROUTE,
                demoMode = false,
                route = emptyList(),
                routeWorldPositions = emptyList(),
                statusMessage = "Loading trail: ${file.name}"
            )
        }

        // Apply route directly (no API call)
        applyRoute(routeInfo)
    }

    /**
     * Reset navigation state: clear route and return to WAITING_FOR_GPS.
     * Used when exiting trail-based navigation.
     */
    fun resetNavigation() {
        autoWalkJob?.cancel()
        autoWalkJob = null
        walkIndex = 0
        destLat = 0.0
        destLng = 0.0

        _state.update {
            it.copy(
                phase = NavigationState.Phase.WAITING_FOR_GPS,
                route = emptyList(),
                routeWorldPositions = emptyList(),
                turnSteps = emptyList(),
                distanceAlongRoute = 0f,
                distanceRemaining = 0f,
                distanceToNextTurn = 0f,
                currentStepIndex = 0,
                statusMessage = "Ready to navigate"
            )
        }
    }

    /**
     * Load persisted file on app startup.
     */
    private fun loadPersistedFile() {
        val selectedId = SelectedFileManager(getApplication()).getSelectedFileId()
        if (selectedId == null) {
            Log.d(TAG, "No persisted file selected")
            return
        }

        viewModelScope.launch {
            try {
                val file = com.ideals.arnav.files.FileRepository(getApplication())
                    .loadFiles()
                    .find { it.id == selectedId }

                if (file == null) {
                    Log.w(TAG, "Persisted file not found: $selectedId")
                    return@launch
                }

                val content = File(file.storedPath).readText()
                val parsed = when (file.type) {
                    GpxKmlFile.FileType.GPX -> GpxKmlParser.parseGpx(content)
                    GpxKmlFile.FileType.KML -> GpxKmlParser.parseKml(content)
                }

                if (parsed != null) {
                    Log.d(TAG, "Loaded persisted file: ${file.name} with ${parsed.waypoints.size} waypoints")
                    setRouteFromFile(file, parsed)
                } else {
                    Log.w(TAG, "Failed to parse persisted file: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading persisted file", e)
            }
        }
    }

    private fun fetchRoute(fromLat: Double, fromLng: Double) {
        viewModelScope.launch {
            val result = routeRepository.fetchRoute(fromLat, fromLng, destLat, destLng)
            result.fold(
                onSuccess = { routeInfo ->
                    Log.d(TAG, "Route received: ${routeInfo.waypoints.size} waypoints, ${routeInfo.steps.size} steps")
                    applyRoute(routeInfo)
                    routeRequested = false
                },
                onFailure = { error ->
                    Log.e(TAG, "Route error: ${error.message}")
                    _state.update {
                        it.copy(
                            phase = NavigationState.Phase.ERROR,
                            statusMessage = "Route error: ${error.message}"
                        )
                    }
                    routeRequested = false
                }
            )
        }
    }

    private fun applyRoute(routeInfo: RouteInfo) {
        val waypoints = routeInfo.waypoints
        if (waypoints.isEmpty()) return

        // Critical: Ensure coordinate origin is set before computing world positions.
        // If not set by GPS yet, fallback to first waypoint.
        if (!CoordinateConverter.originSet) {
            val anchorLat = if (_state.value.userLat != 0.0) _state.value.userLat else waypoints[0].lat
            val anchorLng = if (_state.value.userLat != 0.0) _state.value.userLng else waypoints[0].lng
            CoordinateConverter.setOrigin(anchorLat, anchorLng)
            Log.d(TAG, "Origin force-initialized in applyRoute: $anchorLat, $anchorLng")
        }

        // Convert GPS waypoints to local meter positions
        val worldPositions = waypoints.map { wp ->
            CoordinateConverter.gpsToLocal(wp.lat, wp.lng)
        }

        // Pre-compute chevron segment data (reused per-frame by ArScreen)
        val chevronSegments = ArrowMeshFactory.precomputeSegments(worldPositions)

        // Pre-compute static path placements (blue ground path) — cached to avoid per-frame recomputation
        val pathPlacements = ArrowMeshFactory.computeArrowPlacements(worldPositions, PATH_SPACING)

        // Precompute cumulative distance-along-route for each step location
        stepDistancesAlongRoute = routeInfo.steps.map { step ->
            val snap = SnapToRoute.snap(step.location.lat, step.location.lng, waypoints)
            snap.distanceAlongRoute
        }

        val totalDist = routeInfo.distanceMeters.toFloat()

        Log.d(TAG, "Route applied: ${waypoints.size} waypoints, ${pathPlacements.size} path chunks, ${routeInfo.steps.size} steps")

        // Initialize smoothed position so arrows render immediately
        val currentUserLat = _state.value.userLat
        val currentUserLng = _state.value.userLng

        _state.update {
            it.copy(
                phase = NavigationState.Phase.NAVIGATING,
                route = waypoints,
                routeWorldPositions = worldPositions,
                cachedPathPlacements = pathPlacements,
                cachedChevronSegments = chevronSegments,
                currentSegment = 0,
                turnSteps = routeInfo.steps,
                currentStepIndex = 0,
                totalRouteDistance = totalDist,
                distanceAlongRoute = 0f,
                distanceRemaining = totalDist,
                distanceToNextTurn = if (stepDistancesAlongRoute.size > 1) stepDistancesAlongRoute[1] else totalDist,
                etaSeconds = routeInfo.durationSeconds,
                isOffRoute = false,
                smoothedLat = if (it.smoothedLat == 0.0) currentUserLat else it.smoothedLat,
                smoothedLng = if (it.smoothedLng == 0.0) currentUserLng else it.smoothedLng,
                statusMessage = "Navigating"
            )
        }

        // Always auto-walk along the route so AR visuals render smoothly
        // (real GPS is too noisy indoors to drive AR placement)
        startAutoWalk(waypoints)
    }

    private fun startAutoWalk(waypoints: List<LatLng>) {
        autoWalkJob?.cancel()
        walkIndex = 0
        autoWalkJob = viewModelScope.launch {
            while (walkIndex < waypoints.size) {
                val wp = waypoints[walkIndex]

                // Compute heading from route direction
                val heading = if (walkIndex + 1 < waypoints.size) {
                    val next = waypoints[walkIndex + 1]
                    val dLng = next.lng - wp.lng
                    val dLat = next.lat - wp.lat
                    val bearing = Math.toDegrees(kotlin.math.atan2(dLng, dLat))
                    normalizeAngle(bearing)
                } else if (walkIndex > 0) {
                    val prev = waypoints[walkIndex - 1]
                    val dLng = wp.lng - prev.lng
                    val dLat = wp.lat - prev.lat
                    val bearing = Math.toDegrees(kotlin.math.atan2(dLng, dLat))
                    normalizeAngle(bearing)
                } else {
                    _state.value.heading
                }

                _state.update {
                    it.copy(
                        userLat = wp.lat,
                        userLng = wp.lng,
                        smoothedLat = wp.lat,
                        smoothedLng = wp.lng,
                        heading = heading,
                        demoMode = true, // suppress real GPS fighting auto-walk
                        statusMessage = "Walking... ${walkIndex + 1}/${waypoints.size}"
                    )
                }

                // Update calibration so ARCore heading aligns with route direction
                if (arCoreYawReady) {
                    val rawCalibration = heading - latestArCoreYaw
                    _state.update {
                        it.copy(
                            calibrationAngle = normalizeAngle(rawCalibration),
                            calibrationInitialized = true
                        )
                    }
                }

                updateRouteTracking(wp.lat, wp.lng)
                walkIndex++
                delay(500L)
            }
            _state.update {
                it.copy(statusMessage = "Arrived!", demoMode = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoWalkJob?.cancel()

        CoordinateConverter.reset()
    }
}
