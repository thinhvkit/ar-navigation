import Foundation
import simd

/// Converts GPS coordinates (lat/lng) to local meter-based coordinates.
/// Uses flat-earth approximation accurate within AR range (~1km).
///
/// Coordinate system (matches ARKit): right-handed, Y-up, -Z forward.
/// - x = east/west offset in meters
/// - y = always 0 (ground plane)
/// - z = negated north/south offset (north = -z)
final class CoordinateConverter {
    static let shared = CoordinateConverter()

    private let earthRadius: Double = 6378137.0

    private(set) var originLat: Double = 0
    private(set) var originLng: Double = 0
    private var cosOriginLat: Double = 1
    private(set) var originSet = false

    func setOrigin(lat: Double, lng: Double) {
        originLat = lat
        originLng = lng
        cosOriginLat = cos(lat * .pi / 180.0)
        originSet = true
    }

    /// Convert GPS to local XZ coordinates in meters.
    /// Returns SIMD3<Float>(x, 0, z).
    func gpsToLocal(lat: Double, lng: Double) -> SIMD3<Float> {
        guard originSet else { return .zero }

        let dlat = (lat - originLat) * .pi / 180.0
        let dlng = (lng - originLng) * .pi / 180.0

        let x = Float(dlng * earthRadius * cosOriginLat)
        let z = Float(-(dlat * earthRadius))

        return SIMD3<Float>(x, 0, z)
    }

    /// Distance between two GPS points in meters (flat-earth approximation).
    func distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double) -> Float {
        let dlat = (lat2 - lat1) * .pi / 180.0
        let dlng = (lng2 - lng1) * .pi / 180.0
        let cosLat = cos((lat1 + lat2) / 2.0 * .pi / 180.0)

        let dx = dlng * earthRadius * cosLat
        let dz = dlat * earthRadius

        return Float(sqrt(dx * dx + dz * dz))
    }

    func reset() {
        originSet = false
        originLat = 0
        originLng = 0
        cosOriginLat = 1
    }
}
