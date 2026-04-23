package com.ideals.arnav.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ideals.arnav.ar.filament.FilamentArSurfaceView
import com.ideals.arnav.files.GpxKmlFile
import com.ideals.arnav.files.GpxKmlParser
import com.ideals.arnav.files.SelectedFileManager
import com.ideals.arnav.navigation.NavigationState
import com.ideals.arnav.navigation.NavigationViewModel
import java.io.File


// TrailSight AR chrome colours (bark-tinted frost).
private val BarkFrost = Color(0xB3_0F1412)       // rgba(15,20,18,0.7)
private val FrostBorder = Color(0x2E_F4EDE0)     // rgba(244,237,224,0.18)
private val FrostBorderSoft = Color(0x26_F4EDE0) // rgba(244,237,224,0.15)
private val StatusOkAmber = Color(0xFFE8B04A)
private val StatusOkMoss = Color(0xFF6B8560)

@Composable
fun ArScreen(
    viewModel: NavigationViewModel,
    openLibraryOnStart: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lang by TrailSightSettings.get(context).language.collectAsState()
    val s = strings(lang)
    val isRtl = lang == TrailSightSettings.LANG_HE
    var showDestinationPicker by remember { mutableStateOf(false) }
    var showFileList by remember { mutableStateOf(openLibraryOnStart) }
    var selectedFile by remember { mutableStateOf<GpxKmlFile?>(null) }
    var parsedRoute by remember { mutableStateOf<GpxKmlParser.ParsedRoute?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- Filament AR SurfaceView ----
        val filamentView = remember {
            FilamentArSurfaceView(context, viewModel)
        }

        // Push state whenever it changes
        LaunchedEffect(state) {
            filamentView.setNavigationState(state)
        }

        // Lifecycle: resume/pause the FilamentArSurfaceView
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> filamentView.onResume()
                    Lifecycle.Event.ON_PAUSE -> filamentView.onPause()
                    Lifecycle.Event.ON_DESTROY -> filamentView.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                filamentView.onDestroy()
            }
        }

        AndroidView(
            factory = { filamentView },
            modifier = Modifier.fillMaxSize()
        )

        // Mini map with compass arrow overlay (bottom-left)
        MiniMapView(
            route = state.route,
            userLat = state.userLat,
            userLng = state.userLng,
            heading = state.heading,
            hasPosition = true,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Top HUD: navigation turn card + progress, or status pill when not navigating
        val isNavigating = state.phase == NavigationState.Phase.NAVIGATING ||
            state.phase == NavigationState.Phase.RECALCULATING

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp, start = 16.dp, end = 60.dp)
        ) {
            if (isNavigating && state.turnSteps.isNotEmpty()) {
                AnimatedContent(
                    targetState = state.currentStepIndex,
                    transitionSpec = {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    },
                    label = "turnCard"
                ) { stepIndex ->
                    val step = state.turnSteps.getOrNull(stepIndex) ?: state.turnSteps.last()
                    TurnInstructionCard(
                        step = step,
                        distanceToNextTurn = state.distanceToNextTurn,
                        isOffRoute = state.isOffRoute,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                RouteProgressBar(
                    distanceAlongRoute = state.distanceAlongRoute,
                    totalRouteDistance = state.totalRouteDistance,
                    distanceRemaining = state.distanceRemaining,
                    etaSeconds = state.etaSeconds,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OffRouteIndicator(isOffRoute = state.isOffRoute, text = s.offRouteLabel)

            } else {
                // Idle / waiting — TrailSight status pill.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(BarkFrost)
                        .border(1.dp, FrostBorderSoft, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = state.statusMessage,
                        color = TrailSight.Cream,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (state.gpsSignalLost) {
                Text(
                    text = "GPS signal lost",
                    color = StatusOkAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BarkFrost)
                        .border(1.dp, FrostBorderSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (state.gpsAccuracy > 0 && !state.gpsSignalLost) {
                Text(
                    text = "GPS ±%.0fm".format(state.gpsAccuracy),
                    color = if (state.gpsAccuracy < 10) StatusOkMoss else StatusOkAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BarkFrost)
                        .border(1.dp, FrostBorderSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // ── Top-left: Exit pill (when a file-based trail is active) ──
        if (selectedFile != null) {
            TsArPillButton(
                text = s.exit,
                icon = Icons.Outlined.Close,
                onClick = {
                    val cleared = selectedFile
                    selectedFile = null
                    parsedRoute = null
                    viewModel.resetNavigation()
                    if (cleared != null) {
                        SelectedFileManager(context).clearSelected()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 42.dp, start = 14.dp)
            )
        }

        // ── Top-right: heading pill (always visible) ──
        TsHeadingPill(
            headingDegrees = state.heading,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 42.dp, end = 72.dp)
        )

        // ── Trail name strip (centered, below top chrome) ──
        selectedFile?.let { file ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
            ) {
                TsTrailNameStrip(name = "${file.name}.${file.type.name.lowercase()}")
            }
        }

        // Screenshot button (top-right) — tap = screenshot, long-press = demo mode.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 38.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(BarkFrost)
                .border(1.dp, FrostBorder, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            Toast.makeText(context, "Screenshot captured", Toast.LENGTH_SHORT).show()
                        },
                        onLongPress = {
                            viewModel.enableDemoMode()
                            Toast.makeText(context, "Demo mode enabled", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = "Screenshot",
                tint = TrailSight.Cream,
                modifier = Modifier.size(18.dp)
            )
        }

        // Files button (bottom-right, above the old destination area).
        TsArCircleButton(
            icon = Icons.Outlined.FolderOpen,
            contentDescription = "Route files",
            onClick = { showFileList = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 76.dp, end = 16.dp),
        )

        // Bottom HUD: three stat pills (when navigating).
        val isNavigatingForHud = state.phase == NavigationState.Phase.NAVIGATING ||
            state.phase == NavigationState.Phase.RECALCULATING
        if (isNavigatingForHud) {
            TsArBottomHud(
                nextLabel = s.hudNext,
                nextValue = formatDistanceMetersTs(state.distanceToNextTurn),
                remainingLabel = s.hudRemaining,
                remainingValue = formatDistanceMetersTs(state.distanceRemaining),
                thirdLabel = s.hudEta,
                thirdValue = formatEtaTs(state.etaSeconds),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 150.dp),
            )
        }

        // Destination button (bottom-right)
        /* val isWaiting = state.phase == NavigationState.Phase.WAITING_FOR_GPS
        Button(
            onClick = { showDestinationPicker = true },
            enabled = !isWaiting,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppleBlue,
                disabledContainerColor = Color(0xFF3A3A3C)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (state.route.isEmpty()) "Set destination" else "New destination",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp)
            )
        } */
    }

    if (showDestinationPicker) {
        DestinationPickerScreen(
            viewModel = viewModel,
            onDismiss = { showDestinationPicker = false }
        )
    }

    if (showFileList) {
        FileListScreen(
            onDismiss = { showFileList = false },
            onFileSelected = { file ->
                selectedFile = file
                // Parse the file to get route waypoints
                try {
                    val fileContent = File(file.storedPath).readText()
                    parsedRoute = when (file.type) {
                        GpxKmlFile.FileType.GPX -> GpxKmlParser.parseGpx(fileContent)
                        GpxKmlFile.FileType.KML -> GpxKmlParser.parseKml(fileContent)
                    }
                    if (parsedRoute != null) {
                        showPreview = true
                    } else {
                        Toast.makeText(context, "Failed to parse file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showPreview && selectedFile != null && parsedRoute != null) {
        FilePreviewScreen(
            file = selectedFile!!,
            parsedRoute = parsedRoute!!,
            onStart = {
                viewModel.setRouteFromFile(selectedFile!!, parsedRoute!!)
                SelectedFileManager(context).saveSelectedFile(selectedFile!!.id)
                showPreview = false
            },
            onClose = {
                showPreview = false
                selectedFile = null
                parsedRoute = null
            }
        )
    }
}
