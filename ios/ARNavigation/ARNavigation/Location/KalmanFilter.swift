import Foundation

/// 1D Kalman filter for GPS coordinate smoothing with outlier rejection
/// and speed-adaptive process noise. Applied independently to latitude and longitude.
final class KalmanFilter {
    private var estimate: Double = 0
    private var errorCovariance: Double = 1
    private var initialized = false

    // Velocity tracking for prediction step
    private var velocity: Double = 0
    private var lastTimestamp: TimeInterval = 0

    /// Base process noise — scaled by speed.
    private let baseProcessNoise: Double = 1e-5

    /// Maximum allowed jump in one update (degrees).
    /// ~100m at equator = 0.0009 degrees. Reject anything beyond this
    /// unless accuracy is very poor (>50m).
    private let maxJumpDegrees: Double = 0.001

    /// Number of consecutive rejected readings before forcing acceptance.
    private var consecutiveRejects: Int = 0
    private let maxConsecutiveRejects: Int = 5

    func update(measurement: Double, accuracy: Float = 5, speed: Float = 0, timestamp: TimeInterval = 0) -> Double {
        // Derive measurement noise from GPS accuracy (meters → degrees approx)
        let measurementNoise = max(Double(accuracy) * 1e-5, 1e-6)

        if !initialized {
            estimate = measurement
            errorCovariance = measurementNoise
            lastTimestamp = timestamp
            initialized = true
            return estimate
        }

        // Outlier rejection: reject implausible jumps
        let jump = abs(measurement - estimate)
        if jump > maxJumpDegrees && accuracy < 50 && consecutiveRejects < maxConsecutiveRejects {
            consecutiveRejects += 1
            // Still return the current estimate — don't incorporate the outlier
            return estimate
        }
        consecutiveRejects = 0

        // Speed-adaptive process noise: faster movement = more uncertainty in prediction
        let speedFactor = max(1.0, Double(speed) * 0.5)  // walking ~1.5m/s → factor ~1.75
        let processNoise = baseProcessNoise * speedFactor

        // Prediction step with velocity model
        var predictedEstimate = estimate
        if timestamp > 0 && lastTimestamp > 0 {
            let dt = timestamp - lastTimestamp
            if dt > 0 && dt < 5.0 {  // sanity: ignore gaps > 5s
                predictedEstimate = estimate + velocity * dt
            }
        }
        let predictedError = errorCovariance + processNoise

        // Update step
        let kalmanGain = predictedError / (predictedError + measurementNoise)
        let newEstimate = predictedEstimate + kalmanGain * (measurement - predictedEstimate)

        // Update velocity estimate
        if timestamp > 0 && lastTimestamp > 0 {
            let dt = timestamp - lastTimestamp
            if dt > 0 && dt < 5.0 {
                let newVelocity = (newEstimate - estimate) / dt
                velocity = velocity * 0.7 + newVelocity * 0.3  // smooth velocity
            }
        }

        estimate = newEstimate
        errorCovariance = (1.0 - kalmanGain) * predictedError
        lastTimestamp = timestamp

        return estimate
    }

    func reset() {
        initialized = false
        estimate = 0
        errorCovariance = 1
        velocity = 0
        lastTimestamp = 0
        consecutiveRejects = 0
    }
}
