package com.ideals.arnav.location

/**
 * Simple 1D Kalman filter for GPS coordinate smoothing.
 * Applied independently to latitude and longitude.
 */
class KalmanFilter(
    private val processNoise: Double = 1e-5,
    private var measurementNoise: Double = 3e-5
) {
    private var estimate = 0.0
    private var errorCovariance = 1.0
    private var initialized = false

    fun update(measurement: Double, accuracy: Float = 5f): Double {
        // Derive measurement noise from GPS accuracy (meters → degrees approx)
        measurementNoise = (accuracy * 1e-5).coerceAtLeast(1e-6).toDouble()

        if (!initialized) {
            estimate = measurement
            errorCovariance = measurementNoise
            initialized = true
            return estimate
        }

        // Prediction step
        val predictedEstimate = estimate
        val predictedError = errorCovariance + processNoise

        // Update step
        val kalmanGain = predictedError / (predictedError + measurementNoise)
        estimate = predictedEstimate + kalmanGain * (measurement - predictedEstimate)
        errorCovariance = (1.0 - kalmanGain) * predictedError

        return estimate
    }

    fun reset() {
        initialized = false
        estimate = 0.0
        errorCovariance = 1.0
    }
}
