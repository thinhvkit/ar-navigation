package com.ideals.arnav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Shared frost colours used across the AR chrome.
private val BarkFrost = Color(0xB3_0F1412)        // rgba(15,20,18,0.7)
private val FrostBorder = Color(0x2E_F4EDE0)      // rgba(244,237,224,0.18)
private val FrostBorderSoft = Color(0x26_F4EDE0)  // rgba(244,237,224,0.15)

// ── Pill button: "Exit" style ─────────────────────────────────

@Composable
fun TsArPillButton(
    text: String,
    icon: ImageVector? = Icons.Outlined.Close,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(BarkFrost)
            .border(1.dp, FrostBorder, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TrailSight.Cream, modifier = Modifier.size(15.dp))
        }
        Text(
            text,
            color = TrailSight.Cream,
            fontFamily = TsType.sans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Circular icon button ───────────────────────────────────────

@Composable
fun TsArCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(BarkFrost)
            .border(1.dp, FrostBorder, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = TrailSight.Cream,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Heading / compass pill ────────────────────────────────────

@Composable
fun TsHeadingPill(
    headingDegrees: Double,
    modifier: Modifier = Modifier,
) {
    val normalised = ((headingDegrees % 360) + 360) % 360
    val cardinal = headingCardinal(normalised)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(BarkFrost)
            .border(1.dp, FrostBorder, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // North-needle icon: cream circle, orange top triangle pointing up.
        Canvas(modifier = Modifier.size(14.dp)) {
            val r = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                color = TrailSight.Cream.copy(alpha = 0.5f),
                radius = r - 1f,
                center = Offset(cx, cy),
                style = Stroke(width = 1f),
            )
            val top = Path().apply {
                moveTo(cx, cy - r + 1f)
                lineTo(cx + r * 0.45f, cy - 0.4f)
                lineTo(cx, cy - 0.9f)
                lineTo(cx - r * 0.45f, cy - 0.4f)
                close()
            }
            drawPath(top, color = TrailSight.Orange)
            val bot = Path().apply {
                moveTo(cx, cy + r - 1f)
                lineTo(cx - r * 0.45f, cy + 0.4f)
                lineTo(cx, cy + 0.9f)
                lineTo(cx + r * 0.45f, cy + 0.4f)
                close()
            }
            drawPath(bot, color = TrailSight.Cream.copy(alpha = 0.5f))
        }
        Text(
            text = "${normalised.roundToInt()}° $cardinal",
            color = TrailSight.Cream,
            fontFamily = TsType.mono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
    }
}

private fun headingCardinal(deg: Double): String = when {
    deg < 22.5 || deg >= 337.5 -> "N"
    deg < 67.5 -> "NE"
    deg < 112.5 -> "E"
    deg < 157.5 -> "SE"
    deg < 202.5 -> "S"
    deg < 247.5 -> "SW"
    deg < 292.5 -> "W"
    else -> "NW"
}

// ── Trail name strip ──────────────────────────────────────────

@Composable
fun TsTrailNameStrip(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(BarkFrost)
            .border(1.dp, FrostBorderSoft, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        // Amber glow dot.
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(TrailSight.ArOrb)
        )
        Text(
            name,
            color = TrailSight.Cream,
            fontFamily = TsType.mono,
            fontSize = 11.sp,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
    }
}

// ── Bottom HUD: three stat pills ──────────────────────────────

@Composable
fun TsArBottomHud(
    nextLabel: String = "NEXT WAYPOINT",
    nextValue: String,
    remainingLabel: String = "REMAINING",
    remainingValue: String,
    thirdLabel: String = "ETA",
    thirdValue: String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        HudStat(
            label = nextLabel,
            value = nextValue,
            valueColor = TrailSight.ArOrb,
            modifier = Modifier.weight(1f),
        )
        HudStat(
            label = remainingLabel,
            value = remainingValue,
            modifier = Modifier.weight(1f),
        )
        HudStat(
            label = thirdLabel,
            value = thirdValue,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HudStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TrailSight.Cream,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BarkFrost)
            .border(1.dp, FrostBorderSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = TrailSight.Cream.copy(alpha = 0.55f),
            fontFamily = TsType.mono,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            color = valueColor,
            fontFamily = TsType.mono,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
    }
}

// ── Formatters shared with ArScreen ───────────────────────────

fun formatDistanceMetersTs(m: Float): String {
    if (m <= 0f) return "—"
    return if (m < 1000f) "${m.roundToInt()}m" else "%.1fkm".format(m / 1000f)
}

fun formatEtaTs(seconds: Double): String {
    if (seconds <= 0) return "—"
    val total = seconds.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
