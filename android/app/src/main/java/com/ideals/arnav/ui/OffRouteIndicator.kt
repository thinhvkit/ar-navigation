package com.ideals.arnav.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OffRouteIndicator(
    isOffRoute: Boolean,
    modifier: Modifier = Modifier,
    text: String = "OFF ROUTE · REROUTING"
) {
    // Subtle pulse — matches the "pulseAlert" animation in the design.
    val pulse = rememberInfiniteTransition(label = "offRoutePulse")
    val alphaPulse by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "offRouteAlpha",
    )

    AnimatedVisibility(
        visible = isOffRoute,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .alpha(alphaPulse)
                .clip(RoundedCornerShape(20.dp))
                .background(TrailSight.Danger)
                .border(1.dp, TrailSight.Danger, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = TrailSight.Amber,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                color = TrailSight.Cream,
                fontFamily = TsType.mono,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
            CircularProgressIndicator(
                color = TrailSight.Cream.copy(alpha = 0.85f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(14.dp)
            )
        }
    }
}
