package com.ideals.arnav.ar.filament

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.android.UiHelper
import com.ideals.arnav.navigation.NavigationState
import com.ideals.arnav.navigation.NavigationViewModel

/**
 * SurfaceView that hosts Filament rendering with Choreographer-driven frame loop.
 *
 * Replaces ArGLSurfaceView. Same public API:
 *   - setNavigationState(state) — push state from Compose
 *   - onResume() / onPause() / onDestroy() — lifecycle
 *
 * Uses UiHelper for surface lifecycle management and Choreographer for vsync-aligned rendering.
 *
 * Initialization is deferred until both the native window AND dimensions are available,
 * since onNativeWindowChanged fires before onResized.
 */
class FilamentArSurfaceView(
    context: Context,
    private val viewModel: NavigationViewModel
) : SurfaceView(context) {

    companion object {
        private const val TAG = "FilamentArSurfaceView"
    }

    private val renderer = FilamentArRenderer(viewModel.arSessionManager, viewModel)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer = Choreographer.getInstance()
    private var filamentInitialized = false
    private var resumed = false
    private var arSessionCreated = false

    // Deferred init: we need both surface and dimensions
    private var pendingSurface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (filamentInitialized && resumed) {
                renderer.doFrame(frameTimeNanos)
                choreographer.postFrameCallback(this)
            }
        }
    }

    init {
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                Log.d(TAG, "Native window changed")
                if (!filamentInitialized) {
                    pendingSurface = surface
                    tryInitialize()
                } else {
                    // Surface recreated — need new swap chain
                    renderer.onSurfaceRecreated(surface)
                }
            }

            override fun onDetachedFromSurface() {
                Log.d(TAG, "Detached from surface")
                filamentInitialized = false
                pendingSurface = null
                choreographer.removeFrameCallback(frameCallback)
            }

            override fun onResized(width: Int, height: Int) {
                surfaceWidth = width
                surfaceHeight = height
                if (filamentInitialized) {
                    renderer.onSurfaceChanged(width, height)
                } else {
                    tryInitialize()
                }
            }
        }

        uiHelper.attachTo(this)
        Log.d(TAG, "FilamentArSurfaceView initialized")
    }

    /**
     * Initialize Filament once we have both a surface and non-zero dimensions.
     */
    private fun tryInitialize() {
        val surface = pendingSurface ?: return
        if (surfaceWidth == 0 || surfaceHeight == 0) return

        renderer.init(context, surface, surfaceWidth, surfaceHeight)
        filamentInitialized = true
        pendingSurface = null

        // Create ARCore session on first init
        if (!arSessionCreated) {
            renderer.createArSession(context)
            arSessionCreated = true
        }

        // Update display rotation
        val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        }
        renderer.setDisplayRotation(display?.rotation ?: 0)

        // Start frame loop if resumed
        if (resumed) {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /**
     * Push navigation state from Compose thread (single-writer, single-reader safe).
     */
    fun setNavigationState(state: NavigationState) {
        renderer.currentState = state
    }

    fun onResume() {
        resumed = true
        renderer.onResume()
        if (filamentInitialized) {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun onPause() {
        resumed = false
        choreographer.removeFrameCallback(frameCallback)
        renderer.onPause()
    }

    fun onDestroy() {
        resumed = false
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        renderer.destroy()
    }
}
