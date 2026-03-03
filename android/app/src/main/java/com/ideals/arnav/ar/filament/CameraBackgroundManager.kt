package com.ideals.arnav.ar.filament

import android.content.Context
import android.media.Image
import android.util.Log
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages the ARCore camera background using a dedicated Filament View with
 * orthographic projection. Renders as a separate pass before the main AR view.
 *
 * Uses ARCore's transformDisplayUvCoords() to handle display rotation and
 * camera-to-screen aspect ratio mapping automatically.
 *
 * Loads pre-compiled camera_background.filamat which does YUV→RGB conversion
 * using Y and UV texture samplers.
 */
class CameraBackgroundManager(
    private val context: Context,
    private val engine: Engine
) {

    companion object {
        private const val TAG = "CameraBgManager"

        // Display-space UVs for quad corners: (0,0)=top-left, (1,1)=bottom-right
        // Vertex order: bottom-left, bottom-right, top-left, top-right
        private val DISPLAY_UVS = floatArrayOf(
            0f, 1f,   // vertex (-1,-1) bottom-left of screen
            1f, 1f,   // vertex ( 1,-1) bottom-right
            0f, 0f,   // vertex (-1, 1) top-left
            1f, 0f    // vertex ( 1, 1) top-right
        )
    }

    // Dedicated background rendering pipeline
    private var bgView: View? = null
    private var bgScene: Scene? = null
    private var bgCamera: Camera? = null
    private var bgCameraEntity: Int = 0

    private var material: Material? = null
    private var materialInstance: MaterialInstance? = null
    private var yTexture: Texture? = null
    private var uvTexture: Texture? = null
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var entity: Int = 0
    private var initialized = false

    // Track texture dimensions to recreate on size change
    private var texWidth = 0
    private var texHeight = 0

    // Reusable byte buffers for Y and UV plane data
    private var yBuffer: ByteBuffer? = null
    private var uvBuffer: ByteBuffer? = null

    // Only rebuild UVs when display rotation changes
    private var lastDisplayRotation = -1

    private val sampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE
    )

    /**
     * Initialize with a dedicated background View, Scene, and orthographic Camera.
     */
    fun init(viewportWidth: Int, viewportHeight: Int) {
        // Load pre-compiled material
        material = MaterialLoader.load(context, engine, "materials/camera_background.filamat")
        materialInstance = material!!.createInstance()

        buildQuadGeometry(DISPLAY_UVS)

        // Background scene
        bgScene = engine.createScene()

        // Orthographic camera: maps quad vertices (-1,-1)→(1,1) to fill the viewport
        bgCameraEntity = EntityManager.get().create()
        bgCamera = engine.createCamera(bgCameraEntity).apply {
            setProjection(Camera.Projection.ORTHO, -1.0, 1.0, -1.0, 1.0, 0.0, 2.0)
        }

        // Background view — no post-processing (no bloom on camera feed)
        bgView = engine.createView().apply {
            camera = bgCamera
            scene = bgScene
            viewport = Viewport(0, 0, viewportWidth, viewportHeight)
            isPostProcessingEnabled = false
        }

        // Entity
        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer!!, indexBuffer!!)
            .material(0, materialInstance!!)
            .culling(false)
            .receiveShadows(false)
            .castShadows(false)
            .build(engine, entity)
        bgScene!!.addEntity(entity)

        initialized = true
        Log.d(TAG, "Camera background initialized (${viewportWidth}x${viewportHeight})")
    }

    /** Returns the background View for rendering before the main AR view. */
    fun getView(): View? = bgView

    /** Update viewport when surface size changes. */
    fun onSurfaceChanged(width: Int, height: Int) {
        bgView?.viewport = Viewport(0, 0, width, height)
    }

    /**
     * Update quad UVs using ARCore's transformDisplayUvCoords to handle
     * display rotation and camera-to-screen aspect ratio mapping.
     * Only rebuilds vertex buffer when rotation changes.
     */
    fun updateDisplayUvs(frame: Frame, displayRotation: Int) {
        if (!initialized) return
        if (displayRotation == lastDisplayRotation) return
        lastDisplayRotation = displayRotation

        val inputUvs = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(DISPLAY_UVS); flip() }
        val outputUvs = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        frame.transformDisplayUvCoords(inputUvs, outputUvs)
        val transformedUvs = FloatArray(8)
        outputUvs.rewind()
        outputUvs.get(transformedUvs)

        // matc default flipUV=true means Filament internally does V = 1 - V
        // before the fragment shader receives UVs. Pre-compensate here so the
        // final UV reaching the shader is correct.
        for (i in 1 until 8 step 2) {
            transformedUvs[i] = 1.0f - transformedUvs[i]
        }

        // Rebuild vertex buffer with transformed UVs
        val verts = floatArrayOf(
            -1f, -1f, -0.5f,  transformedUvs[0], transformedUvs[1],
             1f, -1f, -0.5f,  transformedUvs[2], transformedUvs[3],
            -1f,  1f, -0.5f,  transformedUvs[4], transformedUvs[5],
             1f,  1f, -0.5f,  transformedUvs[6], transformedUvs[7]
        )

        val vb = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(verts); flip() }
        vertexBuffer!!.setBufferAt(engine, 0, vb)

        Log.d(TAG, "Display UVs updated for rotation=$displayRotation")
    }

    /**
     * Update camera background from the current ARCore frame.
     * Acquires the CPU image (YUV_420_888), uploads Y and UV planes to Filament textures.
     */
    fun updateFromFrame(frame: Frame): Boolean {
        if (!initialized) return false

        val image: Image
        try {
            image = frame.acquireCameraImage()
        } catch (e: Exception) {
            return false
        }

        try {
            if (image.format != android.graphics.ImageFormat.YUV_420_888) {
                image.close()
                return false
            }

            val width = image.width
            val height = image.height

            // Recreate textures if dimensions changed
            if (width != texWidth || height != texHeight) {
                recreateTextures(width, height)
                texWidth = width
                texHeight = height
            }

            uploadYPlane(image)
            uploadUVPlane(image)

            // Bind textures to material
            materialInstance?.setParameter("yTexture", yTexture!!, sampler)
            materialInstance?.setParameter("uvTexture", uvTexture!!, sampler)
        } finally {
            image.close()
        }

        return true
    }

    private fun recreateTextures(width: Int, height: Int) {
        yTexture?.let { engine.destroyTexture(it) }
        uvTexture?.let { engine.destroyTexture(it) }

        yTexture = Texture.Builder()
            .width(width)
            .height(height)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.R8)
            .build(engine)

        uvTexture = Texture.Builder()
            .width(width / 2)
            .height(height / 2)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.RG8)
            .build(engine)

        Log.d(TAG, "Textures created: ${width}x${height}")
    }

    private fun uploadYPlane(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        val yBuf: ByteBuffer
        if (rowStride == width) {
            yBuf = buffer
        } else {
            val size = width * height
            if (yBuffer == null || yBuffer!!.capacity() < size) {
                yBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            }
            val dst = yBuffer!!
            dst.clear()
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.limit(row * rowStride + width)
                dst.put(buffer)
            }
            dst.flip()
            buffer.clear()
            yBuf = dst
        }

        yTexture?.setImage(engine, 0,
            Texture.PixelBufferDescriptor(
                yBuf, Texture.Format.R, Texture.Type.UBYTE
            )
        )
    }

    private fun uploadUVPlane(image: Image) {
        val plane = image.planes[1]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width / 2
        val height = image.height / 2

        val size = width * height * 2
        if (uvBuffer == null || uvBuffer!!.capacity() < size) {
            uvBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        }
        val dst = uvBuffer!!
        dst.clear()

        if (pixelStride == 2 && rowStride == width * 2) {
            val available = buffer.remaining().coerceAtMost(size)
            buffer.limit(buffer.position() + available)
            dst.put(buffer)
            while (dst.position() < size) dst.put(0)
        } else if (pixelStride == 2) {
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                buffer.limit(row * rowStride + width * 2)
                dst.put(buffer)
            }
        } else {
            val vPlane = image.planes[2]
            val vBuffer = vPlane.buffer
            for (row in 0 until height) {
                for (col in 0 until width) {
                    val uIdx = row * rowStride + col * pixelStride
                    val vIdx = row * vPlane.rowStride + col * vPlane.pixelStride
                    dst.put(buffer.get(uIdx))
                    dst.put(vBuffer.get(vIdx))
                }
            }
        }
        dst.flip()
        buffer.clear()

        uvTexture?.setImage(engine, 0,
            Texture.PixelBufferDescriptor(
                dst, Texture.Format.RG, Texture.Type.UBYTE
            )
        )
    }

    private fun buildQuadGeometry(initialUvs: FloatArray) {
        val verts = floatArrayOf(
            -1f, -1f, -0.5f,  initialUvs[0], initialUvs[1],
             1f, -1f, -0.5f,  initialUvs[2], initialUvs[3],
            -1f,  1f, -0.5f,  initialUvs[4], initialUvs[5],
             1f,  1f, -0.5f,  initialUvs[6], initialUvs[7]
        )
        val indices = shortArrayOf(0, 1, 2, 2, 1, 3)

        val vb = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(verts); flip() }

        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(4)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 20)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0,
                VertexBuffer.AttributeType.FLOAT2, 12, 20)
            .build(engine)
        vertexBuffer!!.setBufferAt(engine, 0, vb)

        val ib = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(indices); flip() }

        indexBuffer = IndexBuffer.Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer!!.setBuffer(engine, ib)
    }

    fun destroy() {
        if (!initialized) return
        bgScene?.removeEntity(entity)
        engine.renderableManager.destroy(entity)
        EntityManager.get().destroy(entity)
        materialInstance?.let { engine.destroyMaterialInstance(it) }
        material?.let { engine.destroyMaterial(it) }
        yTexture?.let { engine.destroyTexture(it) }
        uvTexture?.let { engine.destroyTexture(it) }
        vertexBuffer?.let { engine.destroyVertexBuffer(it) }
        indexBuffer?.let { engine.destroyIndexBuffer(it) }
        bgView?.let { engine.destroyView(it) }
        bgScene?.let { engine.destroyScene(it) }
        bgCamera?.let { engine.destroyCameraComponent(bgCameraEntity) }
        EntityManager.get().destroy(bgCameraEntity)
        initialized = false
    }
}
