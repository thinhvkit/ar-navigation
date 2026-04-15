package com.ideals.arnav.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ideals.arnav.files.GpxKmlFile
import com.ideals.arnav.files.GpxKmlParser
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource

private val AppleBlue = Color(0xFF007AFF)
private val AppleFrost = Color(0.12f, 0.12f, 0.14f, 0.72f)

@Composable
fun FilePreviewScreen(
    file: GpxKmlFile,
    parsedRoute: GpxKmlParser.ParsedRoute,
    onStart: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onStart()
                Lifecycle.Event.ON_PAUSE -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Setup map with route visualization - zoom to fit all waypoints
    DisposableEffect(parsedRoute) {
        try {
            // Calculate center and appropriate zoom level
            if (parsedRoute.waypoints.isNotEmpty()) {
                var minLat = parsedRoute.waypoints[0].lat
                var maxLat = parsedRoute.waypoints[0].lat
                var minLng = parsedRoute.waypoints[0].lng
                var maxLng = parsedRoute.waypoints[0].lng

                for (waypoint in parsedRoute.waypoints) {
                    minLat = minOf(minLat, waypoint.lat)
                    maxLat = maxOf(maxLat, waypoint.lat)
                    minLng = minOf(minLng, waypoint.lng)
                    maxLng = maxOf(maxLng, waypoint.lng)
                }

                val centerLng = (minLng + maxLng) / 2.0
                val centerLat = (minLat + maxLat) / 2.0
                val latDiff = maxLat - minLat
                val lngDiff = maxLng - minLng
                val maxDiff = maxOf(latDiff, lngDiff)
                val zoomLevel = when {
                    maxDiff > 0.5 -> 10.0
                    maxDiff > 0.1 -> 13.0
                    maxDiff > 0.01 -> 15.0
                    else -> 17.0
                }

                // Use post to allow map to be ready
                mapView.post {
                    try {
                        val mapboxMap = mapView.mapboxMap
                        val cameraOptions = com.mapbox.maps.CameraOptions.Builder()
                            .center(Point.fromLngLat(centerLng, centerLat))
                            .zoom(zoomLevel)
                            .build()
                        mapboxMap.setCamera(cameraOptions)

                        // Draw polyline once style is loaded
                        mapboxMap.getStyle { style ->
                            try {
                                val routePoints = parsedRoute.waypoints
                                    .map { Point.fromLngLat(it.lng, it.lat) }
                                val lineString = LineString.fromLngLats(routePoints)
                                val sourceId = "route-source"
                                val layerId = "route-layer"

                                // Remove existing if present
                                if (style.styleLayerExists(layerId)) style.removeStyleLayer(layerId)
                                if (style.styleSourceExists(sourceId)) style.removeStyleSource(sourceId)

                                style.addSource(geoJsonSource(sourceId) { geometry(lineString) })
                                style.addLayer(lineLayer(layerId, sourceId) {
                                    lineColor("#007AFF")
                                    lineWidth(4.0)
                                    lineOpacity(0.9)
                                })
                            } catch (e: Exception) {
                                Log.d("FilePreviewScreen", "Error drawing polyline: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("FilePreviewScreen", "Error setting camera: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("FilePreviewScreen", "Error in DisposableEffect: ${e.message}")
        }
        onDispose {
            // Cleanup handled by lifecycle
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
        ) {
            // Top toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text("Close", color = AppleBlue, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Route Preview",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text("Start", fontSize = 14.sp)
                }
            }

            HorizontalDivider(color = Color(0xFF3A3A3C))

            // Map preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            HorizontalDivider(color = Color(0xFF3A3A3C))

            // Route details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                DetailRow(label = "File", value = file.name)
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow(label = "Type", value = file.type.name)
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow(label = "Size", value = formatFileSize(file.sizeBytes))
                Spacer(modifier = Modifier.height(12.dp))

                // Distance & Time section
                DetailRow(
                    label = "Distance",
                    value = "%.2f km".format(parsedRoute.distanceMeters / 1000f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                DetailRow(
                    label = "Est. Time",
                    value = formatDuration((parsedRoute.distanceMeters / 1.4).toInt()) // ~1.4 m/s walking
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Elevation section (if available)
                if (parsedRoute.maxElevationMeters > parsedRoute.minElevationMeters) {
                    DetailRow(label = "Elevation", value = "")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 80.dp, top = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                "↑ Gain",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "%.0f m".format(parsedRoute.elevationGainMeters),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column {
                            Text(
                                "↓ Loss",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "%.0f m".format(parsedRoute.elevationLossMeters),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column {
                            Text(
                                "Max",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "%.0f m".format(parsedRoute.maxElevationMeters),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                DetailRow(label = "Waypoints", value = "${parsedRoute.waypoints.size}")

                if (!parsedRoute.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        parsedRoute.description!!,
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2C2C2E))
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color(0xFF8E8E93),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "%.1fKB".format(bytes / 1024.0)
        else -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "%dh %dm".format(hours, minutes)
        minutes > 0 -> "%dm".format(minutes)
        else -> "<1m"
    }
}
