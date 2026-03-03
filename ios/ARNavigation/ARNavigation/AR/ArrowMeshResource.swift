import RealityKit
import UIKit
import simd

/// Creates chevron arrow mesh and material for route visualization.
/// Each chevron is a flat V-shape generated with MeshDescriptor (both arms in one mesh).
enum ArrowMeshResource {
    // Chevron arm dimensions
    static let chevronArmLength: Float = 0.7
    static let chevronHeight: Float = 0.02
    static let chevronArmWidth: Float = 0.18
    static let chevronAngle: Float = 30  // degrees from center axis

    // Arrow color: teal with transparency for holographic look
    static let arrowColor = UIColor(red: 0.25, green: 0.95, blue: 0.78, alpha: 1.0)

    // Y offset above ground
    static let yOffset: Float = 0.15

    // Halo layer scale relative to core chevron
    static let haloScale = SIMD3<Float>(1.3, 1.5, 1.1)

    struct ArrowPlacement {
        let position: SIMD3<Float>  // local meters
        let rotationY: Float        // radians
        let segmentIndex: Int
    }

    /// Generate a flat V-shape chevron mesh with both arms in a single MeshDescriptor.
    /// Tip at origin, arms extend in -Z direction at ±chevronAngle.
    @MainActor
    static func makeChevronMesh() -> MeshResource {
        let angle = chevronAngle * .pi / 180.0
        let length = chevronArmLength
        let halfW = chevronArmWidth / 2.0

        let sinA = sin(angle)
        let cosA = cos(angle)

        // Left arm direction and perpendicular
        let leftDir = SIMD3<Float>(-sinA, 0, -cosA)
        let leftPerp = SIMD3<Float>(cosA, 0, -sinA)  // cross(leftDir, Y-up), points toward center

        // Right arm direction and perpendicular
        let rightDir = SIMD3<Float>(sinA, 0, -cosA)
        let rightInnerPerp = SIMD3<Float>(-cosA, 0, -sinA)  // toward center

        let up = SIMD3<Float>(0, 1, 0)
        let down = SIMD3<Float>(0, -1, 0)

        var positions: [SIMD3<Float>] = []
        var normals: [SIMD3<Float>] = []
        var indices: [UInt32] = []

        // Helper: add one arm (top + bottom faces, 8 verts, 4 triangles)
        func addArm(dir: SIMD3<Float>, innerPerp: SIMD3<Float>) {
            let tipInner = halfW * innerPerp
            let tipOuter = -halfW * innerPerp
            let endInner = tipInner + length * dir
            let endOuter = tipOuter + length * dir

            let base = UInt32(positions.count)

            // Top face vertices (0-3)
            positions.append(contentsOf: [tipInner, tipOuter, endOuter, endInner])
            normals.append(contentsOf: [up, up, up, up])
            // CCW from above: tipInner→endInner→tipOuter, tipOuter→endInner→endOuter
            indices.append(contentsOf: [base, base+3, base+1,
                                        base+1, base+3, base+2])

            // Bottom face vertices (4-7), same positions, reversed winding
            let b = base + 4
            positions.append(contentsOf: [tipInner, tipOuter, endOuter, endInner])
            normals.append(contentsOf: [down, down, down, down])
            indices.append(contentsOf: [b, b+1, b+3,
                                        b+1, b+2, b+3])
        }

        addArm(dir: leftDir, innerPerp: leftPerp)
        addArm(dir: rightDir, innerPerp: rightInnerPerp)

        var descriptor = MeshDescriptor()
        descriptor.positions = MeshBuffer(positions)
        descriptor.normals = MeshBuffer(normals)
        descriptor.primitives = .triangles(indices)

        return try! MeshResource.generate(from: [descriptor])
    }

    /// Core arrow material: unlit teal, semi-transparent for holographic look.
    @MainActor
    static func makeCoreArrowMaterial() -> UnlitMaterial {
        var material = UnlitMaterial()
        material.color = .init(tint: arrowColor)
        material.blending = .transparent(opacity: .init(floatLiteral: 0.65))
        return material
    }

    /// Halo material: soft emissive glow around edges.
    @MainActor
    static func makeHaloMaterial() -> PhysicallyBasedMaterial {
        var material = PhysicallyBasedMaterial()
        material.baseColor = .init(tint: arrowColor.withAlphaComponent(0.3))
        material.emissiveColor = .init(color: arrowColor)
        material.emissiveIntensity = 4.0
        material.blending = .transparent(opacity: .init(floatLiteral: 0.35))
        material.faceCulling = .none
        return material
    }

    /// Generate a flat disk mesh for glow effect at arrow base.
    @MainActor
    static func makeGlowDiskMesh(radius: Float = 0.35, segments: Int = 16) -> MeshResource {
        var positions: [SIMD3<Float>] = []
        var normals: [SIMD3<Float>] = []
        var indices: [UInt32] = []

        let up = SIMD3<Float>(0, 1, 0)

        // Center vertex
        positions.append(.zero)
        normals.append(up)

        // Ring vertices (0 through 2*pi, inclusive to close the fan)
        for i in 0...segments {
            let angle = Float(i) / Float(segments) * 2.0 * .pi
            positions.append(SIMD3<Float>(cos(angle) * radius, 0, sin(angle) * radius))
            normals.append(up)
        }

        // Triangle fan (top face, CCW from above)
        for i in 1...segments {
            indices.append(contentsOf: [0, UInt32(i), UInt32(i + 1)])
        }

        // Bottom face (reversed winding for double-sided rendering)
        let bottomBase = UInt32(positions.count)
        let down = SIMD3<Float>(0, -1, 0)
        positions.append(.zero)
        normals.append(down)
        for i in 0...segments {
            let angle = Float(i) / Float(segments) * 2.0 * .pi
            positions.append(SIMD3<Float>(cos(angle) * radius, 0, sin(angle) * radius))
            normals.append(down)
        }
        for i in 1...segments {
            indices.append(contentsOf: [bottomBase, bottomBase + UInt32(i + 1), bottomBase + UInt32(i)])
        }

        var descriptor = MeshDescriptor()
        descriptor.positions = MeshBuffer(positions)
        descriptor.normals = MeshBuffer(normals)
        descriptor.primitives = .triangles(indices)

        return try! MeshResource.generate(from: [descriptor])
    }

    /// Glow ring material: unlit teal, 30% opacity.
    @MainActor
    static func makeGlowRingMaterial() -> UnlitMaterial {
        var material = UnlitMaterial()
        material.color = .init(tint: arrowColor)
        material.blending = .transparent(opacity: .init(floatLiteral: 0.3))
        return material
    }

    /// Place one arrow at each route waypoint, rotated toward the next waypoint.
    static func computeArrowPlacements(
        worldPositions: [SIMD3<Float>],
        spacing: Float = 1.5
    ) -> [ArrowPlacement] {
        guard worldPositions.count >= 2 else { return [] }

        var placements: [ArrowPlacement] = []

        for i in 0..<(worldPositions.count - 1) {
            let pos = worldPositions[i]
            let next = worldPositions[i + 1]

            let dx = next.x - pos.x
            let dz = next.z - pos.z

            // Rotation: angle from +Z axis to direction vector (around Y axis)
            let rotY = atan2(dx, -dz)

            placements.append(ArrowPlacement(
                position: SIMD3<Float>(pos.x, yOffset, pos.z),
                rotationY: rotY,
                segmentIndex: i
            ))
        }

        return placements
    }
}
