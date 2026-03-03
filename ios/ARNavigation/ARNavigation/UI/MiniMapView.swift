import SwiftUI
import MapKit

/// Mini-map overlay showing real map tiles with route polyline and compass arrow.
struct MiniMapView: View {
    let route: [LatLng]
    let userLat: Double
    let userLng: Double
    let heading: Double
    let hasPosition: Bool
    let currentSegment: Int

    private let mapSize: CGFloat = 180

    @State private var position: MapCameraPosition = .automatic
    @State private var lastCameraLat: Double = 0
    @State private var lastCameraLng: Double = 0
    @State private var pulseScale: CGFloat = 1.0
    @State private var pulseOpacity: Double = 0.6

    var body: some View {
        Map(position: $position, interactionModes: []) {
            // Completed portion of route (dimmed)
            if route.count >= 2, currentSegment > 0 {
                let completedCoords = Array(route.prefix(through: min(currentSegment, route.count - 1)))
                    .map { $0.coordinate }
                if completedCoords.count >= 2 {
                    MapPolyline(coordinates: completedCoords)
                        .stroke(.blue.opacity(0.3), lineWidth: 2)
                }
            }

            // Remaining portion of route (bright)
            if route.count >= 2 {
                let remainingCoords = Array(route.suffix(from: min(currentSegment, route.count - 1)))
                    .map { $0.coordinate }
                if remainingCoords.count >= 2 {
                    MapPolyline(coordinates: remainingCoords)
                        .stroke(.blue, lineWidth: 4)
                }
            }

            // End marker (destination)
            if route.count >= 2, let last = route.last {
                Annotation("", coordinate: last.coordinate) {
                    Circle()
                        .fill(.red)
                        .frame(width: 10, height: 10)
                        .overlay(Circle().stroke(.white, lineWidth: 1.5))
                }
            }

            // User position with pulsing dot + compass arrow
            if hasPosition {
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: userLat, longitude: userLng)) {
                    ZStack {
                        // Pulsing ring
                        Circle()
                            .stroke(Color.green.opacity(pulseOpacity), lineWidth: 2)
                            .frame(width: 32 * pulseScale, height: 32 * pulseScale)

                        NavigationArrow(heading: heading)
                    }
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .frame(width: mapSize, height: mapSize)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.white.opacity(0.6), lineWidth: 1.5)
        )
        .onChange(of: route.count) {
            updateCamera()
        }
        .onChange(of: userLat) {
            updateCameraIfNeeded()
        }
        .onChange(of: userLng) {
            updateCameraIfNeeded()
        }
        .onAppear {
            updateCamera()
            startPulseAnimation()
        }
    }

    private func startPulseAnimation() {
        withAnimation(
            .easeInOut(duration: 1.2)
            .repeatForever(autoreverses: true)
        ) {
            pulseScale = 1.4
            pulseOpacity = 0.0
        }
    }

    private func updateCameraIfNeeded() {
        guard hasPosition, !route.isEmpty else { return }

        // Throttle camera updates: only move if user moved > 5m
        let dist = CoordinateConverter.shared.distanceBetween(
            lat1: lastCameraLat, lng1: lastCameraLng,
            lat2: userLat, lng2: userLng
        )
        if dist > 5 || lastCameraLat == 0 {
            centerOnUser()
        }
    }

    private func centerOnUser() {
        lastCameraLat = userLat
        lastCameraLng = userLng
        position = .region(MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: userLat, longitude: userLng),
            latitudinalMeters: 250,
            longitudinalMeters: 250
        ))
    }

    private func updateCamera() {
        // If navigating with position, center on user
        if hasPosition, !route.isEmpty {
            centerOnUser()
            return
        }

        guard route.count >= 2 else {
            position = .region(MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: userLat, longitude: userLng),
                latitudinalMeters: 200,
                longitudinalMeters: 200
            ))
            return
        }

        var minLat = route[0].lat, maxLat = route[0].lat
        var minLng = route[0].lng, maxLng = route[0].lng
        for p in route {
            minLat = min(minLat, p.lat)
            maxLat = max(maxLat, p.lat)
            minLng = min(minLng, p.lng)
            maxLng = max(maxLng, p.lng)
        }

        let center = CLLocationCoordinate2D(
            latitude: (minLat + maxLat) / 2,
            longitude: (minLng + maxLng) / 2
        )
        let latSpan = (maxLat - minLat) * 1.5 + 0.001
        let lngSpan = (maxLng - minLng) * 1.5 + 0.001

        position = .region(MKCoordinateRegion(
            center: center,
            span: MKCoordinateSpan(latitudeDelta: latSpan, longitudeDelta: lngSpan)
        ))
    }
}

/// Google Maps-style navigation arrow: dark teal triangle inside a circular disc.
private struct NavigationArrow: View {
    let heading: Double

    private let discSize: CGFloat = 24
    private let arrowColor = Color(red: 0.15, green: 0.35, blue: 0.30)
    private let discColor = Color(red: 0.70, green: 0.78, blue: 0.74)

    var body: some View {
        ZStack {
            // Outer ring
            Circle()
                .stroke(discColor.opacity(0.6), lineWidth: 2)
                .frame(width: discSize + 4, height: discSize + 4)

            // Semi-transparent disc background
            Circle()
                .fill(discColor.opacity(0.55))
                .frame(width: discSize, height: discSize)

            // Navigation icon
            Image(systemName: "location.north.fill")
                   .font(.system(size: 12))
                   .foregroundColor(.green)
                   .rotationEffect(.degrees(heading))
        }
    }
}

/// Custom triangle shape pointing up (north).
private struct NavigationTriangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
