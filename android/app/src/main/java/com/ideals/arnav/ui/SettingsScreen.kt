package com.ideals.arnav.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

@Composable
fun SettingsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { TrailSightSettings.get(context) }
    val lang by settings.language.collectAsState()
    val keepOn by settings.keepScreenOn.collectAsState()
    val showElev by settings.showElevation.collectAsState()
    val autoResume by settings.autoResume.collectAsState()
    val s = strings(lang)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TrailSight.Paper),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsTopBar(title = s.settings, onBack = onDismiss)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    // Language section.
                    item { SectionHeader(s.language) }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xB3_FFFFFF))
                                .border(1.dp, Color(0x70_C5BFAE), RoundedCornerShape(14.dp))
                        ) {
                            LangOption(
                                flag = "EN",
                                label = s.englishLabel,
                                sub = s.englishSub,
                                selected = lang == TrailSightSettings.LANG_EN,
                                onClick = { settings.setLanguage(TrailSightSettings.LANG_EN) },
                            )
                            Divider()
                            LangOption(
                                flag = "HE",
                                label = s.hebrewLabel,
                                sub = s.hebrewSub,
                                selected = lang == TrailSightSettings.LANG_HE,
                                onClick = { settings.setLanguage(TrailSightSettings.LANG_HE) },
                            )
                        }
                    }
                    item {
                        // Info banner.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x14_4A6B3F))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = TrailSight.Moss,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                s.languageNote,
                                color = TrailSight.BarkSoft,
                                fontFamily = TsType.sans,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    // Navigation section.
                    item { SectionHeader(s.navigation, topPadding = 26.dp) }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xB3_FFFFFF))
                                .border(1.dp, Color(0x70_C5BFAE), RoundedCornerShape(14.dp))
                        ) {
                            SettingToggleRow(
                                icon = Icons.Outlined.LocationOn,
                                label = s.keepScreenOn,
                                on = keepOn,
                                onToggle = { settings.setKeepScreenOn(it) },
                            )
                            Divider()
                            SettingToggleRow(
                                icon = Icons.Outlined.Layers,
                                label = s.showElevationProfile,
                                on = showElev,
                                onToggle = { settings.setShowElevation(it) },
                            )
                            Divider()
                            SettingToggleRow(
                                icon = Icons.Outlined.History,
                                label = s.autoResume,
                                sub = s.autoResumeSub,
                                on = autoResume,
                                onToggle = { settings.setAutoResume(it) },
                            )
                        }
                    }

                    // About section.
                    item { SectionHeader(s.about, topPadding = 26.dp) }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xB3_FFFFFF))
                                .border(1.dp, Color(0x70_C5BFAE), RoundedCornerShape(14.dp))
                        ) {
                            InfoRow(label = s.version, value = appVersion(context))
                            Divider()
                            InfoRow(label = s.storageUsed, value = computeStorageLabel(context))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            // AutoMirrored ArrowBack: flips automatically in RTL.
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = TrailSight.Bark,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            title,
            color = TrailSight.Bark,
            fontFamily = TsType.sans,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f),
            textAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                androidx.compose.ui.text.style.TextAlign.Right
            } else {
                androidx.compose.ui.text.style.TextAlign.Center
            },
        )
        Spacer(modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun SectionHeader(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    Text(
        text,
        color = TrailSight.Stone,
        fontFamily = TsType.mono,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = topPadding, bottom = 8.dp),
    )
}

@Composable
private fun LangOption(
    flag: String,
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TrailSight.Moss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                flag,
                color = TrailSight.Cream,
                fontFamily = TsType.mono,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = TrailSight.Bark,
                fontFamily = TsType.sans,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                sub,
                color = TrailSight.Stone,
                fontFamily = TsType.sans,
                fontSize = 12.sp,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(TrailSight.Moss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = TrailSight.Cream,
                    modifier = Modifier.size(13.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, TrailSight.StoneLt, CircleShape),
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!on) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x33_4A6B3F)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TrailSight.Moss, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = TrailSight.Bark,
                fontFamily = TsType.sans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (sub != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    sub,
                    color = TrailSight.Stone,
                    fontFamily = TsType.sans,
                    fontSize = 11.5.sp,
                )
            }
        }
        TrailSightToggle(on = on, onToggle = onToggle)
    }
}

@Composable
private fun TrailSightToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) TrailSight.Moss else TrailSight.StoneLt)
            .clickable { onToggle(!on) },
    ) {
        Box(
            modifier = Modifier
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            color = TrailSight.Bark,
            fontFamily = TsType.sans,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = TrailSight.Stone,
            fontFamily = TsType.mono,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 16.dp)
            .background(Color(0x60_C5BFAE))
    )
}

// ── Version / storage helpers ──────────────────────────────────

private fun appVersion(context: android.content.Context): String = try {
    val pm = context.packageManager
    val info = pm.getPackageInfo(context.packageName, 0)
    info.versionName ?: "—"
} catch (_: Exception) { "—" }

private fun computeStorageLabel(context: android.content.Context): String {
    val dir = File(context.filesDir, "gpx_kml")
    if (!dir.exists()) return "0 KB"
    val bytes = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
