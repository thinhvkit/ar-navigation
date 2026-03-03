import RealityKit
import UIKit
import simd

/// Renders a green ground-hugging ribbon along the route path.
/// Uses a procedural quad-strip mesh with UV flow shader for animated energy pulses.
/// Mesh is cached and only regenerated when the visible segment range changes.
@MainActor
final class PathRibbonRenderer {
    private var ribbonEntity: ModelEntity?
    private var routePositions: [SIMD3<Float>] = []

    private let ribbonWidth: Float = 0.6
    private let ribbonYOffset: Float = 0.01
    private let ribbonColor = UIColor(red: 0.3, green: 0.9, blue: 0.4, alpha: 1.0)
    private let ribbonOpacity: Float = 0.4

    private let minRenderDistance: Float = 5
    private let maxRenderDistance: Float = 35

    var groundY: Float = -1.5

    private var flowMaterial: RealityKit.Material?
    private var flowMaterialPrepared = false

    // Mesh cache: skip regeneration if visible range hasn't changed
    private var cachedStartIdx: Int = -1
    private var cachedEndIdx: Int = -1
    private var cachedGroundY: Float = -999

    func setRoute(worldPositions: [SIMD3<Float>]) {
        routePositions = worldPositions
        ribbonEntity?.removeFromParent()
        ribbonEntity = nil
        cachedStartIdx = -1
        cachedEndIdx = -1
        cachedGroundY = -999
    }

    func update(userX: Float, userZ: Float, currentSegment: Int, anchor: AnchorEntity) {
        guard routePositions.count >= 2 else { return }

        // Find visible range of waypoints by walking along route from user
        var routeDist: Float = 0
        var startIdx: Int?
        var endIdx = currentSegment
        var prevX = userX
        var prevZ = userZ

        for i in currentSegment..<routePositions.count {
            let pos = routePositions[i]
            let dx = pos.x - prevX
            let dz = pos.z - prevZ
            routeDist += sqrt(dx * dx + dz * dz)
            prevX = pos.x
            prevZ = pos.z

            if routeDist > maxRenderDistance { break }
            if startIdx == nil && routeDist >= minRenderDistance {
                startIdx = max(currentSegment, i - 1)
            }
            endIdx = i
        }

        guard let start = startIdx, endIdx > start else {
            ribbonEntity?.removeFromParent()
            ribbonEntity = nil
            cachedStartIdx = -1
            cachedEndIdx = -1
            return
        }

        // Only regenerate mesh if the visible range or ground Y changed
        let groundChanged = abs(groundY - cachedGroundY) > 0.01
        if start == cachedStartIdx && endIdx == cachedEndIdx && !groundChanged {
            return  // Mesh is still valid — skip expensive regeneration
        }

        cachedStartIdx = start
        cachedEndIdx = endIdx
        cachedGroundY = groundY

        let visiblePositions = Array(routePositions[start...endIdx])
        guard let mesh = generateRibbonMesh(positions: visiblePositions) else { return }

        if let existing = ribbonEntity {
            existing.model?.mesh = mesh
        } else {
            // Prepare flow material once (lazy)
            if !flowMaterialPrepared {
                flowMaterial = prepareFlowMaterial()
                flowMaterialPrepared = true
            }

            let material: RealityKit.Material
            if let flow = flowMaterial {
                material = flow
            } else {
                // Fallback to static UnlitMaterial (e.g., simulator)
                var unlit = UnlitMaterial()
                unlit.color = .init(tint: ribbonColor)
                unlit.blending = .transparent(opacity: .init(floatLiteral: ribbonOpacity))
                material = unlit
            }

            let entity = ModelEntity(mesh: mesh, materials: [material])
            anchor.addChild(entity)
            ribbonEntity = entity
        }
    }

