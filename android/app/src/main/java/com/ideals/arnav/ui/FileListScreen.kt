package com.ideals.arnav.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ideals.arnav.files.FileListViewModel
import com.ideals.arnav.files.GpxKmlFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun FileListScreen(
    onDismiss: () -> Unit,
    onFileSelected: (GpxKmlFile) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: FileListViewModel = viewModel()
    val files by viewModel.files.collectAsState()
    val error by viewModel.error.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    var fileToDelete by remember { mutableStateOf<GpxKmlFile?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val lang by rememberLanguage(context)
    val s = strings(lang)
    val layoutDir = if (lang == TrailSightSettings.LANG_HE) LayoutDirection.Rtl else LayoutDirection.Ltr

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.uploadFile(it) }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TrailSight.Paper)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TsHeader(
                    appName = s.appName,
                    subtitle = if (files.isEmpty()) s.noRoutes
                    else s.routesCountTemplate.replace("{n}", files.size.toString()),
                    onClose = onDismiss,
                    onSettings = { showSettings = true },
                )

                if (files.isEmpty()) {
                    EmptyLibrary(
                        modifier = Modifier.weight(1f),
                        strings = s,
                        onUpload = { filePicker.launch(arrayOf("*/*")) },
                    )
                } else {
                    val resume = files.first()
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 0.dp, end = 0.dp, top = 0.dp, bottom = 120.dp
                        ),
                    ) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                ResumeCard(
                                    file = resume,
                                    resumeLabel = s.resumeLast,
                                    onOpen = {
                                        onFileSelected(resume)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                        item { SectionDivider(allRoutesLabel = s.allRoutes, dateLabel = s.dateAdded) }
                        items(files, key = { it.id }) { file ->
                            FileRow(
                                file = file,
                                deleteLabel = s.deleteAction,
                                onClick = {
                                    onFileSelected(file)
                                    onDismiss()
                                },
                                onDelete = { fileToDelete = file },
                            )
                        }
                    }
                }
            }

            // Orange floating action button (upload).
            UploadFab(
                isUploading = isUploading,
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 42.dp),
            )
        }
        } // end CompositionLocalProvider
    }

    fileToDelete?.let { file ->
        TsDeleteDialog(
            file = file,
            strings = s,
            onCancel = { fileToDelete = null },
            onConfirm = {
                viewModel.deleteFile(file.id)
                fileToDelete = null
            },
        )
    }

    if (showSettings) {
        SettingsScreen(onDismiss = { showSettings = false })
    }
}

// ── Header ─────────────────────────────────────────────────────

@Composable
private fun TsHeader(
    appName: String,
    subtitle: String,
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 28.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                appName,
                color = TrailSight.Bark,
                fontFamily = TsType.sans,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                color = TrailSight.Stone,
                fontFamily = TsType.mono,
                fontSize = 9.5.sp,
                letterSpacing = 0.5.sp,
            )
        }
        TsIconBtn(Icons.Outlined.Search, contentDescription = "Search")
        TsIconBtn(Icons.Outlined.Settings, contentDescription = "Settings", onClick = onSettings)
        TsIconBtn(Icons.Outlined.Close, contentDescription = "Close library", onClick = onClose)
    }
}

@Composable
private fun TsIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {},
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = TrailSight.Bark, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp = 32.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s / 2f - 0.5f
        // Moss circle with a slightly darker outline.
        drawCircle(TrailSight.Moss, radius = r, center = Offset(cx, cy))
        drawCircle(TrailSight.MossDark, radius = r, center = Offset(cx, cy), style = Stroke(width = 1f))
        // Topographic peaks (cream) — viewBox 32 mapped to actual size.
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
        // Amber "sun".
        drawCircle(TrailSight.Amber, radius = 2f * k, center = Offset(22f * k, 9f * k))
    }
}

// ── Resume card ────────────────────────────────────────────────

@Composable
private fun ResumeCard(file: GpxKmlFile, resumeLabel: String, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TrailSight.Bark)
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TrailSight.Amber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        resumeLabel,
                        color = TrailSight.Amber,
                        fontFamily = TsType.mono,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${file.name}.${file.type.name.lowercase()}",
                    color = TrailSight.Cream,
                    fontFamily = TsType.mono,
                    fontSize = 14.sp,
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
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, TrailSight.Cream.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            ) {
                TrailSightMiniMap(modifier = Modifier.fillMaxSize(), seed = seedFor(file))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(TrailSight.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "Resume",
                    tint = TrailSight.Cream,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Section divider ────────────────────────────────────────────

@Composable
private fun SectionDivider(allRoutesLabel: String, dateLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            allRoutesLabel,
            color = TrailSight.Stone,
            fontFamily = TsType.mono,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(TrailSight.StoneHair)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            dateLabel,
            color = TrailSight.Stone,
            fontFamily = TsType.mono,
            fontSize = 10.sp,
        )
    }
}

// ── File row ───────────────────────────────────────────────────

