package com.ideals.arnav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Stylised topo-map thumbnail — decorative only, used as a file-row tile.
// Deterministic from `seed` so the same file always renders the same squiggle.
@Composable
fun TrailSightMiniMap(
    modifier: Modifier = Modifier,
    seed: Int = 7,
    showHeading: Boolean = true,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Radial cream-to-stone wash for the map base.
        drawRect(
            brush = Brush.radialGradient(
                0f to Color(0xFFE8DFC9),
                1f to Color(0xFFC7BDA1),
                center = Offset(w / 2f, h / 2f),
                radius = max(w, h),
            )
        )

        // Contour lines — wavy strokes across the tile.
        val contourColor = Color(0xFF4A6B3F).copy(alpha = 0.22f)
        repeat(6) { i ->
            val cy = 20f + i * (h - 40f) / 5f
            val path = Path().apply {
                moveTo(0f, cy)
                var x = 0f
                while (x <= w) {
                    val nextX = x + 10f
                    val ctrlY = cy + sin((x + i * 9f) / 8f) * 4f
                    quadraticTo(x + 5f, ctrlY, nextX, cy)
                    x = nextX
                }
            }
            drawPath(path, color = contourColor, style = Stroke(width = 0.7f))
        }

        // Deterministic pseudo-random trail walk.
        val trail = buildTrail(seed, w, h)

        val trailPath = Path().apply {
            trail.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }

        // Soft underglow.
        drawPath(
            trailPath,
            color = TrailSight.Orange.copy(alpha = 0.35f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Dashed primary trail (matches the dashed SVG stroke in the design).
        drawPath(
            trailPath,
            color = TrailSight.Orange,
            style = Stroke(
                width = 2.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4f)),
            ),
        )

        // Start (moss) and end (bark) dots.
        trail.firstOrNull()?.let { p ->
            drawCircle(TrailSight.Moss, radius = 3f, center = Offset(p.x, p.y))
        }
        trail.lastOrNull()?.let { p ->
            drawCircle(TrailSight.Bark, radius = 3f, center = Offset(p.x, p.y))
        }

        // User marker with an optional heading cone.
        if (trail.size >= 5) {
            val user = trail[4]
            if (showHeading) {
                val cone = Path().apply {
                    moveTo(user.x, user.y)
                    lineTo(user.x - 10f, user.y - 16f)
                    // Slight arc at the top to round off the cone.
                    quadraticTo(user.x, user.y - 22f, user.x + 10f, user.y - 16f)
                    close()
                }
                drawPath(cone, color = TrailSight.Moss.copy(alpha = 0.25f))
            }
            drawCircle(Color.White, radius = 6f, center = Offset(user.x, user.y))
            drawCircle(TrailSight.Moss, radius = 4f, center = Offset(user.x, user.y))
        }
    }
}

private data class Pt(val x: Float, val y: Float)

private fun buildTrail(seed: Int, w: Float, h: Float): List<Pt> {
    var rand = seed.toLong()
    val rng = {
        rand = (rand * 9301L + 49297L) % 233280L
        rand / 233280.0
    }
    val points = mutableListOf<Pt>()
    var x = 20f
    var y = h - 20f
    points.add(Pt(x, y))
    repeat(14) {
        x += (6f + rng().toFloat() * 8f)
        y -= (4f + rng().toFloat() * 8f - 2f)
        x = min(w - 10f, max(10f, x))
        y = min(h - 10f, max(10f, y))
        points.add(Pt(x, y))
    }
    return points
}
