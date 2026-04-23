package com.ideals.arnav.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ideals.arnav.route.TurnStep

// Shared with TrailSightArChrome.kt.
private val BarkFrost = Color(0xB3_0F1412)
private val FrostBorderSoft = Color(0x26_F4EDE0)

@Composable
fun TurnInstructionCard(
    step: TurnStep,
    distanceToNextTurn: Float,
    isOffRoute: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val outlineColor = if (isOffRoute) TrailSight.Danger else FrostBorderSoft
    val outlineWidth = if (isOffRoute) 2.dp else 1.dp
    val iconTileColor = if (isOffRoute) TrailSight.Danger else TrailSight.Moss

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(BarkFrost)
            .border(outlineWidth, outlineColor, shape)
            .padding(12.dp)
    ) {
        // Maneuver icon on a moss (or danger) tile.
        Icon(
            imageVector = ManeuverIcon.forManeuver(step.maneuverType, step.maneuverModifier),
            contentDescription = step.maneuverType,
            tint = TrailSight.Cream,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTileColor)
                .padding(8.dp)
        )

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = formatDistance(distanceToNextTurn),
                color = TrailSight.Cream,
                fontFamily = TsType.mono,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            if (step.instruction.isNotBlank()) {
                Text(
                    text = step.instruction,
                    color = TrailSight.Cream.copy(alpha = 0.75f),
                    fontFamily = TsType.sans,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun formatDistance(meters: Float): String {
    return when {
        meters >= 1000 -> "%.1f km".format(meters / 1000f)
        meters >= 100 -> "${(meters / 10).toInt() * 10} m"
        else -> "${meters.toInt()} m"
    }
}
