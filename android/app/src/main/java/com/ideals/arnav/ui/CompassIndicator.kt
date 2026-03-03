package com.ideals.arnav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val COMPASS_SIZE = 28.dp
private val BG_COLOR = Color(0f, 0f, 0f, 0.5f)
private val NORTH_COLOR = Color(0xFFFF3B30) // Apple Red
private val ARROW_COLOR = Color.White

@Composable
fun CompassIndicator(
    heading: Double,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(COMPASS_SIZE)
            .clip(CircleShape)
            .background(BG_COLOR)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.55f

        // Rotate so north arrow always points geographic north
        val angleRad = Math.toRadians(-heading).toFloat()
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)

        // Arrow half-width
        val hw = r * 0.35f

        // North half (red) — tip points to north
        val tipX = cx - sinA * r
        val tipY = cy - cosA * r
        val northPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(cx + cosA * hw, cy - sinA * hw)
            lineTo(cx, cy)
            close()
        }
        drawPath(northPath, NORTH_COLOR, style = Fill)

        // North half right side
        val northPath2 = Path().apply {
            moveTo(tipX, tipY)
            lineTo(cx - cosA * hw, cy + sinA * hw)
            lineTo(cx, cy)
            close()
        }
        drawPath(northPath2, NORTH_COLOR.copy(alpha = 0.7f), style = Fill)

        // South half (white) — tail
        val tailX = cx + sinA * r
        val tailY = cy + cosA * r
        val southPath = Path().apply {
            moveTo(tailX, tailY)
            lineTo(cx + cosA * hw, cy - sinA * hw)
            lineTo(cx, cy)
            close()
        }
        drawPath(southPath, ARROW_COLOR.copy(alpha = 0.5f), style = Fill)

        val southPath2 = Path().apply {
            moveTo(tailX, tailY)
            lineTo(cx - cosA * hw, cy + sinA * hw)
            lineTo(cx, cy)
            close()
        }
        drawPath(southPath2, ARROW_COLOR.copy(alpha = 0.35f), style = Fill)

        // Center dot
        drawCircle(Color.White, r * 0.12f, Offset(cx, cy))
    }
}
