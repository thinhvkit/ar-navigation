import RealityKit
import UIKit
import simd

/// Manages rendering of chevron navigation arrows in the AR scene.
/// Uses entity caching with differential updates to support animations.
/// Each arrow: Parent → Chevron (core) → Halo, GlowDisk, Particles.
@MainActor
final class ArrowRenderer {
    private let minRenderDistance: Float = 10
    private let maxRenderDistance: Float = 30
    private let maxActiveArrows = 20

    // Animation parameters
    private let bobAmplitude: Float = 0.08
    private let bobFrequency: Float = 1.2
    private let pulseScale: Float = 0.15
    private let pulseFrequency: Float = 0.8
    private let spawnDuration: Float = 0.4
    private let despawnDuration: Float = 0.3

    struct ArrowAnimState {
        var spawnProgress: Float = 0
        var despawnProgress: Float = 0
        var isDespawning: Bool = false
        var bobPhase: Float
        var pulsePhase: Float
        var lastFadeBucket: Int = -1  // which pre-built material bucket is applied
    }

    private var allPlacements: [ArrowMeshResource.ArrowPlacement] = []
    private var cachedArrows: [Int: Entity] = [:]
    private var animationStates: [Int: ArrowAnimState] = [:]
    private var chevronMesh: MeshResource?
    private var glowDiskMesh: MeshResource?

    // Pre-bucketed materials: 11 buckets (0%, 10%, 20%, ..., 100% opacity)
    // Avoids per-frame material creation which is a major GPU stall.
    private let fadeBucketCount = 11
    private var coreMaterials: [UnlitMaterial] = []
    private var haloMaterials: [PhysicallyBasedMaterial] = []
    private var glowMaterials: [UnlitMaterial] = []

    /// Ground Y in AR world space. Updated from detected horizontal planes.
    var groundY: Float = -1.5

    /// Pre-build mesh and materials (call once on init).
    func prepare() {
        chevronMesh = ArrowMeshResource.makeChevronMesh()
        glowDiskMesh = ArrowMeshResource.makeGlowDiskMesh()

        // Build opacity-bucketed material sets
        let baseCore = ArrowMeshResource.makeCoreArrowMaterial()
        let baseHalo = ArrowMeshResource.makeHaloMaterial()
        let baseGlow = ArrowMeshResource.makeGlowRingMaterial()

        coreMaterials.removeAll()
        haloMaterials.removeAll()
        glowMaterials.removeAll()

        for i in 0..<fadeBucketCount {
            let fade = Float(i) / Float(fadeBucketCount - 1)

            var core = baseCore
            core.blending = .transparent(opacity: .init(floatLiteral: 0.65 * fade))
            coreMaterials.append(core)

            var halo = baseHalo
            halo.blending = .transparent(opacity: .init(floatLiteral: 0.35 * fade))
            haloMaterials.append(halo)

            var g = baseGlow
            g.blending = .transparent(opacity: .init(floatLiteral: 0.3 * fade))
            glowMaterials.append(g)
        }
    }

    /// Set the full route's arrow placements.
    func setRoute(placements: [ArrowMeshResource.ArrowPlacement]) {
        allPlacements = placements
        clearArrows()
        print("[ArrowRenderer] Route set with \(placements.count) arrow positions")
    }

