package com.ideals.arnav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.min

// Canvas elevation profile — filled moss silhouette + stroke on top.
// If `samples` is non-empty, the real elevation series is rendered; otherwise a
// deterministic pseudo-random silhouette is generated from `seed`.
@Composable
fun ElevationProfile(
    modifier: Modifier = Modifier,
    samples: List<Float> = emptyList(),
    seed: Int = 1,
    strokeColor: Color = TrailSight.Moss,
    fillColor: Color = TrailSight.Moss.copy(alpha = 0.18f),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val points = buildProfilePoints(samples, seed, w, h)

        val stroke = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        val fill = Path().apply {
            addPath(stroke)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(fill, color = fillColor)
        drawPath(
            stroke,
            color = strokeColor,
            style = Stroke(width = 1.5f, join = StrokeJoin.Round),
        )
    }
}

private data class ProfilePt(val x: Float, val y: Float)

private fun buildProfilePoints(samples: List<Float>, seed: Int, w: Float, h: Float): List<ProfilePt> {
    val steps = 40
    val minPad = 10f
    val maxPad = 6f

    return if (samples.size >= 2) {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        samples.forEach { e ->
            if (e < lo) lo = e
            if (e > hi) hi = e
        }
        val span = (hi - lo).coerceAtLeast(1f)
        val n = samples.size
        (0..steps).map { i ->
            val t = i.toFloat() / steps
            val srcIdx = (t * (n - 1)).toInt().coerceAtMost(n - 1)
            val sampleFrac = (samples[srcIdx] - lo) / span
            val x = t * w
            val y = h - maxPad - sampleFrac * (h - minPad - maxPad)
            ProfilePt(x, y)
        }
    } else {
        var rand = seed.toLong()
        val rng = {
            rand = (rand * 9301L + 49297L) % 233280L
            rand / 233280.0
        }
        var y = h * 0.5f
        (0..steps).map { i ->
            y += (rng().toFloat() - 0.45f) * 8f
            y = max(minPad, min(h - maxPad, y))
            ProfilePt(i * (w / steps), y)
        }
    }
}
