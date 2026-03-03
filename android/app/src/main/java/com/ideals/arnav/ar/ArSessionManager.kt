package com.ideals.arnav.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException

/**
 * Manages ARCore session lifecycle for direct OpenGL rendering.
 * Owns the Session and provides create/resume/pause/close/update methods
 * called from the GL thread.
 */
class ArSessionManager {

    companion object {
        private const val TAG = "ArSessionManager"
    }

    var session: Session? = null
        private set

    var isTracking = false
        private set

    /**
     * Create and configure an ARCore session. Call from GL thread (onSurfaceCreated).
     */
    fun createSession(context: Context): Session? {
        return try {
            val s = Session(context)
            val config = Config(s).apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.DISABLED
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                focusMode = Config.FocusMode.AUTO
                depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                    Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            }
            s.configure(config)
            session = s
            Log.d(TAG, "ARCore session created")
            s
        } catch (e: UnavailableException) {
            Log.e(TAG, "Failed to create ARCore session: ${e.message}")
            null
        }
    }

    fun resumeSession() {
        try {
            session?.resume()
            Log.d(TAG, "ARCore session resumed")
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available on resume: ${e.message}")
        }
    }

    fun pauseSession() {
        session?.pause()
        Log.d(TAG, "ARCore session paused")
    }

    /**
     * Update the session and return the latest Frame, or null if not ready.
     * Call from GL thread (onDrawFrame).
     */
    fun update(): Frame? {
        val s = session ?: return null
        return try {
            val frame = s.update()
            updateTrackingState(frame.camera.trackingState)
            frame
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during update: ${e.message}")
            null
        }
    }

    fun close() {
        session?.close()
        session = null
        Log.d(TAG, "ARCore session closed")
    }

    fun checkAvailability(context: Context): Boolean {
        return try {
            val availability = com.google.ar.core.ArCoreApk.getInstance()
                .checkAvailability(context)
            availability.isSupported
        } catch (e: UnavailableException) {
            Log.e(TAG, "ARCore not available: ${e.message}")
            false
        }
    }

    fun updateTrackingState(state: TrackingState) {
        isTracking = state == TrackingState.TRACKING
    }
}