    /// Update visible arrows with differential caching and animations.
    func updateVisibleArrows(
        userX: Float, userZ: Float,
        currentSegment: Int,
        anchor: AnchorEntity,
        deltaTime: Float
    ) {
        guard let mesh = chevronMesh, !coreMaterials.isEmpty else { return }

        // 1. Compute which placement indices should be visible
        var newVisibleIndices = Set<Int>()
        var distanceMap: [Int: Float] = [:]

        var routeDist: Float = 0
        var prevX = userX
        var prevZ = userZ

        for i in 0..<allPlacements.count {
            let arrow = allPlacements[i]
            if arrow.segmentIndex < currentSegment { continue }

            let dx = arrow.position.x - prevX
            let dz = arrow.position.z - prevZ
            routeDist += sqrt(dx * dx + dz * dz)
            prevX = arrow.position.x
            prevZ = arrow.position.z

            if routeDist > maxRenderDistance { break }
            if routeDist >= minRenderDistance {
                newVisibleIndices.insert(i)
                distanceMap[i] = routeDist
                if newVisibleIndices.count >= maxActiveArrows { break }
            }
        }

        // 2. Determine arrows to spawn and despawn
        let currentIndices = Set(cachedArrows.keys.filter {
            !(animationStates[$0]?.isDespawning ?? false)
        })
        let toSpawn = newVisibleIndices.subtracting(currentIndices)
        let toDespawn = currentIndices.subtracting(newVisibleIndices)

        // 3. Mark departing arrows for despawn
        for idx in toDespawn {
            if var state = animationStates[idx] {
                state.isDespawning = true
                state.despawnProgress = 0
                animationStates[idx] = state
            }
        }

        // 4. Reverse despawn for arrows that re-entered visible range
        for idx in newVisibleIndices {
            if var state = animationStates[idx], state.isDespawning {
                state.isDespawning = false
                state.despawnProgress = 0
                animationStates[idx] = state
            }
        }

        // 5. Create new arrow entities
        let arrowY = groundY + ArrowMeshResource.yOffset
        let fullOpacityCore = coreMaterials[fadeBucketCount - 1]
        let fullOpacityHalo = haloMaterials[fadeBucketCount - 1]

        for idx in toSpawn {
            let arrow = allPlacements[idx]

            let parent = Entity()
            parent.position = SIMD3<Float>(arrow.position.x, arrowY, arrow.position.z)
            parent.orientation = simd_quatf(angle: arrow.rotationY, axis: [0, 1, 0])
            parent.scale = .zero  // spawn animation starts from 0

            // Chevron core + halo
            let chevron = ModelEntity(mesh: mesh, materials: [fullOpacityCore])
            chevron.name = "chevron"
            let haloEntity = ModelEntity(mesh: mesh, materials: [fullOpacityHalo])
            haloEntity.scale = ArrowMeshResource.haloScale
            chevron.addChild(haloEntity)
            parent.addChild(chevron)

            // Glow disk at ground level
            if let glowMesh = glowDiskMesh, !glowMaterials.isEmpty {
                let glowDisk = ModelEntity(mesh: glowMesh, materials: [glowMaterials[fadeBucketCount - 1]])
                glowDisk.name = "glow"
                glowDisk.position.y = -(ArrowMeshResource.yOffset - 0.005)
                parent.addChild(glowDisk)
            }

            // Particle sparkle trail
            if #available(iOS 18.0, *) {
                addParticleEmitter(to: parent)
            }

            anchor.addChild(parent)
            cachedArrows[idx] = parent
            animationStates[idx] = ArrowAnimState(
                bobPhase: Float.random(in: 0...(.pi * 2)),
                pulsePhase: Float.random(in: 0...(.pi * 2))
            )
        }

        // 6. Animate all cached arrows
        updateAnimations(deltaTime: deltaTime, distanceMap: distanceMap, baseY: arrowY)

