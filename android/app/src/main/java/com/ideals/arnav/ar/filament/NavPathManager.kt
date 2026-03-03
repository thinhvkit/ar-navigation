package com.ideals.arnav.ar.filament

import android.content.Context
import android.util.Log
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
import com.google.android.filament.Box
import com.ideals.arnav.ar.ArrowMeshFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Renders the navigation route path as a strip mesh with two material layers:
 * 1. Glow layer (additive blending) — bright, flowing energy effect
 * 2. Occluded layer (transparent) — visible through walls as a ghost outline
 *
 * Vertices are stored in GPS-local coordinates. The GPS→AR world transform is
 * applied each frame via the entity's TransformManager, keeping the path
 * consistent with the per-frame chevron positions.
 */
class NavPathManager(
    private val context: Context,
    private val engine: Engine
) {

    companion object {
        private const val TAG = "NavPathManager"
        private const val PATH_HALF_WIDTH = 0.5f
    }

    private var glowMaterial: Material? = null
    private var occludedMaterial: Material? = null
    private var glowMaterialInstance: MaterialInstance? = null
    private var occludedMaterialInstance: MaterialInstance? = null

    private var glowVertexBuffer: VertexBuffer? = null
    private var glowIndexBuffer: IndexBuffer? = null
    private var occludedVertexBuffer: VertexBuffer? = null
    private var occludedIndexBuffer: IndexBuffer? = null

    private var glowEntity: Int = 0
    private var occludedEntity: Int = 0
    private var glowInScene = false
    private var occludedInScene = false

    private var initialized = false
    private lateinit var scene: Scene
    private lateinit var transformManager: TransformManager

    // Track current route to detect changes
    private var currentRouteHash = 0
    private var pathLength = 0f

    fun init(scene: Scene, dummyDepthTexture: Texture) {
        this.scene = scene
        this.transformManager = engine.transformManager

        glowMaterial = MaterialLoader.load(context, engine, "materials/nav_path_glow.filamat")
        occludedMaterial = MaterialLoader.load(context, engine, "materials/nav_path_occluded.filamat")

        glowMaterialInstance = glowMaterial!!.createInstance()
        occludedMaterialInstance = occludedMaterial!!.createInstance()

        // Set default occluded material params — dummy texture makes everything "visible"
        occludedMaterialInstance!!.setParameter("depthTexture", dummyDepthTexture, depthSampler)
        occludedMaterialInstance!!.setParameter("dashFreq", 3.0f)
        occludedMaterialInstance!!.setParameter("edgeFade", 0.15f)
        occludedMaterialInstance!!.setParameter("color",
            0.0f, 0.5f, 0.9f, 0.5f)
        occludedMaterialInstance!!.setParameter("occlusionAlpha", 0.15f)
        occludedMaterialInstance!!.setParameter("depthTolerance", 0.15f)
        occludedMaterialInstance!!.setParameter("screenResolution", 1f, 1f)

        // Set default glow material params — blue/cyan to contrast with green arrows
        glowMaterialInstance!!.setParameter("colorNear",
            0.0f, 0.7f, 1.0f, 0.7f)
        glowMaterialInstance!!.setParameter("colorFar",
            0.0f, 0.4f, 0.8f, 0.4f)

        initialized = true
        Log.d(TAG, "NavPathManager initialized")
    }

    private val depthSampler = TextureSampler(
        TextureSampler.MinFilter.NEAREST,
        TextureSampler.MagFilter.NEAREST,
        TextureSampler.WrapMode.CLAMP_TO_EDGE
    )

    fun setDepthTexture(texture: Texture, width: Int, height: Int) {
        occludedMaterialInstance?.setParameter(
            "depthTexture", texture, depthSampler
        )
        occludedMaterialInstance?.setParameter(
            "screenResolution", width.toFloat(), height.toFloat()
        )
    }

    /**
     * Update the path mesh and material parameters.
     *
     * @param segments Pre-computed route segment data (null = no route)
     * @param timeSeconds Current animation time
     * @param transform 4x4 column-major transform mapping GPS-local → AR world coords
     */
    fun update(
        segments: ArrowMeshFactory.RouteSegmentData?,
        timeSeconds: Float,
        transform: FloatArray
    ) {
        if (!initialized) return

        if (segments == null || segments.segCount == 0) {
            hideAll()
            return
        }

        // Rebuild mesh if route changed (vertices in GPS-local space)
        val hash = System.identityHashCode(segments)
        if (hash != currentRouteHash) {
            rebuildMesh(segments)
            currentRouteHash = hash
        }

        // Apply GPS→AR transform each frame so path stays consistent with arrows
        applyTransform(glowEntity, transform)
        applyTransform(occludedEntity, transform)

        // Update animation params
        glowMaterialInstance?.setParameter("time", timeSeconds)
        glowMaterialInstance?.setParameter("speed", 1.5f)
        glowMaterialInstance?.setParameter("pathLength", pathLength)

        occludedMaterialInstance?.setParameter("time", timeSeconds)
        occludedMaterialInstance?.setParameter("speed", 1.0f)
    }

    private fun applyTransform(entity: Int, transform: FloatArray) {
        if (entity == 0) return
        val ti = transformManager.getInstance(entity)
        if (ti != 0) {
            transformManager.setTransform(ti, transform)
        }
    }

    /**
     * Build the strip mesh in GPS-local coordinates (no heading rotation).
     * The GPS→AR transform is applied via the entity transform each frame.
     */
    private fun rebuildMesh(segments: ArrowMeshFactory.RouteSegmentData) {
        // Remove old entities from scene
        hideAll()
        destroyMeshResources()

        val positions = segments.worldPositions
        if (positions.size < 2) return

        pathLength = segments.totalDist

        // Build strip: 2 vertices per route point (left + right edge)
        val vertCount = positions.size * 2
        val triCount = (positions.size - 1) * 2
        val idxCount = triCount * 3

        // 5 floats per vertex: pos(3) + uv(2), stride 20
        val vertexData = FloatArray(vertCount * 5)
        val indexData = ShortArray(idxCount)

        var cumDist = 0f
        for (i in positions.indices) {
            val wp = positions[i]
            // Get perpendicular direction for strip width
            val perpX: Float
            val perpZ: Float
            if (i < segments.segCount) {
                perpX = -segments.segDirZ[i]
                perpZ = segments.segDirX[i]
            } else if (i > 0) {
                perpX = -segments.segDirZ[i - 1]
                perpZ = segments.segDirX[i - 1]
            } else {
                perpX = 1f; perpZ = 0f
            }

            // Cumulative distance for UV.y
            if (i > 0) {
                val prev = positions[i - 1]
                val dx = wp[0] - prev[0]
                val dz = wp[2] - prev[2]
                cumDist += sqrt((dx * dx + dz * dz).toDouble()).toFloat()
            }

            // Store vertices in GPS-local coordinates (no heading transform)
            val gx = wp[0]; val gy = wp[1]; val gz = wp[2]

            // Left vertex: pos(3) + uv(2)
            val li = i * 2
            val lo = li * 5
            vertexData[lo + 0] = gx - perpX * PATH_HALF_WIDTH
            vertexData[lo + 1] = gy
            vertexData[lo + 2] = gz - perpZ * PATH_HALF_WIDTH
            vertexData[lo + 3] = 0f       // u = 0 (left edge)
            vertexData[lo + 4] = cumDist   // v = distance in meters

            // Right vertex: pos(3) + uv(2)
            val ri = li + 1
            val ro = ri * 5
            vertexData[ro + 0] = gx + perpX * PATH_HALF_WIDTH
            vertexData[ro + 1] = gy
            vertexData[ro + 2] = gz + perpZ * PATH_HALF_WIDTH
            vertexData[ro + 3] = 1f       // u = 1 (right edge)
            vertexData[ro + 4] = cumDist   // v = distance in meters
        }

        // Build triangle indices for strip
        var idx = 0
        for (i in 0 until positions.size - 1) {
            val bl = (i * 2).toShort()
            val br = (i * 2 + 1).toShort()
            val tl = (i * 2 + 2).toShort()
            val tr = (i * 2 + 3).toShort()
            indexData[idx++] = bl; indexData[idx++] = br; indexData[idx++] = tl
            indexData[idx++] = tl; indexData[idx++] = br; indexData[idx++] = tr
        }

        // Create Filament buffers
        val vb = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(vertexData); flip() }

        val sharedVB = VertexBuffer.Builder()
            .vertexCount(vertCount)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 20)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0,
                VertexBuffer.AttributeType.FLOAT2, 12, 20)
            .build(engine)
        sharedVB.setBufferAt(engine, 0, vb)

        val ib = ByteBuffer.allocateDirect(indexData.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(indexData); flip() }

        val sharedIB = IndexBuffer.Builder()
            .indexCount(idxCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        sharedIB.setBuffer(engine, ib)

        // Create glow entity with transform component
        glowEntity = EntityManager.get().create()
        transformManager.create(glowEntity)
        glowVertexBuffer = sharedVB
        glowIndexBuffer = sharedIB
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 500f, 1f, 500f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, sharedVB, sharedIB)
            .material(0, glowMaterialInstance!!)
            .culling(false)
            .receiveShadows(false)
            .castShadows(false)
            .priority(4)
            .build(engine, glowEntity)
        scene.addEntity(glowEntity)
        glowInScene = true

        // Create occluded entity — separate VB/IB copy (same data)
        val vb2 = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(vertexData); flip() }

        val occVB = VertexBuffer.Builder()
            .vertexCount(vertCount)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 20)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0,
                VertexBuffer.AttributeType.FLOAT2, 12, 20)
            .build(engine)
        occVB.setBufferAt(engine, 0, vb2)

        val ib2 = ByteBuffer.allocateDirect(indexData.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(indexData); flip() }

        val occIB = IndexBuffer.Builder()
            .indexCount(idxCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        occIB.setBuffer(engine, ib2)

        occludedEntity = EntityManager.get().create()
        transformManager.create(occludedEntity)
        occludedVertexBuffer = occVB
        occludedIndexBuffer = occIB
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 500f, 1f, 500f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, occVB, occIB)
            .material(0, occludedMaterialInstance!!)
            .culling(false)
            .receiveShadows(false)
            .castShadows(false)
            .priority(5)
            .build(engine, occludedEntity)
        scene.addEntity(occludedEntity)
        occludedInScene = true

        Log.d(TAG, "Path mesh rebuilt: ${positions.size} points, $vertCount verts, $triCount tris, length=${pathLength}m")
    }

    private fun hideAll() {
        if (glowInScene) {
            scene.removeEntity(glowEntity)
            glowInScene = false
        }
        if (occludedInScene) {
            scene.removeEntity(occludedEntity)
            occludedInScene = false
        }
    }

    private fun destroyMeshResources() {
        val em = EntityManager.get()
        val rm = engine.renderableManager

        if (glowEntity != 0) {
            rm.destroy(glowEntity)
            transformManager.destroy(glowEntity)
            em.destroy(glowEntity)
            glowEntity = 0
        }
        if (occludedEntity != 0) {
            rm.destroy(occludedEntity)
            transformManager.destroy(occludedEntity)
            em.destroy(occludedEntity)
            occludedEntity = 0
        }
        glowVertexBuffer?.let { engine.destroyVertexBuffer(it) }
        glowIndexBuffer?.let { engine.destroyIndexBuffer(it) }
        occludedVertexBuffer?.let { engine.destroyVertexBuffer(it) }
        occludedIndexBuffer?.let { engine.destroyIndexBuffer(it) }
        glowVertexBuffer = null
        glowIndexBuffer = null
        occludedVertexBuffer = null
        occludedIndexBuffer = null
    }

    fun destroy() {
        if (!initialized) return
        hideAll()
        destroyMeshResources()
        glowMaterialInstance?.let { engine.destroyMaterialInstance(it) }
        occludedMaterialInstance?.let { engine.destroyMaterialInstance(it) }
        glowMaterial?.let { engine.destroyMaterial(it) }
        occludedMaterial?.let { engine.destroyMaterial(it) }
        initialized = false
    }
}
