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

private val AppleBlue = Color(0xFF007AFF)
private val AppleFrost = Color(0.12f, 0.12f, 0.14f, 0.72f)
private val OffRouteOrange = Color(0xFFFF9500)

@Composable
fun TurnInstructionCard(
    step: TurnStep,
    distanceToNextTurn: Float,
    isOffRoute: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val borderModifier = if (isOffRoute) {
        Modifier.border(2.dp, OffRouteOrange, shape)
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .then(borderModifier)
            .background(AppleFrost)
            .padding(12.dp)
    ) {
        // Maneuver icon in blue rounded square
        Icon(
            imageVector = ManeuverIcon.forManeuver(step.maneuverType, step.maneuverModifier),
            contentDescription = step.maneuverType,
            tint = Color.White,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppleBlue)
                .padding(8.dp)
        )

        // Distance + instruction text
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = formatDistance(distanceToNextTurn),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (step.instruction.isNotBlank()) {
                Text(
                    text = step.instruction,
                    color = Color.White.copy(alpha = 0.8f),
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