@Composable
private fun FileRow(
    file: GpxKmlFile,
    deleteLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isGpx = file.type == GpxKmlFile.FileType.GPX
    val badgeColor = if (isGpx) TrailSight.Moss else TrailSight.Orange

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, TrailSight.StoneLine, RoundedCornerShape(10.dp))
        ) {
            TrailSightMiniMap(modifier = Modifier.fillMaxSize(), seed = seedFor(file))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.name,
                    color = TrailSight.Bark,
                    fontFamily = TsType.mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, badgeColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        file.type.name,
                        color = badgeColor,
                        fontFamily = TsType.mono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row {
                Text(
                    formatFileSize(file.sizeBytes),
                    color = TrailSight.Stone,
                    fontFamily = TsType.sans,
                    fontSize = 11.5.sp,
                )
                Text(" · ", color = TrailSight.Stone, fontSize = 11.5.sp)
                Text(
                    formatDate(file.uploadedAt),
                    color = TrailSight.Stone,
                    fontFamily = TsType.sans,
                    fontSize = 11.5.sp,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More",
                    tint = TrailSight.Stone,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(deleteLabel, color = TrailSight.Danger) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = TrailSight.Danger)
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

// ── Empty state ────────────────────────────────────────────────

@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    strings: TrailSightStrings,
    onUpload: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val w = size.width
            val cx = w / 2f
            val cy = w / 2f

            // Outer dashed compass ring.
            drawCircle(
                color = TrailSight.StoneLt,
                radius = 80f / 180f * w,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(2f, 4f)
                    )
                ),
            )
            // Inner faint moss ring.
            drawCircle(
                color = TrailSight.Moss.copy(alpha = 0.35f),
                radius = 55f / 180f * w,
                center = Offset(cx, cy),
                style = Stroke(width = 1f),
            )
            // Topographic peak silhouette.
            val k = w / 180f
            val peaks = Path().apply {
                moveTo(cx - 40f * k, cy + 30f * k)
                lineTo(cx - 20f * k, cy - 5f * k)
                lineTo(cx - 5f * k, cy + 15f * k)
                lineTo(cx + 12f * k, cy - 18f * k)
                lineTo(cx + 38f * k, cy + 28f * k)
                close()
            }
            drawPath(peaks, color = TrailSight.Moss.copy(alpha = 0.22f))
            drawPath(
                peaks,
                color = TrailSight.Moss,
                style = Stroke(width = 1.8f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
            // Amber "sun" on the highest peak.
            drawCircle(
                TrailSight.Amber,
                radius = 3.5f * k,
                center = Offset(cx + 25f * k, cy - 22f * k),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            strings.emptyTitle,
            color = TrailSight.Bark,
            fontFamily = TsType.sans,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            strings.emptyBody,
            color = TrailSight.BarkSoft,
            fontFamily = TsType.sans,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onUpload,
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrailSight.Bark),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Icon(
                Icons.Outlined.FileUpload,
                contentDescription = null,
                tint = TrailSight.Cream,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                strings.uploadFile,
                color = TrailSight.Cream,
                fontFamily = TsType.sans,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            strings.maxSizeHint,
            color = TrailSight.Stone,
            fontFamily = TsType.mono,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
        )
    }
}

// ── Upload FAB ─────────────────────────────────────────────────

@Composable
private fun UploadFab(
    isUploading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(TrailSight.Orange)
            .clickable(enabled = !isUploading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isUploading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = TrailSight.Cream,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Upload file",
                tint = TrailSight.Cream,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ── Delete confirm dialog ──────────────────────────────────────

@Composable
private fun TsDeleteDialog(
    file: GpxKmlFile,
    strings: TrailSightStrings,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = TrailSight.Paper,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TrailSight.DangerTint18),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = TrailSight.Danger,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    strings.deleteTitle,
                    color = TrailSight.Bark,
                    fontFamily = TsType.sans,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    strings.deleteBody,
                    color = TrailSight.BarkSoft,
                    fontFamily = TsType.sans,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x14_4A6B3F)) // rgba(74,107,63,0.08)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(7.dp))
                    ) {
                        TrailSightMiniMap(modifier = Modifier.fillMaxSize(), seed = seedFor(file))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${file.name}.${file.type.name.lowercase()}",
                            color = TrailSight.Bark,
                            fontFamily = TsType.mono,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            formatFileSize(file.sizeBytes),
                            color = TrailSight.Stone,
                            fontFamily = TsType.mono,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = TrailSight.Danger,
                    contentColor = TrailSight.Cream,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
            ) {
                Text(
                    strings.deleteAction,
                    fontFamily = TsType.sans,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TrailSight.Cream,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(100.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TrailSight.Bark),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
            ) {
                Text(
                    strings.cancel,
                    color = TrailSight.Bark,
                    fontFamily = TsType.sans,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

// ── Helpers ────────────────────────────────────────────────────

private fun seedFor(file: GpxKmlFile): Int {
    // Stable per-file seed so the tile doesn't shuffle between renders.
    val h = file.id.hashCode() xor file.name.hashCode()
    return abs(h % 997) + 3
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
