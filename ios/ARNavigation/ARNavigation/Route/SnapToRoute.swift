import Foundation

/// Snaps user position to the nearest point on a route polyline.
enum SnapToRoute {
    static func snap(
        userLat: Double, userLng: Double,
        route: [LatLng]
    ) -> SnapResult {
        guard route.count >= 2 else {
            return SnapResult(
                snappedLat: userLat, snappedLng: userLng,
                segmentIndex: 0, distanceToRoute: 0, distanceAlongRoute: 0
            )
        }

        var bestDist = Double.greatestFiniteMagnitude
        var bestLat = userLat
        var bestLng = userLng
        var bestSegment = 0
        var distAlong: Double = 0
        var cumulativeDistance: Double = 0

        for i in 0..<(route.count - 1) {
            let a = route[i]
            let b = route[i + 1]

            let projected = projectOntoSegment(
                pLat: userLat, pLng: userLng,
                aLat: a.lat, aLng: a.lng,
                bLat: b.lat, bLng: b.lng
            )

            let dx = userLat - projected.0
            let dy = userLng - projected.1
            let dist = dx * dx + dy * dy

            if dist < bestDist {
                bestDist = dist
                bestLat = projected.0
                bestLng = projected.1
                bestSegment = i

                let segStartToSnap = sqrt(
                    (projected.0 - a.lat) * (projected.0 - a.lat) +
                    (projected.1 - a.lng) * (projected.1 - a.lng)
                )
                let segLen = sqrt(
                    (b.lat - a.lat) * (b.lat - a.lat) +
                    (b.lng - a.lng) * (b.lng - a.lng)
                )
                let fraction = segLen > 0 ? segStartToSnap / segLen : 0
                let segMeters = Double(CoordinateConverter.shared.distanceBetween(
                    lat1: a.lat, lng1: a.lng, lat2: b.lat, lng2: b.lng
                ))
                distAlong = cumulativeDistance + fraction * segMeters
            }

            cumulativeDistance += Double(CoordinateConverter.shared.distanceBetween(
                lat1: a.lat, lng1: a.lng, lat2: b.lat, lng2: b.lng
            ))
        }

        let distToRoute = CoordinateConverter.shared.distanceBetween(
            lat1: userLat, lng1: userLng, lat2: bestLat, lng2: bestLng
        )

        return SnapResult(
            snappedLat: bestLat,
            snappedLng: bestLng,
            segmentIndex: bestSegment,
            distanceToRoute: distToRoute,
            distanceAlongRoute: Float(distAlong)
        )
    }

    /// Project point P onto segment AB, clamped to [0, 1].
    private static func projectOntoSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ) -> (Double, Double) {
        let abLat = bLat - aLat
        let abLng = bLng - aLng
        let apLat = pLat - aLat
        let apLng = pLng - aLng

        let ab2 = abLat * abLat + abLng * abLng
        guard ab2 > 1e-20 else { return (aLat, aLng) }

        let t = max(0, min(1, (apLat * abLat + apLng * abLng) / ab2))
        return (aLat + t * abLat, aLng + t * abLng)
    }
}
