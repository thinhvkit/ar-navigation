package com.ideals.arnav.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.ideals.arnav.navigation.NavigationState
import com.ideals.arnav.navigation.NavigationViewModel


// Apple-style colors
private val AppleBlue = Color(0xFF007AFF)
private val AppleFrost = Color(0.12f, 0.12f, 0.14f, 0.72f)
private val AppleGreen = Color(0xFF34C759)
private val AppleYellow = Color(0xFFFFCC00)

@Composable
fun ArScreen(viewModel: NavigationViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDestinationPicker by remember { mutableStateOf(false) }
    var showFileList by remember { mutableStateOf(false) }

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

                OffRouteIndicator(isOffRoute = state.isOffRoute)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppleFrost)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = state.statusMessage,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (state.gpsSignalLost) {
                Text(
                    text = "GPS signal lost",
                    color = AppleYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleFrost)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (state.gpsAccuracy > 0 && !state.gpsSignalLost) {
                Text(
                    text = "GPS ±%.0fm".format(state.gpsAccuracy),
                    color = if (state.gpsAccuracy < 10) AppleGreen else AppleYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleFrost)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Screenshot button (top-right)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(AppleFrost)
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
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Files button (above destination button, bottom-right)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 76.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(AppleFrost)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showFileList = true })
                }
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = "Route files",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Destination button (bottom-right)
        val isWaiting = state.phase == NavigationState.Phase.WAITING_FOR_GPS
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
        }
    }

    if (showDestinationPicker) {
        DestinationPickerScreen(
            viewModel = viewModel,
            onDismiss = { showDestinationPicker = false }
        )
    }

    if (showFileList) {
        FileListScreen(onDismiss = { showFileList = false })
    }
}
