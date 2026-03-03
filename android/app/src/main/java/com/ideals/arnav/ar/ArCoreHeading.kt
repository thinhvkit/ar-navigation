package com.ideals.arnav.ar

import com.google.ar.core.Pose
import kotlin.math.atan2

/**
 * Extracts camera yaw (rotation around Y-axis) from an ARCore Pose.
 * Used to get smooth 60Hz heading from ARCore instead of noisy 1-2Hz GPS heading.
 */
object ArCoreHeading {

    /**
     * Extract yaw angle in degrees [0, 360) from an ARCore camera Pose.
     *
     * Convention: 0° = ARCore's initial -Z direction, increasing clockwise
     * when viewed from above (matching GPS heading convention).
     */
    fun extractYawDegrees(pose: Pose): Double {
        val q = pose.rotationQuaternion // [x, y, z, w]
        val x = q[0].toDouble()
        val y = q[1].toDouble()
        val z = q[2].toDouble()
        val w = q[3].toDouble()

        // Camera forward is -Z in camera space. Transform by quaternion to get
        // world-space forward, then compute yaw from its XZ projection.
        // yaw = atan2(2(xz + wy), 1 - 2(x² + y²))
        val sinYaw = 2.0 * (x * z + w * y)
        val cosYaw = 1.0 - 2.0 * (x * x + y * y)
        val yawRad = atan2(sinYaw, cosYaw)

        var degrees = Math.toDegrees(yawRad)
        if (degrees < 0) degrees += 360.0
        return degrees
    }
}
