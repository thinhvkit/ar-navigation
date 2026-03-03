import Foundation
import Combine
import CoreLocation
import simd

@MainActor
final class NavigationViewModel: ObservableObject {
    @Published var state = NavigationState()

    let locationManager = LocationManager()
    let arSessionManager = ARSessionManager()
    let arrowRenderer = ArrowRenderer()
    let pathRibbonRenderer = PathRibbonRenderer()
    let chevronRunnerRenderer = ChevronRunnerRenderer()
    private let routeService = RouteService()
    private let converter = CoordinateConverter.shared

    private var cancellables = Set<AnyCancellable>()
    private var routeRequested = false

    /// Set to true to enable auto-walk testing mode (simulates walking along route).
    let autoWalkEnabled = false

    private var walkTimer: Timer?
    private var walkIndex = 0

    private var destLat: Double = 0
    private var destLng: Double = 0

    private let arrowSpacing: Float = 1.5
    private let rerouteThreshold: Float = 20
    private let offRouteWarningThreshold: Float = 12

    /// Minimum GPS accuracy (meters) required before allowing destination selection.
    private let requiredAccuracy: Float = 20

    // Snap-to-route throttling: avoid re-snapping every GPS tick
    private var lastSnapTime: TimeInterval = 0
    private var lastSnapLat: Double = 0
    private var lastSnapLng: Double = 0
    /// Minimum interval between snap computations (seconds).
    private let snapInterval: TimeInterval = 0.3
    /// Minimum movement before re-snapping (meters).
    private let snapMinMovement: Float = 1.5

    func start() {
        arrowRenderer.prepare()
        chevronRunnerRenderer.prepare()

        locationManager.$lastLocation
            .compactMap { $0 }
            .sink { [weak self] loc in
                self?.onLocationUpdate(loc)
            }
            .store(in: &cancellables)

        locationManager.$heading
            .sink { [weak self] heading in
                self?.state.heading = heading
            }
            .store(in: &cancellables)

        // GPS signal loss subscription
        locationManager.$gpsSignalLost
            .removeDuplicates()
            .sink { [weak self] lost in
                self?.onGPSSignalChanged(lost: lost)
            }
            .store(in: &cancellables)

        // Thermal state monitoring
        NotificationCenter.default.publisher(for: ProcessInfo.thermalStateDidChangeNotification)
            .sink { [weak self] _ in
                self?.onThermalStateChanged()
            }
            .store(in: &cancellables)

        locationManager.requestPermission()
    }

    // MARK: - GPS Signal Loss

    private func onGPSSignalChanged(lost: Bool) {
        state.gpsSignalLost = lost
        if lost {
            // Don't reroute or update snap while GPS is lost
            print("[NavVM] GPS signal lost — freezing navigation state")
        } else {
            print("[NavVM] GPS signal restored")
        }
    }

    // MARK: - Thermal State

    private func onThermalStateChanged() {
        let thermal = ProcessInfo.processInfo.thermalState
        state.thermalState = thermal

        switch thermal {
        case .nominal, .fair:
            locationManager.setDesiredAccuracy(kCLLocationAccuracyBest)
        case .serious:
            // Reduce GPS polling precision to save power
            locationManager.setDesiredAccuracy(kCLLocationAccuracyNearestTenMeters)
            print("[NavVM] Thermal serious — reducing GPS accuracy")
        case .critical:
            locationManager.setDesiredAccuracy(kCLLocationAccuracyHundredMeters)
            print("[NavVM] Thermal critical — minimal GPS accuracy")
        @unknown default:
            break
        }
    }

    // MARK: - Location Updates