        // 7. Remove fully despawned entities
        var toRemove: [Int] = []
        for (idx, state) in animationStates {
            if state.isDespawning && state.despawnProgress >= 1.0 {
                toRemove.append(idx)
            }
        }
        for idx in toRemove {
            cachedArrows[idx]?.removeFromParent()
            cachedArrows.removeValue(forKey: idx)
            animationStates.removeValue(forKey: idx)
        }
    }

    @available(iOS 18.0, *)
    private func addParticleEmitter(to parent: Entity) {
        var particles = ParticleEmitterComponent()
        particles.emitterShape = .point
        particles.speed = 0.05
        particles.speedVariation = 0.02

        particles.mainEmitter.birthRate = 15
        particles.mainEmitter.lifeSpan = 0.8
        particles.mainEmitter.size = 0.007
        particles.mainEmitter.sizeVariation = 0.003
        particles.mainEmitter.blendMode = .additive
        particles.mainEmitter.acceleration = [0, 0.3, 0]
        particles.mainEmitter.color = .evolving(
            start: .single(.white),
            end: .single(UIColor(red: 0.25, green: 0.95, blue: 0.78, alpha: 0))
        )

        let particleEntity = Entity()
        particleEntity.name = "particles"
        particleEntity.components.set(particles)
        parent.addChild(particleEntity)
    }

    private func updateAnimations(deltaTime: Float, distanceMap: [Int: Float], baseY: Float) {
        for (idx, entity) in cachedArrows {
            guard var state = animationStates[idx] else { continue }

            // Advance spawn/despawn progress
            if state.isDespawning {
                state.despawnProgress = min(1.0, state.despawnProgress + deltaTime / despawnDuration)
            } else if state.spawnProgress < 1.0 {
                state.spawnProgress = min(1.0, state.spawnProgress + deltaTime / spawnDuration)
            }

            // Scale from spawn/despawn
            var scaleMultiplier: Float = 1.0
            if state.isDespawning {
                scaleMultiplier = 1.0 - easeOutCubic(state.despawnProgress)
            } else if state.spawnProgress < 1.0 {
                scaleMultiplier = easeOutBack(state.spawnProgress)
            }

            // Bob animation (vertical oscillation)
            state.bobPhase += deltaTime * bobFrequency * 2 * .pi
            let bobOffset = sin(state.bobPhase) * bobAmplitude

            // Pulse animation (scale breathing)
            state.pulsePhase += deltaTime * pulseFrequency * 2 * .pi
            let pulseMultiplier = 1.0 + sin(state.pulsePhase) * pulseScale

            // Apply position with bob
            entity.position.y = baseY + bobOffset

            // Apply combined scale
            let finalScale = scaleMultiplier * pulseMultiplier
            entity.scale = SIMD3<Float>(repeating: finalScale)

            // Bucketed opacity fade — only swap materials when bucket changes
            if let dist = distanceMap[idx] {
                let fade = smoothFade(dist)
                let bucket = min(fadeBucketCount - 1, max(0, Int(fade * Float(fadeBucketCount - 1) + 0.5)))
                if bucket != state.lastFadeBucket {
                    state.lastFadeBucket = bucket
                    applyFadeBucket(bucket, to: entity)
                }
            }

            animationStates[idx] = state
        }
    }

    /// Swap pre-built materials by bucket index — no allocation, just pointer swap.
    private func applyFadeBucket(_ bucket: Int, to entity: Entity) {
        guard let chevron = entity.children.first(where: { $0.name == "chevron" }) as? ModelEntity else { return }
        chevron.model?.materials = [coreMaterials[bucket]]

        if let haloChild = chevron.children.first as? ModelEntity {
            haloChild.model?.materials = [haloMaterials[bucket]]
        }

        if !glowMaterials.isEmpty,
           let glowDisk = entity.children.first(where: { $0.name == "glow" }) as? ModelEntity {
            glowDisk.model?.materials = [glowMaterials[bucket]]
        }
    }

    /// Ease-in-out quadratic: 1.0 at minDist → 0.0 at maxDist.
    private func smoothFade(_ dist: Float) -> Float {
        let t = max(0, min(1, (dist - minRenderDistance) / (maxRenderDistance - minRenderDistance)))
        return t < 0.5
            ? 1.0 - 2 * t * t
            : 1.0 - (1.0 - pow(-2 * t + 2, 2) / 2)
    }

    /// Overshoot easing for spawn pop-in.
    private func easeOutBack(_ t: Float) -> Float {
        let c1: Float = 1.70158
        let c3 = c1 + 1
        return 1 + c3 * pow(t - 1, 3) + c1 * pow(t - 1, 2)
    }

    /// Smooth deceleration for despawn shrink.
    private func easeOutCubic(_ t: Float) -> Float {
        1 - pow(1 - t, 3)
    }

    /// Remove all active arrow entities.
    func clearArrows() {
        for (_, entity) in cachedArrows {
            entity.removeFromParent()
        }
        cachedArrows.removeAll()
        animationStates.removeAll()
    }

    var activeCount: Int { cachedArrows.count }
}
