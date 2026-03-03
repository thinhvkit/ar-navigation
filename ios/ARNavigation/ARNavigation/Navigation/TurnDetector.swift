import Foundation

enum TurnDetector {
    /// Precompute turn instructions for the entire route.
    /// Each instruction marks a waypoint where the user must change direction.
    static func computeTurnInstructions(route: [LatLng]) -> [TurnInstruction] {
        guard route.count >= 2 else { return [] }

        let converter = CoordinateConverter.shared
        var instructions: [TurnInstruction] = []

        // Build cumulative distances from start to each waypoint
        var cumulativeDistances = [Float](repeating: 0, count: route.count)
        for i in 1..<route.count {
            let segLen = converter.distanceBetween(
                lat1: route[i - 1].lat, lng1: route[i - 1].lng,
                lat2: route[i].lat, lng2: route[i].lng
            )
            cumulativeDistances[i] = cumulativeDistances[i - 1] + segLen
        }

        // For each triplet A→B→C, compute the signed turn angle at B
        for i in 1..<(route.count - 1) {
            let a = route[i - 1]
            let b = route[i]
            let c = route[i + 1]

            // Direction vectors in lat/lng space (scaled by cos for longitude)
            let cosLat = cos(b.lat * .pi / 180.0)
            let abX = (b.lng - a.lng) * cosLat
            let abY = b.lat - a.lat
            let bcX = (c.lng - b.lng) * cosLat
            let bcY = c.lat - b.lat

            // Signed angle: positive = right turn, negative = left turn
            let cross = abX * bcY - abY * bcX
            let dot = abX * bcX + abY * bcY
            let angle = atan2(cross, dot) * 180.0 / .pi  // degrees

            let direction = classify(angleDegrees: angle)

            // Only emit an instruction if it's not straight
            if direction != .straight {
                instructions.append(TurnInstruction(
                    direction: direction,
                    distanceToTurn: cumulativeDistances[i],
                    turnPointIndex: i
                ))
            }
        }

        // Always append arrive at the last waypoint
        instructions.append(TurnInstruction(
            direction: .arrive,
            distanceToTurn: cumulativeDistances[route.count - 1],
            turnPointIndex: route.count - 1
        ))

        return instructions
    }

    /// Find the next instruction still ahead of the user's current position along the route.
    /// Returns the instruction with distanceToTurn adjusted relative to the user's distanceAlongRoute.
    static func nextInstruction(
        instructions: [TurnInstruction],
        distanceAlongRoute: Float
    ) -> TurnInstruction? {
        for inst in instructions {
            if inst.distanceToTurn > distanceAlongRoute {
                return TurnInstruction(
                    direction: inst.direction,
                    distanceToTurn: inst.distanceToTurn - distanceAlongRoute,
                    turnPointIndex: inst.turnPointIndex
                )
            }
        }
        // If past all turns, return the arrive instruction with 0 distance
        if let last = instructions.last, last.direction == .arrive {
            return TurnInstruction(
                direction: .arrive,
                distanceToTurn: max(0, last.distanceToTurn - distanceAlongRoute),
                turnPointIndex: last.turnPointIndex
            )
        }
        return nil
    }

    /// Classify a signed turn angle into a TurnDirection.
    /// Positive = right, negative = left.
    private static func classify(angleDegrees: Double) -> TurnDirection {
        let abs = Swift.abs(angleDegrees)
        let isLeft = angleDegrees < 0

        if abs < 15 {
            return .straight
        } else if abs < 45 {
            return isLeft ? .slightLeft : .slightRight
        } else if abs < 120 {
            return isLeft ? .left : .right
        } else if abs < 160 {
            return isLeft ? .sharpLeft : .sharpRight
        } else {
            return .uTurn
        }
    }
}
