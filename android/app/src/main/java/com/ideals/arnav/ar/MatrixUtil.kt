package com.ideals.arnav.ar

import android.opengl.Matrix

object MatrixUtil {

    private val tempModel = FloatArray(16)
    private val tempMVP = FloatArray(16)

    /**
     * Build a model matrix: translate → rotateY → scale, written into [out].
     */
    fun buildModelMatrix(
        out: FloatArray,
        tx: Float, ty: Float, tz: Float,
        rotYDeg: Float,
        sx: Float, sy: Float, sz: Float
    ) {
        Matrix.setIdentityM(out, 0)
        Matrix.translateM(out, 0, tx, ty, tz)
        if (rotYDeg != 0f) {
            Matrix.rotateM(out, 0, rotYDeg, 0f, 1f, 0f)
        }
        if (sx != 1f || sy != 1f || sz != 1f) {
            Matrix.scaleM(out, 0, sx, sy, sz)
        }
    }

    /**
     * Multiply projection * view * model → [out].
     */
    fun computeMVP(out: FloatArray, projection: FloatArray, view: FloatArray, model: FloatArray) {
        Matrix.multiplyMM(tempMVP, 0, view, 0, model, 0) // VM = view * model
        Matrix.multiplyMM(out, 0, projection, 0, tempMVP, 0) // PVM = proj * VM
    }
}
