package com.ideals.arnav.ar

import kotlin.math.atan2
import kotlin.math.sqrt

/** Simple 3D vector replacing SceneView's Position type. */
data class Vec3(val x: Float, val y: Float, val z: Float)

/**
 * Computes arrow positions and rotations along the route.
 * Chevron geometry: two thin arms at ±30° forming a V shape.
 * Also provides constants for the green ground path.
 */
object ArrowMeshFactory {

    /** Chevron arm dimensions — chunky V-shape like road paint */
    const val CHEVRON_ARM_LENGTH = 0.6f
    const val CHEVRON_HEIGHT = 0.005f
    const val CHEVRON_ARM_WIDTH = 0.14f
    const val CHEVRON_ANGLE = 32f

    /** Arrow color: green/teal, clearly visible */
    const val ARROW_COLOR_R = 0.0f
    const val ARROW_COLOR_G = 0.85f
    const val ARROW_COLOR_B = 0.55f
    const val ARROW_COLOR_A = 0.82f

    /** Ground path: subdued teal, semi-transparent */
    const val PATH_COLOR_R = 0.0f
    const val PATH_COLOR_G = 0.55f
    const val PATH_COLOR_B = 0.45f
    const val PATH_COLOR_A = 0.30f
    const val PATH_WIDTH = 1.0f
    const val PATH_HEIGHT = 0.005f
    const val PATH_CHUNK_LENGTH = 1.0f

    /** Y offsets — ground-hugging */
    const val Y_OFFSET = 0.008f
    const val PATH_Y_OFFSET = 0.003f

    data class ArrowPlacement(
        val position: Vec3,
        val rotationY: Float,
        val segmentIndex: Int
    )

    /**
     * Pre-computed route segment geometry. Built once when route changes,
     * reused every frame by [sampleChevrons].
     */
    class RouteSegmentData(
        val worldPositions: List<FloatArray>,
        val segStartDist: FloatArray,
        val segLen: FloatArray,
        val segDirX: FloatArray,
        val segDirZ: FloatArray,
        val segRotY: FloatArray,
        val totalDist: Float
    ) {
        val segCount get() = segLen.size
    }

    /**
     * Pre-compute route segment arrays from world positions.
     * Call once when the route is set; result is stored in NavigationState.
     */
    fun precomputeSegments(worldPositions: List<FloatArray>): RouteSegmentData? {
        if (worldPositions.size < 2) return null

        val segCount = worldPositions.size - 1
        val segStartDist = FloatArray(segCount)
        val segLen = FloatArray(segCount)
        val segDirX = FloatArray(segCount)
        val segDirZ = FloatArray(segCount)
        val segRotY = FloatArray(segCount)

        var cumDist = 0f
        for (seg in 0 until segCount) {
            segStartDist[seg] = cumDist
            val start = worldPositions[seg]
            val end = worldPositions[seg + 1]
            val dx = end[0] - start[0]
            val dz = end[2] - start[2]
            val len = sqrt((dx * dx + dz * dz).toDouble()).toFloat()
            segLen[seg] = len
            if (len > 0) {
                segDirX[seg] = dx / len
                segDirZ[seg] = dz / len
            }
            segRotY[seg] = Math.toDegrees(atan2(dx.toDouble(), -dz.toDouble())).toFloat()
            cumDist += len
        }

        return RouteSegmentData(worldPositions, segStartDist, segLen, segDirX, segDirZ, segRotY, cumDist)
    }

    /**
     * Zero-allocation chevron sampling into pre-allocated flat arrays.
     * Writes world X, Z, rotation, segment index, and distance-along-route
     * directly into caller-provided arrays.
     *
     * @return number of chevrons placed (up to [maxCount])
     */
    fun sampleChevrons(
        cache: RouteSegmentData,
        spacing: Float,
        startOffset: Float,
        outX: FloatArray,
        outZ: FloatArray,
        outRotY: FloatArray,
        outSegIdx: IntArray,
        outDistAlong: FloatArray,
        maxCount: Int
    ): Int {
        var count = 0
        var d = startOffset
        var currentSeg = 0
        val segCount = cache.segCount

        while (d < cache.totalDist && count < maxCount) {
            while (currentSeg < segCount - 1 &&
                d >= cache.segStartDist[currentSeg] + cache.segLen[currentSeg]
            ) {
                currentSeg++
            }

            if (cache.segLen[currentSeg] < 0.3f) {
                d += spacing
                continue
            }

            val distInSeg = d - cache.segStartDist[currentSeg]
            val start = cache.worldPositions[currentSeg]
            outX[count] = start[0] + cache.segDirX[currentSeg] * distInSeg
            outZ[count] = start[2] + cache.segDirZ[currentSeg] * distInSeg
            outRotY[count] = cache.segRotY[currentSeg]
            outSegIdx[count] = currentSeg
            outDistAlong[count] = d
            count++

            d += spacing
        }

        return count
    }

    /**
     * Compute arrow placements along route using cumulative distance.
     * [worldPositions] are local meters via CoordinateConverter.
     * [startOffset] shifts the first placement along the route (for marching animation).
     *
     * NOTE: For per-frame chevron animation, prefer [precomputeSegments] +
     * [sampleChevrons] to avoid recomputing segment data and allocating objects every frame.
     */
    fun computeArrowPlacements(
        worldPositions: List<FloatArray>,
        spacing: Float = 2.0f,
        startOffset: Float = 0f
    ): List<ArrowPlacement> {
        val segments = precomputeSegments(worldPositions) ?: return emptyList()
        val maxCount = ((segments.totalDist - startOffset) / spacing).toInt() + 1
        val tmpX = FloatArray(maxCount)
        val tmpZ = FloatArray(maxCount)
        val tmpRotY = FloatArray(maxCount)
        val tmpSegIdx = IntArray(maxCount)
        val tmpDist = FloatArray(maxCount)
        val count = sampleChevrons(segments, spacing, startOffset, tmpX, tmpZ, tmpRotY, tmpSegIdx, tmpDist, maxCount)
        return (0 until count).map { i ->
            ArrowPlacement(
                position = Vec3(tmpX[i], Y_OFFSET, tmpZ[i]),
                rotationY = tmpRotY[i],
                segmentIndex = tmpSegIdx[i]
            )
        }
    }
}
