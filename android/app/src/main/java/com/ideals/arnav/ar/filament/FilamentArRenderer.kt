package com.ideals.arnav.ar.filament

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.Filament
import com.google.ar.core.TrackingState
import com.ideals.arnav.ar.ArCoreHeading
import com.ideals.arnav.ar.ArrowMeshFactory
import com.ideals.arnav.ar.ArSessionManager
import com.ideals.arnav.geo.CoordinateConverter
import com.ideals.arnav.navigation.NavigationState
import com.ideals.arnav.navigation.NavigationViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filament rendering engine lifecycle and AR frame orchestration.
 *
 * Owns: Engine, Renderer, Scene, View, Camera, SwapChain.
 * Delegates: camera background to CameraBackgroundManager, chevrons to ChevronEntityManager,
 *            path rendering to NavPathManager.
 * Syncs: ARCore camera matrices to Filament camera each frame.
 *
 * All materials are pre-compiled (.filamat) and loaded from assets — no runtime
 * MaterialBuilder compilation needed.
 */
class FilamentArRenderer(
    private val sessionManager: ArSessionManager,
    private val viewModel: NavigationViewModel
) {

    companion object {
        private const val TAG = "FilamentArRenderer"
        private const val CHEVRON_SPACING = 2.0f
        private const val ANIM_SPEED = 1.2f
        private const val MAX_RENDER_DIST = 30f
        private const val MAX_RENDER_DIST_SQ = MAX_RENDER_DIST * MAX_RENDER_DIST
    }

    // Filament core objects
    lateinit var engine: Engine
        private set
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var filamentView: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Int = 0
    private var swapChain: SwapChain? = null

    // Managers
    private var cameraBackground: CameraBackgroundManager? = null
    private var chevronManager: ChevronEntityManager? = null
    private var navPathManager: NavPathManager? = null
    private var depthManager: DepthTextureManager? = null

    // Minimal EGL context for ARCore (it requires a current GL context for session.update())
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var arCoreTextureId = 0

    // State
    @Volatile
    var currentState: NavigationState = NavigationState()
    private var initialized = false
    private var displayRotation = 0
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Per-frame reusable arrays
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // Chevron sampling arrays
    private val maxChevrons = 40
    private val chevronOutX = FloatArray(maxChevrons)
    private val chevronOutZ = FloatArray(maxChevrons)
    private val chevronOutRotY = FloatArray(maxChevrons)
    private val chevronOutSegIdx = IntArray(maxChevrons)
    private val chevronOutDist = FloatArray(maxChevrons)

    // Chevron draw arrays
    private val chevronPositions = Array(maxChevrons) { FloatArray(3) }
    private val chevronRotations = FloatArray(maxChevrons)
    private val chevronScales = FloatArray(maxChevrons)
    private val chevronWaveAlphas = FloatArray(maxChevrons)
    private val chevronDistances = FloatArray(maxChevrons)

    // GPS→AR transform matrix (reused per frame)
    private val gpsToArTransform = FloatArray(16)

    /**
     * Initialize the Filament engine and all rendering resources.
     * Must be called after the surface is available.
     */
    fun init(context: Context, surface: Surface, width: Int, height: Int) {
        // Load Filament native libraries
        Filament.init()

        engine = Engine.create()
        renderer = engine.createRenderer().apply {
            // Transparent clear so the camera background view shows through
            clearOptions = clearOptions.apply {
                clear = true
                clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            }
        }
        scene = engine.createScene()

        // Create camera
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)

        // Create main AR view (translucent so camera background shows through)
        filamentView = engine.createView().apply {
            this.camera = this@FilamentArRenderer.camera
            this.scene = this@FilamentArRenderer.scene
            viewport = Viewport(0, 0, width, height)
            blendMode = View.BlendMode.TRANSLUCENT

            // Disable post-processing for TRANSLUCENT views — bloom corrupts
            // the alpha channel, causing the entire view to become opaque black
            // or fully transparent. Glow effects are handled in materials instead.
            isPostProcessingEnabled = false
        }

        // Create swap chain with transparent flag for TRANSLUCENT view compositing
        swapChain = engine.createSwapChain(surface, SwapChainFlags.CONFIG_TRANSPARENT)

        viewportWidth = width
        viewportHeight = height

        // Initialize depth manager first so dummy texture is available
        depthManager = DepthTextureManager(engine)

        // Initialize managers with pre-compiled materials
        cameraBackground = CameraBackgroundManager(context, engine).also {
            it.init(width, height)
        }
        chevronManager = ChevronEntityManager(context, engine).also {
            it.init(scene!!, depthManager!!.dummyTexture)
        }
        navPathManager = NavPathManager(context, engine).also {
            it.init(scene!!, depthManager!!.dummyTexture)
        }

        initialized = true
        Log.d(TAG, "Filament renderer initialized (${width}x${height})")
    }

    fun createArSession(context: Context) {
        // ARCore requires a current EGL context for session.update()
        initEglContext()

        sessionManager.createSession(context)

        // Give ARCore the OES texture
        sessionManager.session?.setCameraTextureName(arCoreTextureId)

        sessionManager.resumeSession()
        Log.d(TAG, "ARCore session created (OES texture=$arCoreTextureId)")
    }

    /**
     * Create a minimal EGL context and OES texture for ARCore.
     * The OES texture is used both by ARCore and by the camera background material.
     */
    private fun initEglContext() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(eglDisplay, null, 0, null, 0)

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0]!!, surfaceAttribs, 0)

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        // Create OES texture for ARCore
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        arCoreTextureId = texIds[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, arCoreTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        Log.d(TAG, "EGL context created for ARCore (texture=$arCoreTextureId)")
    }

    /**
     * Called when the surface size changes.
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        filamentView?.viewport = Viewport(0, 0, width, height)
        cameraBackground?.onSurfaceChanged(width, height)
        sessionManager.session?.setDisplayGeometry(displayRotation, width, height)
    }

    /**
     * Called when the native surface is recreated (e.g., after pause/resume).
     * Destroys old swap chain and creates a new one.
     */
    fun onSurfaceRecreated(surface: Surface) {
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface, SwapChainFlags.CONFIG_TRANSPARENT)
    }

    /**
     * Main render loop — called from Choreographer.
     */
    fun doFrame(frameTimeNanos: Long) {
        if (!initialized) return

        // Make EGL context current for ARCore session.update()
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        // 1. Update ARCore
        val frame = sessionManager.update() ?: return
        val arCamera = frame.camera

        // 2. Update depth texture for occlusion
        depthManager?.update(frame)?.let { depthTex ->
            val dims = frame.camera.imageIntrinsics.imageDimensions
            navPathManager?.setDepthTexture(depthTex, dims[0], dims[1])
            chevronManager?.setDepthTexture(depthTex, dims[0], dims[1])
        }

        // 3. Upload camera background + update UVs for display rotation
        cameraBackground?.updateDisplayUvs(frame, displayRotation)
        cameraBackground?.updateFromFrame(frame)

        // 4. Sync ARCore camera → Filament camera
        syncCamera(arCamera)

        // 5. Update chevrons and path if tracking
        val timeSeconds = frame.timestamp / 1_000_000_000.0f
        if (arCamera.trackingState == TrackingState.TRACKING) {
            updateChevrons(frame, arCamera, timeSeconds)
            updateNavPath(arCamera, timeSeconds)
        } else {
            chevronManager?.update(chevronPositions, chevronRotations, chevronScales, chevronWaveAlphas, 0)
            navPathManager?.update(null, timeSeconds, gpsToArTransform)
        }

        // 6. Render: background view first, then main AR view on top
        val r = renderer ?: return
        val sc = swapChain ?: return
        val v = filamentView ?: return
        if (r.beginFrame(sc, frameTimeNanos)) {
            val bgView = cameraBackground?.getView()
            if (bgView != null) {
                r.render(bgView)
            }
            r.render(v)
            r.endFrame()
        }
    }

    private fun syncCamera(arCamera: com.google.ar.core.Camera) {
        val cam = camera ?: return

        // Projection matrix (float[] → double[])
        arCamera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)
        val projDouble = DoubleArray(16) { projMatrix[it].toDouble() }
        cam.setCustomProjection(projDouble, 0.1, 100.0)

        // View matrix → invert to get model matrix (camera position in world)
        arCamera.getViewMatrix(viewMatrix, 0)
        Matrix.invertM(modelMatrix, 0, viewMatrix, 0)

        // Filament's setModelMatrix expects column-major float array
        cam.setModelMatrix(modelMatrix)
    }

    private fun updateChevrons(frame: com.google.ar.core.Frame, arCamera: com.google.ar.core.Camera, timeSeconds: Float) {
        val state = currentState
        val camPose = arCamera.pose
        val camX = camPose.tx()
        val camY = camPose.ty()
        val camZ = camPose.tz()

        // Feed ARCore yaw for heading calibration
        val arCoreYaw = ArCoreHeading.extractYawDegrees(camPose)
        viewModel.latestArCoreYaw = arCoreYaw
        viewModel.arCoreYawReady = true

        // No route? Hide chevrons.
        if (state.routeWorldPositions.size < 2 || !CoordinateConverter.originSet) {
            chevronManager?.update(chevronPositions, chevronRotations, chevronScales, chevronWaveAlphas, 0)
            return
        }

        // GPS→local user position (smoothed)
        val userLocal = CoordinateConverter.gpsToLocal(state.smoothedLat, state.smoothedLng)
        val userX = userLocal[0]
        val userZ = userLocal[2]

        // Effective heading: ARCore yaw + calibration offset
        val effectiveHeading = if (state.calibrationInitialized) {
            var h = (arCoreYaw + state.calibrationAngle) % 360.0
            if (h < 0.0) h += 360.0
            h
        } else {
            state.heading
        }
        val headRad = Math.toRadians(effectiveHeading)
        val cosH = cos(headRad).toFloat()
        val sinH = sin(headRad).toFloat()
        val groundY = camY - 1.5f

        // GPS→AR transform
        fun gpsToAR(gpsX: Float, gpsZ: Float, y: Float): FloatArray {
            val dx = gpsX - userX
            val dz = gpsZ - userZ
            return floatArrayOf(
                dx * cosH + dz * sinH + camX,
                y,
                -dx * sinH + dz * cosH + camZ
            )
        }

        // Sample chevrons starting from user's current position along the route
        val chevronSegments = state.cachedChevronSegments ?: return
        val animPhase = ((timeSeconds * ANIM_SPEED) % CHEVRON_SPACING)
        val currentSegment = state.currentSegment

        // Start sampling from user's distance along route (not from route start)
        val userDistAlong = state.distanceAlongRoute.coerceAtLeast(0f)
        val sampledCount = ArrowMeshFactory.sampleChevrons(
            chevronSegments, CHEVRON_SPACING, userDistAlong + animPhase,
            chevronOutX, chevronOutZ, chevronOutRotY, chevronOutSegIdx, chevronOutDist,
            maxChevrons
        )

        // Debug: log once per second
        val debugFrame = (timeSeconds % 1.0f) < 0.02f
        if (debugFrame) {
            Log.d(TAG, "chevrons: sampled=$sampledCount userLocal=(${userX},${userZ}) " +
                "smoothed=(${state.smoothedLat},${state.smoothedLng}) " +
                "heading=$effectiveHeading calibInit=${state.calibrationInitialized} " +
                "phase=${state.phase} routePts=${state.routeWorldPositions.size}")
        }

        var drawCount = 0
        var skipSeg = 0; var skipDist = 0; var skipNear = 0
        for (i in 0 until sampledCount) {
            if (drawCount >= maxChevrons) break
            if (chevronOutSegIdx[i] < currentSegment) { skipSeg++; continue }

            val dx = chevronOutX[i] - userX
            val dz = chevronOutZ[i] - userZ
            val distSq = dx * dx + dz * dz
            if (distSq > MAX_RENDER_DIST_SQ) { skipDist++; continue }

            val dist = sqrt(distSq)
            if (dist < 1.5f) { skipNear++; continue }

            // AR position
            val arPos = gpsToAR(chevronOutX[i], chevronOutZ[i], groundY + ArrowMeshFactory.Y_OFFSET)
            chevronPositions[drawCount][0] = arPos[0]
            chevronPositions[drawCount][1] = arPos[1]
            chevronPositions[drawCount][2] = arPos[2]

            // Rotation: route direction rotated by heading
            chevronRotations[drawCount] = chevronOutRotY[i] - effectiveHeading.toFloat()

            // Store distance for animation states
            chevronDistances[drawCount] = dist

            // Distance-based scale
            val t = ((dist - 2f) / 20f).coerceIn(0f, 1f)
            chevronScales[drawCount] = 1.3f + (0.9f - 1.3f) * t

            // Wave animation + distance fade
            val wave = 0.5f + 0.5f * sin(chevronOutDist[i] * 0.8f - timeSeconds * 2.5f)
            val distFade = if (dist < 20f) 1f else 1f - ((dist - 20f) / 10f).coerceIn(0f, 1f)
            chevronWaveAlphas[drawCount] = wave * distFade

            drawCount++
        }

        if (debugFrame) {
            Log.d(TAG, "chevrons: draw=$drawCount skipSeg=$skipSeg skipDist=$skipDist skipNear=$skipNear")
        }

        chevronManager?.update(chevronPositions, chevronRotations, chevronScales, chevronWaveAlphas, drawCount, timeSeconds, chevronDistances)
    }

    private fun updateNavPath(arCamera: com.google.ar.core.Camera, timeSeconds: Float) {
        val state = currentState
        if (state.routeWorldPositions.size < 2 || !CoordinateConverter.originSet) {
            navPathManager?.update(null, timeSeconds, gpsToArTransform)
            return
        }

        val camPose = arCamera.pose
        val camX = camPose.tx()
        val camY = camPose.ty()
        val camZ = camPose.tz()

        val userLocal = CoordinateConverter.gpsToLocal(state.smoothedLat, state.smoothedLng)
        val userX = userLocal[0]
        val userZ = userLocal[2]

        val effectiveHeading = if (state.calibrationInitialized) {
            var h = (viewModel.latestArCoreYaw + state.calibrationAngle) % 360.0
            if (h < 0.0) h += 360.0
            h
        } else {
            state.heading
        }
        val headRad = Math.toRadians(effectiveHeading)
        val cosH = cos(headRad).toFloat()
        val sinH = sin(headRad).toFloat()
        val groundY = camY - 1.5f + ArrowMeshFactory.PATH_Y_OFFSET

        // Build GPS-local → AR world transform matrix (column-major 4x4)
        // GPS point (gx, gy, gz) → AR: rotated by heading, offset by (cam - user)
        // dx = gx - userX, dz = gz - userZ
        // arX = dx*cosH + dz*sinH + camX
        // arY = groundY (we use gy=0 for path)
        // arZ = -dx*sinH + dz*cosH + camZ
        gpsToArTransform[0]  = cosH;   gpsToArTransform[4]  = 0f;  gpsToArTransform[8]  = sinH;   gpsToArTransform[12] = -userX * cosH - userZ * sinH + camX
        gpsToArTransform[1]  = 0f;     gpsToArTransform[5]  = 0f;  gpsToArTransform[9]  = 0f;     gpsToArTransform[13] = groundY
        gpsToArTransform[2]  = -sinH;  gpsToArTransform[6]  = 0f;  gpsToArTransform[10] = cosH;   gpsToArTransform[14] = userX * sinH - userZ * cosH + camZ
        gpsToArTransform[3]  = 0f;     gpsToArTransform[7]  = 0f;  gpsToArTransform[11] = 0f;     gpsToArTransform[15] = 1f

        navPathManager?.update(state.cachedChevronSegments, timeSeconds, gpsToArTransform)
    }

    fun setDisplayRotation(rotation: Int) {
        displayRotation = rotation
        sessionManager.session?.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
    }

    fun onResume() {
        sessionManager.resumeSession()
    }

    fun onPause() {
        sessionManager.pauseSession()
    }

    fun destroy() {
        if (!initialized) return
        initialized = false

        sessionManager.close()

        depthManager?.destroy()
        navPathManager?.destroy()
        chevronManager?.destroy()
        cameraBackground?.destroy()

        swapChain?.let { engine.destroySwapChain(it) }
        filamentView?.let { engine.destroyView(it) }
        scene?.let { engine.destroyScene(it) }
        camera?.let { engine.destroyCameraComponent(cameraEntity) }
        EntityManager.get().destroy(cameraEntity)

        engine.destroy()

        // Clean up EGL context
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }

        Log.d(TAG, "Filament renderer destroyed")
    }
}
