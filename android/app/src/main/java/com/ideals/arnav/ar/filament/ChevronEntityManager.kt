package com.ideals.arnav.ar.filament

import android.content.Context
import android.util.Log
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.TransformManager
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manages a pool of 3D arrow chevron entities in Filament.
 *
 * Geometry: extruded arrow head shape with FLOAT3 normals in TANGENTS attribute
 * (Filament auto-generates tangent frames from normals).
 * Material: turn_arrow.filamat — lit shader with rim glow, emissive pulse.
 *
 * Distance-based animation states (inspired by TurnArrowSystem):
 *   > 20m  : fade out (alpha → 0)
 *   5–20m  : normal — wave animation, green color
 *   2–5m   : attention — bounce + pulse + yellow tint
 *   < 2m   : fade out as user passes
 *
 * Entity pool: 40 entities created at init. Show/hide via scene.addEntity/removeEntity.
 * Render priority 7.
 */
class ChevronEntityManager(
    private val context: Context,
    private val engine: Engine
) {

    companion object {
        private const val TAG = "ChevronEntityMgr"
        private const val POOL_SIZE = 40

        // 3D arrow dimensions
        private const val ARROW_LENGTH = 0.50f
        private const val ARROW_HALF_WIDTH = 0.22f
        private const val ARROW_NOTCH = 0.12f
        private const val ARROW_HEIGHT = 0.06f

        // Color constants
        private const val COLOR_NORMAL_R = 0.0f
        private const val COLOR_NORMAL_G = 0.90f
        private const val COLOR_NORMAL_B = 0.50f

        private const val COLOR_ATTENTION_R = 1.0f
        private const val COLOR_ATTENTION_G = 0.85f
        private const val COLOR_ATTENTION_B = 0.1f

        private const val COLOR_CONFIRM_R = 0.2f
        private const val COLOR_CONFIRM_G = 1.0f
        private const val COLOR_CONFIRM_B = 0.3f

        private const val RIM_STRENGTH = 1.8f

        // Distance thresholds (meters)
        private const val DIST_FAR = 20f
        private const val DIST_ATTENTION = 5f
        private const val DIST_NEAR = 2f

        // Vertex format: pos(3) only = 3 floats, stride 12 bytes (unlit needs no normals)
        private const val FLOAT_STRIDE = 3
        private const val BYTE_STRIDE = FLOAT_STRIDE * 4
    }

    private var material: Material? = null
    private var sharedVertexBuffer: VertexBuffer? = null
    private var sharedIndexBuffer: IndexBuffer? = null

    private val entities = IntArray(POOL_SIZE)
    private val materialInstances = arrayOfNulls<MaterialInstance>(POOL_SIZE)
    private val inScene = BooleanArray(POOL_SIZE)

    private var initialized = false
    private lateinit var scene: Scene
    private lateinit var transformManager: TransformManager

    fun init(scene: Scene, dummyDepthTexture: Texture) {
        this.scene = scene
        this.transformManager = engine.transformManager

        buildMaterial()
        buildSharedGeometry()
        buildEntityPool(dummyDepthTexture)
        initialized = true
        Log.d(TAG, "3D arrow entity pool initialized ($POOL_SIZE entities)")
    }

    private fun buildMaterial() {
        material = MaterialLoader.load(context, engine, "materials/turn_arrow.filamat")
    }

    /**
     * Build a 3D extruded arrow head with FLOAT3 normals in the TANGENTS attribute.
     * Filament auto-generates tangent frames from normals.
     *
     * Arrow outline (XZ plane, pointing in -Z):
     *           tip
     *          / \
     *    leftW     rightW
     *        \   /
     *    leftN \ / rightN
     *        notch
     *
     * Extruded in Y for thickness. Each face has flat normals.
     * Single interleaved buffer: pos(3) + normal(3), stride 24.
     */
    private fun buildSharedGeometry() {
        val halfLen = ARROW_LENGTH / 2f
        val h = ARROW_HEIGHT / 2f
        val hw = ARROW_HALF_WIDTH

        // Arrow outline points (XZ plane)
        val tipX = 0f; val tipZ = -halfLen
        val lwX = -hw; val lwZ = halfLen * 0.15f
        val rwX = hw; val rwZ = halfLen * 0.15f
        val lnX = -hw * 0.30f; val lnZ = halfLen * 0.6f
        val rnX = hw * 0.30f; val rnZ = halfLen * 0.6f
        val ntX = 0f; val ntZ = halfLen * 0.35f

        val vertexData = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        var vi: Short = 0

        fun addTriangle(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float
        ) {
            // 3 vertices: pos(3) only — unlit material needs no normals
            vertexData.addAll(listOf(x0, y0, z0))
            vertexData.addAll(listOf(x1, y1, z1))
            vertexData.addAll(listOf(x2, y2, z2))
            indices.add(vi); indices.add((vi + 1).toShort()); indices.add((vi + 2).toShort())
            vi = (vi + 3).toShort()
        }

        fun addQuad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float
        ) {
            addTriangle(x0, y0, z0, x1, y1, z1, x2, y2, z2)
            addTriangle(x0, y0, z0, x2, y2, z2, x3, y3, z3)
        }

        // Arrow outline in order
        val outline = arrayOf(
            floatArrayOf(tipX, tipZ),
            floatArrayOf(lwX, lwZ),
            floatArrayOf(lnX, lnZ),
            floatArrayOf(ntX, ntZ),
            floatArrayOf(rnX, rnZ),
            floatArrayOf(rwX, rwZ)
        )
        val n = outline.size

        // --- TOP FACE (y = +h) ---
        for (j in 1 until n - 1) {
            addTriangle(
                outline[0][0], h, outline[0][1],
                outline[j][0], h, outline[j][1],
                outline[j + 1][0], h, outline[j + 1][1]
            )
        }

        // --- BOTTOM FACE (y = -h, reversed winding) ---
        for (j in 1 until n - 1) {
            addTriangle(
                outline[0][0], -h, outline[0][1],
                outline[j + 1][0], -h, outline[j + 1][1],
                outline[j][0], -h, outline[j][1]
            )
        }

        // --- SIDE FACES ---
        for (j in 0 until n) {
            val cur = outline[j]
            val nxt = outline[(j + 1) % n]
            addQuad(
                cur[0], h, cur[1],
                nxt[0], h, nxt[1],
                nxt[0], -h, nxt[1],
                cur[0], -h, cur[1]
            )
        }

        val vertexCount = vi.toInt()
        val indexCount = indices.size

        // Single interleaved buffer: pos(3) + normal(3), stride 24
        val buf = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { vertexData.forEach { put(it) }; flip() }

        sharedVertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, BYTE_STRIDE)
            .build(engine)
        sharedVertexBuffer!!.setBufferAt(engine, 0, buf)

        val ib = ByteBuffer.allocateDirect(indexCount * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { indices.forEach { put(it) }; flip() }

        sharedIndexBuffer = IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        sharedIndexBuffer!!.setBuffer(engine, ib)

        Log.d(TAG, "3D arrow geometry: $vertexCount vertices, $indexCount indices")
    }

    private fun buildEntityPool(dummyDepthTexture: Texture) {
        val em = EntityManager.get()
        for (i in 0 until POOL_SIZE) {
            entities[i] = em.create()
            materialInstances[i] = material!!.createInstance()

            RenderableManager.Builder(1)
                .boundingBox(Box(0f, 0f, 0f, 1f, 1f, 1f))
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                    sharedVertexBuffer!!, sharedIndexBuffer!!)
                .material(0, materialInstances[i]!!)
                .culling(false)
                .receiveShadows(false)
                .castShadows(false)
                .priority(7)
                .build(engine, entities[i])

            transformManager.create(entities[i])
            inScene[i] = false

            // Default depth occlusion params — dummy texture makes everything "visible"
            materialInstances[i]!!.setParameter("depthTexture", dummyDepthTexture, depthSampler)
            materialInstances[i]!!.setParameter("occlusionAlpha", 0.15f)
            materialInstances[i]!!.setParameter("depthTolerance", 0.15f)
            materialInstances[i]!!.setParameter("screenResolution", 1f, 1f)
        }
    }

    private val depthSampler = TextureSampler(
        TextureSampler.MinFilter.NEAREST,
        TextureSampler.MagFilter.NEAREST,
        TextureSampler.WrapMode.CLAMP_TO_EDGE
    )

    fun setDepthTexture(texture: Texture, width: Int, height: Int) {
        for (i in 0 until POOL_SIZE) {
            materialInstances[i]?.setParameter("depthTexture", texture, depthSampler)
            materialInstances[i]?.setParameter("screenResolution", width.toFloat(), height.toFloat())
        }
    }

    fun update(
        positions: Array<FloatArray>,
        rotationsY: FloatArray,
        scales: FloatArray,
        waveAlphas: FloatArray,
        count: Int,
        timeSeconds: Float = 0f,
        distances: FloatArray = FloatArray(0)
    ) {
        if (!initialized) return

        val activeCount = count.coerceAtMost(POOL_SIZE)

        for (i in 0 until activeCount) {
            val pos = positions[i]
            val wave = waveAlphas[i]
            val rotY = rotationsY[i]
            val dist = if (i < distances.size) distances[i] else Float.MAX_VALUE

            // Distance-based state
            val cr: Float; val cg: Float; val cb: Float
            val alpha: Float
            val finalScale: Float
            val yOffset: Float
            val rimStr: Float

            when {
                dist > DIST_FAR -> {
                    // Far away: fade out
                    val fadeT = ((dist - DIST_FAR) / 10f).coerceIn(0f, 1f)
                    alpha = wave * (1f - fadeT)
                    cr = COLOR_NORMAL_R; cg = COLOR_NORMAL_G; cb = COLOR_NORMAL_B
                    finalScale = scales[i]
                    yOffset = 0f
                    rimStr = RIM_STRENGTH * alpha
                }
                dist > DIST_ATTENTION -> {
                    // Normal range (5–20m): wave animation, green
                    alpha = wave
                    cr = COLOR_NORMAL_R; cg = COLOR_NORMAL_G; cb = COLOR_NORMAL_B
                    finalScale = scales[i]
                    yOffset = 0f
                    rimStr = RIM_STRENGTH * wave
                }
                dist > DIST_NEAR -> {
                    // Attention range (2–5m): bounce + pulse + yellow
                    val bounce = abs(sin(timeSeconds * 4f)) * 0.12f
                    val pulse = 1f + 0.1f * sin(timeSeconds * 6f)
                    alpha = 1f
                    cr = COLOR_ATTENTION_R; cg = COLOR_ATTENTION_G; cb = COLOR_ATTENTION_B
                    finalScale = scales[i] * pulse
                    yOffset = bounce
                    rimStr = RIM_STRENGTH * 1.5f
                }
                else -> {
                    // Very close (< 2m): fade out, lerp to confirm green
                    val fadeT = (1f - dist / DIST_NEAR).coerceIn(0f, 1f)
                    alpha = 1f - fadeT
                    cr = lerp(COLOR_ATTENTION_R, COLOR_CONFIRM_R, fadeT)
                    cg = lerp(COLOR_ATTENTION_G, COLOR_CONFIRM_G, fadeT)
                    cb = lerp(COLOR_ATTENTION_B, COLOR_CONFIRM_B, fadeT)
                    finalScale = scales[i] * (1f + fadeT * 0.15f)
                    yOffset = 0.2f * fadeT
                    rimStr = RIM_STRENGTH * alpha
                }
            }

            setTransform(i, pos[0], pos[1] + yOffset, pos[2], rotY, finalScale)

            materialInstances[i]?.setParameter("baseColor", cr, cg, cb, alpha)
            materialInstances[i]?.setParameter("time", timeSeconds)
            materialInstances[i]?.setParameter("rimStrength", rimStr)

            if (!inScene[i]) {
                scene.addEntity(entities[i])
                inScene[i] = true
            }
        }

        for (i in activeCount until POOL_SIZE) {
            if (inScene[i]) {
                scene.removeEntity(entities[i])
                inScene[i] = false
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private val tempMatrix = FloatArray(16)

    private fun setTransform(index: Int, tx: Float, ty: Float, tz: Float, rotYDeg: Float, scale: Float) {
        val radY = Math.toRadians(rotYDeg.toDouble())
        val cosY = cos(radY).toFloat() * scale
        val sinY = sin(radY).toFloat() * scale

        tempMatrix[0] = cosY;    tempMatrix[4] = 0f;     tempMatrix[8]  = sinY;   tempMatrix[12] = tx
        tempMatrix[1] = 0f;      tempMatrix[5] = scale;  tempMatrix[9]  = 0f;     tempMatrix[13] = ty
        tempMatrix[2] = -sinY;   tempMatrix[6] = 0f;     tempMatrix[10] = cosY;   tempMatrix[14] = tz
        tempMatrix[3] = 0f;      tempMatrix[7] = 0f;     tempMatrix[11] = 0f;     tempMatrix[15] = 1f

        val ti = transformManager.getInstance(entities[index])
        if (ti != 0) {
            transformManager.setTransform(ti, tempMatrix)
        }
    }

    fun destroy() {
        if (!initialized) return
        val em = EntityManager.get()
        val rm = engine.renderableManager
        for (i in 0 until POOL_SIZE) {
            if (inScene[i]) {
                scene.removeEntity(entities[i])
            }
            rm.destroy(entities[i])
            transformManager.destroy(entities[i])
            em.destroy(entities[i])
            materialInstances[i]?.let { engine.destroyMaterialInstance(it) }
        }
        material?.let { engine.destroyMaterial(it) }
        sharedVertexBuffer?.let { engine.destroyVertexBuffer(it) }
        sharedIndexBuffer?.let { engine.destroyIndexBuffer(it) }
        initialized = false
    }
}
