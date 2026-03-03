import ARKit
import RealityKit

/// Manages ARKit world tracking session lifecycle.
final class ARSessionManager {
    private(set) var isTracking = false

    /// Create an ARKit world tracking configuration for navigation.
    func makeConfiguration() -> ARWorldTrackingConfiguration {
        let config = ARWorldTrackingConfiguration()
        config.worldAlignment = .gravity
        config.planeDetection = [.horizontal]
        // Disable features we don't use — each adds per-frame GPU cost
        config.environmentTexturing = .none
        // Do NOT enable smoothedSceneDepth — it's expensive and unused
        return config
    }

    func updateTrackingState(_ camera: ARCamera) {
        isTracking = camera.trackingState == .normal
    }

    func updateTrackingState(_ tracking: Bool) {
        isTracking = tracking
    }
}
