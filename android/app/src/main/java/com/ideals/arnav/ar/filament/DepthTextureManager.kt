package com.ideals.arnav.ar.filament

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.FloatBuffer

/**
 * Extracts the ARCore depth image each frame and uploads it to a Filament R32F texture.
 * Used by occlusion shaders to hide virtual content behind real-world geometry.
 *
 * Uses R32F (float) format instead of R16UI (integer) because Filament's sampler2d
 * requires a float-compatible texture format.
 */
class DepthTextureManager(private val engine: Engine) {

    companion object {
        private const val TAG = "DepthTextureManager"
    }

    private var texture: Texture? = null
    private var texWidth = 0
    private var texHeight = 0
    private var floatData: FloatArray? = null

    /** 1x1 dummy texture filled with max depth — makes everything "visible" (no occlusion) */
    val dummyTexture: Texture = Texture.Builder()
        .width(1).height(1)
        .levels(1)
        .sampler(Texture.Sampler.SAMPLER_2D)
        .format(Texture.InternalFormat.R32F)
        .build(engine).also {
            it.setImage(
                engine, 0,
                Texture.PixelBufferDescriptor(
                    FloatBuffer.wrap(floatArrayOf(65535f)),
                    Texture.Format.R,
                    Texture.Type.FLOAT
                )
            )
        }

    val filamentTexture: Texture? get() = texture

    fun update(frame: Frame): Texture? {
        val depthImage = try {
            frame.acquireDepthImage16Bits()
        } catch (e: NotYetAvailableException) {
            return texture  // return last valid texture
        } catch (e: Exception) {
            Log.w(TAG, "Depth image unavailable: ${e.message}")
            return null
        }

        try {
            val w = depthImage.width
            val h = depthImage.height

            // Recreate only if dimensions changed
            if (w != texWidth || h != texHeight) {
                texture?.let { engine.destroyTexture(it) }
                texture = Texture.Builder()
                    .width(w).height(h)
                    .levels(1)
                    .sampler(Texture.Sampler.SAMPLER_2D)
                    .format(Texture.InternalFormat.R32F)
                    .build(engine)
                texWidth = w
                texHeight = h
                floatData = FloatArray(w * h)
                Log.d(TAG, "Created depth texture ${w}×${h}")
            }

            // Upload depth plane: convert ushort mm → float mm
            val plane = depthImage.planes[0]
            val buf = plane.buffer.asShortBuffer()
            val fData = floatData!!
            for (i in fData.indices) {
                fData[i] = (buf.get(i).toInt() and 0xFFFF).toFloat()
            }

            texture!!.setImage(
                engine, 0,
                Texture.PixelBufferDescriptor(
                    FloatBuffer.wrap(fData),
                    Texture.Format.R,
                    Texture.Type.FLOAT
                )
            )

            return texture

        } finally {
            depthImage.close()
        }
    }

    fun destroy() {
        texture?.let { engine.destroyTexture(it) }
        texture = null
        engine.destroyTexture(dummyTexture)
    }
}
