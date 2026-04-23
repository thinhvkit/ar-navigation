package com.ideals.arnav.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ideals.arnav.files.GpxKmlFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Cold-start splash that greets a returning user and offers to resume the last trail.
@Composable
fun ResumeScreen(
    file: GpxKmlFile,
    onContinue: () -> Unit,
    onChooseDifferent: () -> Unit,
) {
    val context = LocalContext.current
    val lang by TrailSightSettings.get(context).language.collectAsState()
    val s = strings(lang)
    val layoutDir = if (lang == TrailSightSettings.LANG_HE) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrailSight.Bark),
    ) {
        // Subtle topo wash — a few cream sine-lines laid over the bark background.
        TopoWash(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
        ) {
            // Brand row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrailSightBrandMark(size = 30.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    s.appName,
                    color = TrailSight.Cream,
                    fontFamily = TsType.sans,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                s.welcomeBack,
                color = TrailSight.Amber,
                fontFamily = TsType.mono,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                s.pickingUp,
                color = TrailSight.Cream,
                fontFamily = TsType.sans,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
                lineHeight = 31.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                s.resumeBody,
                color = TrailSight.Cream.copy(alpha = 0.65f),
                fontFamily = TsType.sans,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Preview card.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x14_F4EDE0))
                    .border(1.dp, Color(0x1F_F4EDE0), RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    TrailSightMiniMap(
                        modifier = Modifier.fillMaxSize(),
                        seed = seedForResume(file),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                    ) {
                        Text(
                            s.lastTrail,
                            color = TrailSight.Amber,
                            fontFamily = TsType.mono,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "${file.name}.${file.type.name.lowercase()}",
                    color = TrailSight.Cream,
                    fontFamily = TsType.mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "${formatFileSize(file.sizeBytes)} · ${formatDate(file.uploadedAt)}",
                    color = TrailSight.Cream.copy(alpha = 0.55f),
                    fontFamily = TsType.sans,
                    fontSize = 11.5.sp,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Primary CTA.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(100.dp))
                    .background(TrailSight.Orange)
                    .clickable(onClick = onContinue)
                    .padding(vertical = 16.dp),
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = TrailSight.Cream,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    s.continueTrail,
                    color = TrailSight.Cream,
                    fontFamily = TsType.sans,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary (outline) CTA.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(100.dp))
                    .border(
                        BorderStroke(1.5.dp, Color(0x59_F4EDE0)),
                        RoundedCornerShape(100.dp),
                    )
                    .clickable(onClick = onChooseDifferent)
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    s.pickDifferent,
                    color = TrailSight.Cream,
                    fontFamily = TsType.sans,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    } // end CompositionLocalProvider
}

// Simple cream-tinted topo wash for the splash background.
@Composable
private fun TopoWash(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseColor = TrailSight.Cream.copy(alpha = 0.08f)
        var y = 40f
        var i = 0
        while (y < h) {
            val amp = 6f + (i % 3) * 3f
            val off = (i * 17f) % 40f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x <= w) {
                    val nextX = x + 20f
                    val ctrlY = y + kotlin.math.sin((x + off) / 22f) * amp
                    quadraticTo(x + 10f, ctrlY, nextX, y)
                    x = nextX
                }
            }
            drawPath(
                path,
                color = baseColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )
            y += 42f
            i++
        }
    }
}

@Composable
private fun TrailSightBrandMark(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(size),
    ) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s / 2f - 0.5f
        drawCircle(TrailSight.Moss, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))
        drawCircle(
            TrailSight.MossDark,
            radius = r,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
        )
        val k = s / 32f
        val peaks = androidx.compose.ui.graphics.Path().apply {
            moveTo(6f * k, 23f * k)
            lineTo(11f * k, 14f * k)
            lineTo(15f * k, 19f * k)
            lineTo(19f * k, 12f * k)
            lineTo(26f * k, 23f * k)
            close()
        }
        drawPath(peaks, color = TrailSight.Cream.copy(alpha = 0.9f))
        drawCircle(
            TrailSight.Amber,
            radius = 2f * k,
            center = androidx.compose.ui.geometry.Offset(22f * k, 9f * k),
        )
    }
}

private fun seedForResume(file: GpxKmlFile): Int {
    val h = file.id.hashCode() xor file.name.hashCode()
    return abs(h % 997) + 3
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))
