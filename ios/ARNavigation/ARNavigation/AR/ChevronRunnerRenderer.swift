import RealityKit
import simd

/// Pool of small chevron entities that slide forward along the path,
/// creating a directional "follow me" flow effect.
@MainActor
final class ChevronRunnerRenderer {
    private let runnerCount = 6
    private let runnerSpeed: Float = 3.0
    private let runnerScale: Float = 0.4
    private let runnerOpacity: Float = 0.35
    private let runnerYOffset: Float = 0.08

    private var runners: [ModelEntity] = []
    private var runnerOffsets: [Float] = []

    private var routePositions: [SIMD3<Float>] = []
    private var cumulativeDistances: [Float] = []
    private var totalDistance: Float = 0

    private var mesh: MeshResource?
    private var material: UnlitMaterial?

    var groundY: Float = -1.5

    /// Pre-build mesh and materials, pre-allocate runner entities.
    func prepare() {
        mesh = ArrowMeshResource.makeChevronMesh()

        var mat = UnlitMaterial()
        mat.color = .init(tint: ArrowMeshResource.arrowColor)
        mat.blending = .transparent(opacity: .init(floatLiteral: runnerOpacity))
        material = mat

        guard let mesh = mesh, let material = material else { return }

        for _ in 0..<runnerCount {
            let entity = ModelEntity(mesh: mesh, materials: [material])
            entity.scale = SIMD3<Float>(repeating: runnerScale)
            entity.isEnabled = false
            runners.append(entity)
            runnerOffsets.append(0)
        }
    }

    /// Store route positions and evenly distribute runners along path.
    func setRoute(worldPositions: [SIMD3<Float>]) {
        routePositions = worldPositions
        computeCumulativeDistances()

        guard totalDistance > 0 else { return }
        let spacing = totalDistance / Float(runnerCount)
        for i in 0..<runnerCount {
            runnerOffsets[i] = Float(i) * spacing
        }
    }

    private func computeCumulativeDistances() {
        cumulativeDistances = [0]
        totalDistance = 0
        for i in 1..<routePositions.count {
            let dx = routePositions[i].x - routePositions[i - 1].x
            let dz = routePositions[i].z - routePositions[i - 1].z
            totalDistance += sqrt(dx * dx + dz * dz)
            cumulativeDistances.append(totalDistance)
        }
    }

    /// Advance runners along path, interpolate positions, orient toward next waypoint.
    func update(userX: Float, userZ: Float, currentSegment: Int, anchor: AnchorEntity, deltaTime: Float) {
        guard totalDistance > 0, !runners.isEmpty else { return }

        // Add runners to anchor if not yet parented
        for runner in runners {
            if runner.parent == nil {
                anchor.addChild(runner)
            }
        }

        for i in 0..<runnerCount {
            runnerOffsets[i] += runnerSpeed * deltaTime

            // Loop back when reaching end
            if runnerOffsets[i] >= totalDistance {
                runnerOffsets[i] = runnerOffsets[i].truncatingRemainder(dividingBy: totalDistance)
            }

            let (pos, heading) = interpolatePosition(at: runnerOffsets[i])

            runners[i].position = SIMD3<Float>(pos.x, groundY + runnerYOffset, pos.z)
            runners[i].orientation = simd_quatf(angle: heading, axis: [0, 1, 0])
            runners[i].isEnabled = true
        }
    }

    /// Binary-search cumulative distances to find segment, then lerp within it.
    private func interpolatePosition(at dist: Float) -> (SIMD3<Float>, Float) {
        guard cumulativeDistances.count >= 2 else {
            return (routePositions.first ?? .zero, 0)
        }

        // Binary search for the segment containing this distance
        var lo = 0, hi = cumulativeDistances.count - 1
        while lo < hi - 1 {
            let mid = (lo + hi) / 2
            if cumulativeDistances[mid] <= dist {
                lo = mid
            } else {
                hi = mid
            }
        }

        let segStart = cumulativeDistances[lo]
        let segEnd = cumulativeDistances[hi]
        let segLen = segEnd - segStart
        let t = segLen > 0 ? (dist - segStart) / segLen : 0

        let p = mix(routePositions[lo], routePositions[hi], t: t)

        // Heading toward next waypoint
        let dx = routePositions[hi].x - routePositions[lo].x
        let dz = routePositions[hi].z - routePositions[lo].z
        let heading = atan2(dx, -dz)

        return (p, heading)
    }

    /// Remove all runners from scene and reset state.
    func cleanup() {
        for runner in runners {
            runner.removeFromParent()
            runner.isEnabled = false
        }
        routePositions.removeAll()
        cumulativeDistances.removeAll()
        totalDistance = 0
    }
}