    private func onLocationUpdate(_ loc: LocationUpdate) {
        state.userLat = loc.lat
        state.userLng = loc.lng
        state.gpsAccuracy = loc.accuracy

        // Wait for reasonable GPS accuracy before setting origin
        if !converter.originSet {
            if loc.accuracy <= requiredAccuracy {
                converter.setOrigin(lat: loc.lat, lng: loc.lng)
                print("[NavVM] Origin set at accuracy ±\(loc.accuracy)m: \(loc.lat), \(loc.lng)")
                state.phase = .selectingDestination
                state.statusMessage = "Select a destination"
            } else {
                state.statusMessage = String(format: "Acquiring GPS (±%.0fm)...", loc.accuracy)
            }
            return
        }

        // Keep improving origin while waiting for destination (not yet navigating)
        if case .selectingDestination = state.phase {
            if loc.accuracy < state.gpsAccuracy || loc.accuracy < 10 {
                converter.setOrigin(lat: loc.lat, lng: loc.lng)
            }
        }

        // Don't process route snapping if GPS signal is stale
        guard !state.gpsSignalLost else { return }

        // Snap to route if available
        guard state.route.count >= 2 else { return }

        // Throttle: only re-snap if enough time or distance has passed
        let now = loc.timestamp > 0 ? loc.timestamp : ProcessInfo.processInfo.systemUptime
        let timeSinceSnap = now - lastSnapTime
        let distSinceSnap = converter.distanceBetween(
            lat1: lastSnapLat, lng1: lastSnapLng,
            lat2: loc.lat, lng2: loc.lng
        )

        guard timeSinceSnap >= snapInterval || distSinceSnap >= snapMinMovement else { return }

        lastSnapTime = now
        lastSnapLat = loc.lat
        lastSnapLng = loc.lng

        let snap = SnapToRoute.snap(
            userLat: loc.lat, userLng: loc.lng,
            route: state.route
        )
        state.currentSegment = snap.segmentIndex
        state.distanceToRoute = snap.distanceToRoute
        state.distanceAlongRoute = snap.distanceAlongRoute

        // Progress tracking
        if state.totalRouteDistance > 0 {
            state.distanceRemaining = max(0, state.totalRouteDistance - snap.distanceAlongRoute)
            state.routeProgress = min(1.0, max(0.0, snap.distanceAlongRoute / state.totalRouteDistance))
        }

        // Turn instruction update
        state.turnInstruction = TurnDetector.nextInstruction(
            instructions: state.turnInstructions,
            distanceAlongRoute: snap.distanceAlongRoute
        )

        // Off-route warning (softer threshold than reroute)
        state.offRouteWarningVisible = snap.distanceToRoute > offRouteWarningThreshold

        // Re-route if off track (but not during GPS loss or thermal-critical)
        if snap.distanceToRoute > rerouteThreshold
            && !routeRequested
            && state.thermalState != .critical {
            print("[NavVM] Off-route by \(snap.distanceToRoute)m, recalculating...")
            routeRequested = true
            state.phase = .recalculating
            state.isRecalculating = true
            state.statusMessage = "Recalculating..."
            fetchRoute(fromLat: loc.lat, fromLng: loc.lng)
        }
    }

    // MARK: - Route Fetching

    private func fetchRoute(fromLat: Double, fromLng: Double) {
        Task {
            do {
                let routeInfo = try await routeService.fetchRoute(
                    originLat: fromLat, originLng: fromLng,
                    destLat: destLat, destLng: destLng
                )
                print("[NavVM] Route received: \(routeInfo.waypoints.count) waypoints")
                applyRoute(routeInfo.waypoints)
                routeRequested = false
            } catch {
                print("[NavVM] Route error: \(error.localizedDescription)")
                state.phase = .error(error.localizedDescription)
                state.statusMessage = "Route error: \(error.localizedDescription)"
                state.isRecalculating = false
                routeRequested = false
            }
        }
    }

