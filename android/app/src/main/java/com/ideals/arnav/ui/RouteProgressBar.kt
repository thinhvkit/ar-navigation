package com.ideals.arnav.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BarkFrost = Color(0xB3_0F1412)
private val FrostBorderSoft = Color(0x26_F4EDE0)
private val BarTrack = Color(0x33_F4EDE0) // cream at 20%

@Composable
fun RouteProgressBar(
    distanceAlongRoute: Float,
    totalRouteDistance: Float,
    distanceRemaining: Float,
    etaSeconds: Double,
    modifier: Modifier = Modifier
) {
    val progress = if (totalRouteDistance > 0) {
        (distanceAlongRoute / totalRouteDistance).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "progress"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BarkFrost)
            .border(1.dp, FrostBorderSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Progress bar — moss→amber gradient evokes the glowing AR ribbon.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BarTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            0f to TrailSight.Moss,
                            1f to TrailSight.Amber,
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(
                text = formatDistance(distanceRemaining) + " remaining",
                color = TrailSight.Cream.copy(alpha = 0.7f),
                fontFamily = TsType.mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatEta(etaSeconds),
                color = TrailSight.Cream.copy(alpha = 0.7f),
                fontFamily = TsType.mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatEta(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    return if (mins < 1) "< 1 min" else "$mins min"
}
