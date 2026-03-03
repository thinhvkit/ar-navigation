import Foundation
import CoreLocation
import Combine

struct LocationUpdate {
    let lat: Double
    let lng: Double
    let altitude: Double
    let accuracy: Float
    let speed: Float
    let course: Float
    let timestamp: TimeInterval
}

final class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var lastLocation: LocationUpdate?
    @Published var heading: Double = 0  // degrees from true north, clockwise
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var gpsSignalLost = false

    private let clManager = CLLocationManager()
    private let latFilter = KalmanFilter()
    private let lngFilter = KalmanFilter()

    // Heading smoothing: speed-adaptive + angular rate limiting
    private var lastSpeed: Float = 0
    private var lastHeadingTimestamp: TimeInterval = 0

    /// Max heading change per second (degrees). Prevents compass jitter.
    private let maxHeadingRateDegreesPerSec: Double = 180

    // GPS staleness detection
    private var lastGPSTimestamp: TimeInterval = 0
    private var staleTimer: Timer?
    /// Seconds without a GPS update before declaring signal lost.
    private let gpsStaleThreshold: TimeInterval = 5.0

    override init() {
        super.init()
        clManager.delegate = self
        clManager.desiredAccuracy = kCLLocationAccuracyBest
        clManager.distanceFilter = 0.5
    }

    func requestPermission() {
        clManager.requestWhenInUseAuthorization()
    }

    func startUpdating() {
        clManager.startUpdatingLocation()
        clManager.startUpdatingHeading()
        startStaleTimer()
    }

    func stopUpdating() {
        clManager.stopUpdatingLocation()
        clManager.stopUpdatingHeading()
        latFilter.reset()
        lngFilter.reset()
        staleTimer?.invalidate()
        staleTimer = nil
    }

    func setDesiredAccuracy(_ accuracy: CLLocationAccuracy) {
        clManager.desiredAccuracy = accuracy
    }

    // MARK: - GPS Staleness

    private func startStaleTimer() {
        staleTimer?.invalidate()
        staleTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.checkGPSStaleness()
        }
    }

    private func checkGPSStaleness() {
        guard lastGPSTimestamp > 0 else { return }
        let elapsed = ProcessInfo.processInfo.systemUptime - lastGPSTimestamp
        let isStale = elapsed > gpsStaleThreshold

        if isStale != gpsSignalLost {
            DispatchQueue.main.async { [weak self] in
                self?.gpsSignalLost = isStale
                if isStale {
                    print("[LocationManager] GPS signal lost (no update for \(String(format: "%.1f", elapsed))s)")
                } else {
                    print("[LocationManager] GPS signal restored")
                }
            }
        }
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }

        let now = ProcessInfo.processInfo.systemUptime
        lastGPSTimestamp = now
        lastSpeed = max(0, Float(loc.speed))

        // Dynamically adjust distance filter based on speed
        let newDistanceFilter: CLLocationDistance
        if loc.speed < 0.5 {
            newDistanceFilter = 0.5    // stationary: fine updates
        } else if loc.speed < 3.0 {
            newDistanceFilter = 1.0    // walking
        } else {
            newDistanceFilter = 2.0    // fast movement
        }
        if clManager.distanceFilter != newDistanceFilter {
            clManager.distanceFilter = newDistanceFilter
        }

        let smoothLat = latFilter.update(
            measurement: loc.coordinate.latitude,
            accuracy: Float(loc.horizontalAccuracy),
            speed: lastSpeed,
            timestamp: now
        )
        let smoothLng = lngFilter.update(
            measurement: loc.coordinate.longitude,
            accuracy: Float(loc.horizontalAccuracy),
            speed: lastSpeed,
            timestamp: now
        )

        // Restore signal if it was lost
        if gpsSignalLost {
            gpsSignalLost = false
        }

        lastLocation = LocationUpdate(
            lat: smoothLat,
            lng: smoothLng,
            altitude: loc.altitude,
            accuracy: Float(loc.horizontalAccuracy),
            speed: lastSpeed,
            course: Float(loc.course),
            timestamp: now
        )
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        guard newHeading.trueHeading >= 0 else { return }

        let raw = newHeading.trueHeading
        let now = ProcessInfo.processInfo.systemUptime

        // Shortest-path angular delta
        var delta = raw - heading
        if delta > 180 { delta -= 360 }
        if delta < -180 { delta += 360 }

        // Angular rate limiting: cap how fast heading can change
        let dt = lastHeadingTimestamp > 0 ? max(0.01, now - lastHeadingTimestamp) : 0.05
        let maxDelta = maxHeadingRateDegreesPerSec * dt
        delta = max(-maxDelta, min(maxDelta, delta))

        // Speed-adaptive smoothing factor:
        // Stationary → heavy smoothing (0.1), walking → moderate (0.3), fast → light (0.5)
        let factor: Double
        if lastSpeed < 0.3 {
            factor = 0.1
        } else if lastSpeed < 2.0 {
            factor = 0.2 + Double(lastSpeed) * 0.05
        } else {
            factor = 0.4
        }

        let smoothed = heading + delta * factor

        // Normalize to 0..<360
        var normalized = smoothed.truncatingRemainder(dividingBy: 360)
        if normalized < 0 { normalized += 360 }
        heading = normalized

        lastHeadingTimestamp = now
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus

        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            startUpdating()
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("[LocationManager] Error: \(error.localizedDescription)")

        // If location is unavailable, mark as stale immediately
        if let clError = error as? CLError, clError.code == .locationUnknown || clError.code == .denied {
            if !gpsSignalLost {
                gpsSignalLost = true
                print("[LocationManager] GPS unavailable: \(clError.code.rawValue)")
            }
        }
    }
}
