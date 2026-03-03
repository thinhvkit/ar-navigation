package com.ideals.arnav.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

data class LocationUpdate(
    val lat: Double,
    val lng: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val heading: Double,
    val gpsStale: Boolean = false
)

class LocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val latFilter = KalmanFilter()
    private val lngFilter = KalmanFilter()

    private var smoothedHeading: Double = -1.0 // -1 means uninitialized

    // Adaptive interval state
    private var currentIntervalMs = 1000L
    private var activeCallback: LocationCallback? = null

    companion object {
        private const val HEADING_SMOOTHING_MIN = 0.1
        private const val HEADING_SMOOTHING_MAX = 0.4
        private const val SPEED_HEADING_GATE = 0.5f // m/s — below this, GPS bearing is unreliable
        private const val SPEED_SMOOTHING_RANGE = 2.0f // m/s — full smoothing factor reached here
        private const val GPS_STALE_TIMEOUT_MS = 5000L
    }

    /**
     * Change the GPS polling interval. Only rebuilds the location request if the interval differs.
     * Call from ViewModel based on navigation state.
     */
    @SuppressLint("MissingPermission")
    fun setUpdateInterval(intervalMs: Long) {
        if (intervalMs == currentIntervalMs) return
        val callback = activeCallback ?: return
        currentIntervalMs = intervalMs

        // Rebuild and re-register location request with new interval
        val newRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        )
            .setMinUpdateDistanceMeters(0.5f)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        fusedClient.removeLocationUpdates(callback)
        fusedClient.requestLocationUpdates(newRequest, callback, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<LocationUpdate> = callbackFlow {
        var lastEmittedUpdate: LocationUpdate? = null
        var staleTimerJob: Job? = null

        fun resetStaleTimer() {
            staleTimerJob?.cancel()
            staleTimerJob = launch {
                delay(GPS_STALE_TIMEOUT_MS)
                // No GPS update received for 5s — emit stale signal with last known position
                lastEmittedUpdate?.let { last ->
                    trySend(last.copy(gpsStale = true))
                }
            }
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            currentIntervalMs
        )
            .setMinUpdateDistanceMeters(0.5f)
            .setMinUpdateIntervalMillis(500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                resetStaleTimer()

                // Apply Kalman filter to smooth GPS coordinates
                val smoothLat = latFilter.update(loc.latitude, loc.accuracy)
                val smoothLng = lngFilter.update(loc.longitude, loc.accuracy)

                // Speed-gated heading: GPS bearing is unreliable at low speeds
                val heading = if (loc.speed < SPEED_HEADING_GATE && smoothedHeading >= 0) {
                    // Keep previous smoothed heading when nearly stationary
                    smoothedHeading
                } else {
                    val rawHeading = loc.bearing.toDouble()
                    if (smoothedHeading < 0) {
                        smoothedHeading = rawHeading
                        rawHeading
                    } else {
                        // Adaptive smoothing: more smoothing when slow, less when walking briskly
                        val t = ((loc.speed / SPEED_SMOOTHING_RANGE).coerceIn(0f, 1f)).toDouble()
                        val factor = HEADING_SMOOTHING_MIN + t * (HEADING_SMOOTHING_MAX - HEADING_SMOOTHING_MIN)
                        var delta = rawHeading - smoothedHeading
                        // Wrap delta to [-180, 180]
                        if (delta > 180) delta -= 360
                        if (delta < -180) delta += 360
                        smoothedHeading += delta * factor
                        // Normalize to [0, 360)
                        smoothedHeading = ((smoothedHeading % 360) + 360) % 360
                        smoothedHeading
                    }
                }

                val update = LocationUpdate(
                    lat = smoothLat,
                    lng = smoothLng,
                    altitude = loc.altitude,
                    accuracy = loc.accuracy,
                    speed = loc.speed,
                    heading = heading
                )
                lastEmittedUpdate = update
                trySend(update)
            }
        }

        activeCallback = callback
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        resetStaleTimer()

        awaitClose {
            staleTimerJob?.cancel()
            activeCallback = null
            fusedClient.removeLocationUpdates(callback)
            latFilter.reset()
            lngFilter.reset()
            smoothedHeading = -1.0
        }
    }
}