    private func applyRoute(_ waypoints: [LatLng]) {
        let worldPositions = waypoints.map { wp in
            converter.gpsToLocal(lat: wp.lat, lng: wp.lng)
        }

        let placements = ArrowMeshResource.computeArrowPlacements(
            worldPositions: worldPositions,
            spacing: arrowSpacing
        )
        arrowRenderer.setRoute(placements: placements)
        pathRibbonRenderer.setRoute(worldPositions: worldPositions)
        chevronRunnerRenderer.setRoute(worldPositions: worldPositions)

        // Compute total route distance
        var totalDist: Float = 0
        for i in 1..<waypoints.count {
            totalDist += converter.distanceBetween(
                lat1: waypoints[i - 1].lat, lng1: waypoints[i - 1].lng,
                lat2: waypoints[i].lat, lng2: waypoints[i].lng
            )
        }
        state.totalRouteDistance = totalDist
        state.distanceRemaining = totalDist
        state.routeProgress = 0
        state.distanceAlongRoute = 0

        // Compute turn instructions
        let instructions = TurnDetector.computeTurnInstructions(route: waypoints)
        state.turnInstructions = instructions
        state.turnInstruction = TurnDetector.nextInstruction(
            instructions: instructions,
            distanceAlongRoute: 0
        )

        print("[NavVM] Route applied: \(waypoints.count) waypoints, \(placements.count) arrows, \(instructions.count) turns, \(Int(totalDist))m total")

        state.route = waypoints
        state.routeWorldPositions = worldPositions
        state.phase = .navigating
        state.isRecalculating = false
        state.offRouteWarningVisible = false
        state.statusMessage = "Navigating — \(Int(totalDist))m"

        // Reset snap throttle so first snap happens immediately
        lastSnapTime = 0
        lastSnapLat = 0
        lastSnapLng = 0

        if autoWalkEnabled {
            startAutoWalk()
        }
    }

    // MARK: - Auto Walk (Testing)

    private func startAutoWalk() {
        walkTimer?.invalidate()
        walkIndex = 0
        walkTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.stepForward()
            }
        }
    }

    private func stepForward() {
        guard walkIndex < state.route.count else {
            walkTimer?.invalidate()
            walkTimer = nil
            state.statusMessage = "Arrived!"
            return
        }

        let wp = state.route[walkIndex]
        state.userLat = wp.lat
        state.userLng = wp.lng

        let snap = SnapToRoute.snap(userLat: wp.lat, userLng: wp.lng, route: state.route)
        state.currentSegment = snap.segmentIndex
        state.distanceAlongRoute = snap.distanceAlongRoute

        // Update progress
        if state.totalRouteDistance > 0 {
            state.distanceRemaining = max(0, state.totalRouteDistance - snap.distanceAlongRoute)
            state.routeProgress = min(1.0, snap.distanceAlongRoute / state.totalRouteDistance)
        }

        // Update turn instruction
        state.turnInstruction = TurnDetector.nextInstruction(
            instructions: state.turnInstructions,
            distanceAlongRoute: snap.distanceAlongRoute
        )

        // Compute heading toward next waypoint
        if walkIndex + 1 < state.route.count {
            let next = state.route[walkIndex + 1]
            let dlat = next.lat - wp.lat
            let dlng = next.lng - wp.lng
            let rad = atan2(dlng, dlat)
            state.heading = rad * 180.0 / .pi
        }

        state.statusMessage = "Walking... \(walkIndex + 1)/\(state.route.count)"
        walkIndex += 1
    }

    // MARK: - Destination

    /// Set a destination from user input and fetch route.
    func setDestination(lat: Double, lng: Double) {
        destLat = lat
        destLng = lng

        // Clear previous route
        arrowRenderer.clearArrows()
        pathRibbonRenderer.cleanup()
        chevronRunnerRenderer.cleanup()
        state.route = []
        state.routeWorldPositions = []
        state.turnInstructions = []
        state.turnInstruction = nil
        state.totalRouteDistance = 0
        state.distanceAlongRoute = 0
        state.distanceRemaining = 0
        state.routeProgress = 0
        state.offRouteWarningVisible = false
        state.isRecalculating = false
        lastSnapTime = 0
        lastSnapLat = 0
        lastSnapLng = 0

        guard state.userLat != 0 else { return }

        // Re-anchor origin to latest GPS for best alignment with road
        converter.setOrigin(lat: state.userLat, lng: state.userLng)
        print("[NavVM] Origin re-anchored at ±\(state.gpsAccuracy)m: \(state.userLat), \(state.userLng)")

        routeRequested = true
        state.phase = .fetchingRoute
        state.statusMessage = "Fetching route..."
        fetchRoute(fromLat: state.userLat, fromLng: state.userLng)
    }

    func cleanup() {
        walkTimer?.invalidate()
        walkTimer = nil
        arrowRenderer.clearArrows()
        pathRibbonRenderer.cleanup()
        chevronRunnerRenderer.cleanup()
        converter.reset()
        cancellables.removeAll()
    }
}
