import Foundation
import CoreLocation

struct LatLng: Codable, Equatable {
    let lat: Double
    let lng: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

struct RouteInfo {
    let waypoints: [LatLng]
    let distanceMeters: Double
    let durationSeconds: Double
}

/// Result of snapping user position to route.
struct SnapResult {
    let snappedLat: Double
    let snappedLng: Double
    let segmentIndex: Int
    let distanceToRoute: Float
    let distanceAlongRoute: Float
}
