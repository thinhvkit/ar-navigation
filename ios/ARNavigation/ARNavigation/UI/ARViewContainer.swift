import SwiftUI
import RealityKit
import ARKit

/// UIViewRepresentable wrapping an ARView for SwiftUI.
struct ARViewContainer: UIViewRepresentable {
    let viewModel: NavigationViewModel

    func makeUIView(context: Context) -> ARView {
        let arView = ARView(frame: .zero)

        // Disable unnecessary ARView rendering features for performance
        arView.renderOptions = [
            .disableMotionBlur,
            .disableDepthOfField,
            .disablePersonOcclusion,
            .disableGroundingShadows,
            .disableFaceMesh,
        ]

        // Configure and run AR session
        let config = viewModel.arSessionManager.makeConfiguration()
        arView.session.run(config)
        arView.session.delegate = context.coordinator

        // Add a world-origin anchor for placing arrows
        let worldAnchor = AnchorEntity(world: .zero)
        arView.scene.addAnchor(worldAnchor)
        context.coordinator.worldAnchor = worldAnchor

        // Enable coaching overlay for better UX
        let coachingOverlay = ARCoachingOverlayView()
        coachingOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        coachingOverlay.session = arView.session
        coachingOverlay.goal = .horizontalPlane
        arView.addSubview(coachingOverlay)

        return arView
    }

    func updateUIView(_ arView: ARView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(viewModel: viewModel)
    }

    class Coordinator: NSObject, ARSessionDelegate {
        let viewModel: NavigationViewModel
        var worldAnchor: AnchorEntity?

        /// Once we get a confident ground Y, lock it and stop updating.
        private var groundLocked = false
        private var groundSamples: [Float] = []
        private var initialCameraY: Float?

        /// For computing per-frame deltaTime.
        private var lastFrameTime: TimeInterval?

        /// Frame counter for throttling secondary renderers.
        private var frameIndex: Int = 0

        init(viewModel: NavigationViewModel) {
            self.viewModel = viewModel
        }

        func session(_ session: ARSession, didAdd anchors: [ARAnchor]) {
            guard !groundLocked else { return }
            collectGroundPlanes(anchors)
        }

        func session(_ session: ARSession, didUpdate anchors: [ARAnchor]) {
            guard !groundLocked else { return }
            collectGroundPlanes(anchors)
        }

        /// Collect horizontal planes that are below the camera — likely the floor.
        private func collectGroundPlanes(_ anchors: [ARAnchor]) {
            guard let camY = initialCameraY else { return }

            for anchor in anchors {
                guard let plane = anchor as? ARPlaneAnchor,
                      plane.alignment == .horizontal else { continue }
                let planeY = anchor.transform.columns.3.y

                // Only accept planes significantly below camera (floor, not tables)
                guard planeY < camY - 0.5 else { continue }

                groundSamples.append(planeY)

                // After 3 consistent samples, lock ground Y
                if groundSamples.count >= 3 {
                    let median = groundSamples.sorted()[groundSamples.count / 2]
                    let vm = viewModel
                    DispatchQueue.main.async {
                        vm.arrowRenderer.groundY = median
                        vm.pathRibbonRenderer.groundY = median
                        vm.chevronRunnerRenderer.groundY = median
                    }
                    groundLocked = true
                    print("[AR] Ground locked at Y=\(median) from \(groundSamples.count) samples")
                }
            }
        }

        func session(_ session: ARSession, didUpdate frame: ARFrame) {
            let camera = frame.camera
            let isTracking = camera.trackingState == .normal
            let cameraY = camera.transform.columns.3.y

            // Compute deltaTime
            let currentTime = frame.timestamp
            let deltaTime: Float
            if let last = lastFrameTime {
                deltaTime = Float(min(currentTime - last, 0.1))  // cap at 100ms
            } else {
                deltaTime = 1.0 / 60.0
            }
            lastFrameTime = currentTime

            frameIndex += 1

            // Record initial camera height on first tracking frame
            if initialCameraY == nil && isTracking {
                initialCameraY = cameraY
                let estimate = cameraY - 1.4
                let vm = viewModel
                DispatchQueue.main.async {
                    vm.arrowRenderer.groundY = estimate
                    vm.pathRibbonRenderer.groundY = estimate
                    vm.chevronRunnerRenderer.groundY = estimate
                    print("[AR] Initial ground estimate Y=\(estimate) (camera at \(cameraY))")
                }
            }

            // Determine throttle level based on thermal state
            let thermalState = ProcessInfo.processInfo.thermalState
            let updateSecondary: Bool
            switch thermalState {
            case .nominal, .fair:
                updateSecondary = true
            case .serious:
                updateSecondary = frameIndex % 2 == 0
            case .critical:
                updateSecondary = frameIndex % 3 == 0
            @unknown default:
                updateSecondary = true
            }

            // Capture values for the main-thread block (avoid capturing self in hot path)
            let vm = viewModel
            let anchor = worldAnchor
            let fi = frameIndex
            let doSecondary = updateSecondary

            // Dispatch to main thread — DispatchQueue.main.async is ~10x faster
            // than Task{@MainActor} (no Swift concurrency job overhead).
            DispatchQueue.main.async {
                vm.arSessionManager.updateTrackingState(camera)
                vm.state.isTracking = isTracking

                guard isTracking,
                      let anchor = anchor,
                      !vm.state.route.isEmpty,
                      CoordinateConverter.shared.originSet else { return }

                let userLocal = CoordinateConverter.shared.gpsToLocal(
                    lat: vm.state.userLat,
                    lng: vm.state.userLng
                )

                // Shift world anchor for auto-walk testing
                if vm.autoWalkEnabled {
                    anchor.position = SIMD3<Float>(-userLocal.x, 0, -userLocal.z)
                }

                // Arrows always update (most important visual)
                vm.arrowRenderer.updateVisibleArrows(
                    userX: userLocal.x,
                    userZ: userLocal.z,
                    currentSegment: vm.state.currentSegment,
                    anchor: anchor,
                    deltaTime: deltaTime
                )

                // Ribbon + chevrons throttled under thermal pressure
                if doSecondary {
                    vm.pathRibbonRenderer.update(
                        userX: userLocal.x,
                        userZ: userLocal.z,
                        currentSegment: vm.state.currentSegment,
                        anchor: anchor
                    )

                    vm.chevronRunnerRenderer.update(
                        userX: userLocal.x,
                        userZ: userLocal.z,
                        currentSegment: vm.state.currentSegment,
                        anchor: anchor,
                        deltaTime: deltaTime
                    )
                }
            }
        }

        func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
            switch camera.trackingState {
            case .notAvailable:
                print("[AR] Tracking not available")
            case .limited(let reason):
                let reasonStr: String
                switch reason {
                case .initializing: reasonStr = "initializing"
                case .excessiveMotion: reasonStr = "excessive motion"
                case .insufficientFeatures: reasonStr = "insufficient features"
                case .relocalizing: reasonStr = "relocalizing"
                @unknown default: reasonStr = "unknown"
                }
                print("[AR] Tracking limited: \(reasonStr)")
            case .normal:
                print("[AR] Tracking normal")
            }
        }
    }
}
