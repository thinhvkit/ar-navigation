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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun OnboardingScreen(
    cameraGranted: Boolean,
    locationGranted: Boolean,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lang by TrailSightSettings.get(context).language.collectAsState()
    val s = strings(lang)
    val layoutDir = if (lang == TrailSightSettings.LANG_HE) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TrailSight.Cream),
        ) {
            // Subtle moss topo wash behind the content.
            OnboardingTopoWash(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 32.dp),
            ) {
                // Brand row with BETA badge.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TsBrandMarkLight(size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        s.appName,
                        color = TrailSight.Bark,
                        fontFamily = TsType.sans,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    BetaBadge(text = s.onboardingBeta)
                }

                Spacer(modifier = Modifier.height(60.dp))

                Text(
                    s.onboardingStep,
                    color = TrailSight.Moss,
                    fontFamily = TsType.mono,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    s.onboardingTitle,
                    color = TrailSight.Bark,
                    fontFamily = TsType.sans,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.8).sp,
                    lineHeight = 35.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    s.onboardingBody,
                    color = TrailSight.BarkSoft,
                    fontFamily = TsType.sans,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(32.dp))

                PermRow(
                    icon = Icons.Outlined.CameraAlt,
                    title = s.onboardingCameraTitle,
                    sub = s.onboardingCameraSub,
                    granted = cameraGranted,
                    grantedLabel = s.onboardingGranted,
                    askLabel = s.onboardingAsk,
                )
                Spacer(modifier = Modifier.height(10.dp))
                PermRow(
                    icon = Icons.Outlined.LocationOn,
                    title = s.onboardingLocationTitle,
                    sub = s.onboardingLocationSub,
                    granted = locationGranted,
                    grantedLabel = s.onboardingGranted,
                    askLabel = s.onboardingAsk,
                )

                Spacer(modifier = Modifier.weight(1f))

                // Continue pill.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(100.dp))
                        .background(TrailSight.Bark)
                        .clickable(onClick = onContinue)
                        .padding(vertical = 16.dp),
                ) {
                    Text(
                        s.onboardingContinue,
                        color = TrailSight.Cream,
                        fontFamily = TsType.sans,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = TrailSight.Cream,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    s.onboardingCoordsFooter,
                    color = TrailSight.Stone,
                    fontFamily = TsType.mono,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PermRow(
    icon: ImageVector,
    title: String,
    sub: String,
    granted: Boolean,
    grantedLabel: String,
    askLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x8C_FFFFFF)) // white at ~55%
            .border(1.dp, Color(0x80_C5BFAE), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TrailSight.Moss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TrailSight.Cream, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TrailSight.Bark,
                fontFamily = TsType.sans,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                sub,
                color = TrailSight.Stone,
                fontFamily = TsType.sans,
                fontSize = 12.sp,
            )
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = TrailSight.Moss,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    grantedLabel,
                    color = TrailSight.Moss,
                    fontFamily = TsType.mono,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Text(
                askLabel,
                color = TrailSight.Stone,
                fontFamily = TsType.mono,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun BetaBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .border(1.dp, TrailSight.StoneLt, RoundedCornerShape(100.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = TrailSight.Stone,
            fontFamily = TsType.mono,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

// Brand mark on a light background — same shape as the others but adapted for contrast.
@Composable
private fun TsBrandMarkLight(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s / 2f - 0.5f
        drawCircle(TrailSight.Moss, radius = r, center = Offset(cx, cy))
        drawCircle(TrailSight.MossDark, radius = r, center = Offset(cx, cy), style = Stroke(width = 1f))
        val k = s / 32f
        val peaks = Path().apply {
            moveTo(6f * k, 23f * k)
            lineTo(11f * k, 14f * k)
            lineTo(15f * k, 19f * k)
            lineTo(19f * k, 12f * k)
            lineTo(26f * k, 23f * k)
            close()
        }
        drawPath(peaks, color = TrailSight.Cream.copy(alpha = 0.9f))
        drawCircle(TrailSight.Amber, radius = 2f * k, center = Offset(22f * k, 9f * k))
    }
}

@Composable
private fun OnboardingTopoWash(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseColor = TrailSight.Moss.copy(alpha = 0.09f)
        var y = 40f
        var i = 0
        while (y < h) {
            val amp = 6f + (i % 3) * 3f
            val off = (i * 17f) % 40f
            val path = Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x <= w) {
                    val nextX = x + 20f
                    val ctrlY = y + sin((x + off) / 22f) * amp
                    quadraticTo(x + 10f, ctrlY, nextX, y)
                    x = nextX
                }
            }
            drawPath(path, color = baseColor, style = Stroke(width = 1f))
            y += 42f
            i++
        }
    }
}