    private func generateRibbonMesh(positions: [SIMD3<Float>]) -> MeshResource? {
        guard positions.count >= 2 else { return nil }

        let halfW = ribbonWidth / 2.0
        let y = groundY + ribbonYOffset
        let normal = SIMD3<Float>(0, 1, 0)

        let count = positions.count
        var vertices = [SIMD3<Float>]()
        vertices.reserveCapacity(count * 2)
        var normals = [SIMD3<Float>]()
        normals.reserveCapacity(count * 2)
        var uvs = [SIMD2<Float>]()
        uvs.reserveCapacity(count * 2)
        var accumDist: Float = 0

        for i in 0..<count {
            // Accumulate distance for UV v-coordinate
            if i > 0 {
                let dx = positions[i].x - positions[i - 1].x
                let dz = positions[i].z - positions[i - 1].z
                accumDist += sqrt(dx * dx + dz * dz)
            }

            let perp: SIMD2<Float>

            if i == 0 {
                perp = perpendicular(from: positions[0], to: positions[1])
            } else if i == count - 1 {
                perp = perpendicular(from: positions[i - 1], to: positions[i])
            } else {
                // Average perpendiculars of adjacent segments for smooth corners
                let p1 = perpendicular(from: positions[i - 1], to: positions[i])
                let p2 = perpendicular(from: positions[i], to: positions[i + 1])
                let avg = (p1 + p2) / 2
                let len = sqrt(avg.x * avg.x + avg.y * avg.y)
                perp = len > 0 ? avg / len : SIMD2<Float>(1, 0)
            }

            let left = SIMD3<Float>(positions[i].x + perp.x * halfW, y, positions[i].z + perp.y * halfW)
            let right = SIMD3<Float>(positions[i].x - perp.x * halfW, y, positions[i].z - perp.y * halfW)

            vertices.append(left)
            vertices.append(right)
            normals.append(normal)
            normals.append(normal)
            uvs.append(SIMD2<Float>(0, accumDist))   // left edge
            uvs.append(SIMD2<Float>(1, accumDist))   // right edge
        }

        let triCount = (count - 1) * 4  // 2 faces * 2 triangles each
        var indices = [UInt32]()
        indices.reserveCapacity(triCount * 3)
        for i in 0..<(count - 1) {
            let base = UInt32(i * 2)
            // Top face (CCW from above)
            indices.append(contentsOf: [base, base + 2, base + 1])
            indices.append(contentsOf: [base + 1, base + 2, base + 3])
            // Bottom face (reversed winding for double-sided)
            indices.append(contentsOf: [base, base + 1, base + 2])
            indices.append(contentsOf: [base + 1, base + 3, base + 2])
        }

        var descriptor = MeshDescriptor()
        descriptor.positions = MeshBuffer(vertices)
        descriptor.normals = MeshBuffer(normals)
        descriptor.textureCoordinates = MeshBuffer(uvs)
        descriptor.primitives = .triangles(indices)

        return try? MeshResource.generate(from: [descriptor])
    }

    /// Load Metal surface shader for animated flow effect. Returns nil on failure.
    private func prepareFlowMaterial() -> RealityKit.Material? {
        guard let device = MTLCreateSystemDefaultDevice(),
              let library = device.makeDefaultLibrary() else {
            print("[PathRibbon] No Metal device or library")
            return nil
        }

        do {
            let surfaceShader = CustomMaterial.SurfaceShader(
                named: "ribbonFlowSurface", in: library
            )
            var material = try CustomMaterial(
                surfaceShader: surfaceShader, lightingModel: .unlit
            )
            material.faceCulling = .none
            material.blending = .transparent(opacity: .init(floatLiteral: 1.0))
            return material
        } catch {
            print("[PathRibbon] CustomMaterial failed: \(error)")
            return nil
        }
    }

    /// Perpendicular direction in XZ plane (unit length) for a segment.
    private func perpendicular(from a: SIMD3<Float>, to b: SIMD3<Float>) -> SIMD2<Float> {
        let dx = b.x - a.x
        let dz = b.z - a.z
        let len = sqrt(dx * dx + dz * dz)
        guard len > 0 else { return SIMD2<Float>(1, 0) }
        return SIMD2<Float>(-dz / len, dx / len)
    }

    func cleanup() {
        ribbonEntity?.removeFromParent()
        ribbonEntity = nil
        routePositions = []
        flowMaterialPrepared = false
        flowMaterial = nil
        cachedStartIdx = -1
        cachedEndIdx = -1
        cachedGroundY = -999
    }
}
