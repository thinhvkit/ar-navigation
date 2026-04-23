package com.ideals.arnav.ui

import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ideals.arnav.files.GpxKmlFile
import com.ideals.arnav.files.GpxKmlParser
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import kotlin.math.abs

@Composable
fun FilePreviewScreen(
    file: GpxKmlFile,
    parsedRoute: GpxKmlParser.ParsedRoute,
    onStart: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lang by TrailSightSettings.get(context).language.collectAsState()
    val s = strings(lang)
    val layoutDir = if (lang == TrailSightSettings.LANG_HE) LayoutDirection.Rtl else LayoutDirection.Ltr

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

    // Center + draw the route polyline in TrailSight orange.
    DisposableEffect(parsedRoute) {
        try {
            if (parsedRoute.waypoints.isNotEmpty()) {
                var minLat = parsedRoute.waypoints[0].lat
                var maxLat = minLat
                var minLng = parsedRoute.waypoints[0].lng
                var maxLng = minLng
                for (w in parsedRoute.waypoints) {
                    if (w.lat < minLat) minLat = w.lat
                    if (w.lat > maxLat) maxLat = w.lat
                    if (w.lng < minLng) minLng = w.lng
                    if (w.lng > maxLng) maxLng = w.lng
                }
                val centerLng = (minLng + maxLng) / 2.0
                val centerLat = (minLat + maxLat) / 2.0
                val maxDiff = maxOf(maxLat - minLat, maxLng - minLng)
                val zoomLevel = when {
                    maxDiff > 0.5 -> 10.0
                    maxDiff > 0.1 -> 13.0
                    maxDiff > 0.01 -> 15.0
                    else -> 17.0
                }

                mapView.post {
                    try {
                        val mapboxMap = mapView.mapboxMap
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(centerLng, centerLat))
                                .zoom(zoomLevel)
                                .build()
                        )
                        mapboxMap.getStyle { style ->
                            try {
                                val routePoints = parsedRoute.waypoints
                                    .map { Point.fromLngLat(it.lng, it.lat) }
                                val lineString = LineString.fromLngLats(routePoints)
                                val sourceId = "route-source"
                                val glowLayerId = "route-layer-glow"
                                val mainLayerId = "route-layer"

                                if (style.styleLayerExists(mainLayerId)) style.removeStyleLayer(mainLayerId)
                                if (style.styleLayerExists(glowLayerId)) style.removeStyleLayer(glowLayerId)
                                if (style.styleSourceExists(sourceId)) style.removeStyleSource(sourceId)

                                style.addSource(geoJsonSource(sourceId) { geometry(lineString) })
                                // Softer underglow for depth.
                                style.addLayer(lineLayer(glowLayerId, sourceId) {
                                    lineColor("#E89167") // TrailSight orangeLt
                                    lineWidth(7.0)
                                    lineOpacity(0.35)
                                })
                                style.addLayer(lineLayer(mainLayerId, sourceId) {
                                    lineColor("#C8663B") // TrailSight orange
                                    lineWidth(4.0)
                                    lineOpacity(0.95)
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
        onDispose { }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim behind the sheet.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x73_1E1914)) // rgba(30,25,20,0.45)
                    .clickable(onClick = onClose),
            )

            // The sheet itself — rounded top corners, paper background.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(TrailSight.Paper),
            ) {
                // Grabber.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TrailSight.StoneLt)
                    )
                }

                // Header: PREVIEW TRAIL eyebrow + filename + close icon.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            s.previewTrail,
                            color = TrailSight.Moss,
                            fontFamily = TsType.mono,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${file.name}.${file.type.name.lowercase()}",
                            color = TrailSight.Bark,
                            fontFamily = TsType.mono,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close preview",
                            tint = TrailSight.Bark,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Big map preview.
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, TrailSight.StoneLine, RoundedCornerShape(16.dp)),
                ) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // "ROUTE PREVIEW" corner tag.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xC7_2B2620))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            s.routePreview,
                            color = TrailSight.Cream,
                            fontFamily = TsType.mono,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    // Faux scale bar — decorative.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TrailSight.Paper.copy(alpha = 0.85f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 2.dp)
                                .background(TrailSight.Bark, RectangleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            scaleLabel(parsedRoute.distanceMeters),
                            color = TrailSight.Bark,
                            fontFamily = TsType.mono,
                            fontSize = 9.5.sp,
                        )
                    }
                }

                // Stats grid: Distance / Elevation / Waypoints.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = s.distance,
                        value = formatKm(parsedRoute.distanceMeters),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = s.climb,
                        value = "${parsedRoute.elevationGainMeters.toInt()} m",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = s.waypoints,
                        value = "${parsedRoute.waypoints.size}",
                        modifier = Modifier.weight(1f),
                    )
                }

                val hasElevation = parsedRoute.maxElevationMeters > parsedRoute.minElevationMeters
                if (hasElevation) {
                    // ELEVATION section header with gain/loss on the right.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            s.elevation,
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
                            "${parsedRoute.elevationGainMeters.toInt()}m ↑ / ${parsedRoute.elevationLossMeters.toInt()}m ↓",
                            color = TrailSight.Stone,
                            fontFamily = TsType.mono,
                            fontSize = 10.sp,
                        )
                    }
                    // Profile canvas.
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x0F_4A6B3F))
                            .padding(10.dp),
                    ) {
                        ElevationProfile(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            samples = parsedRoute.elevationsMeters,
                            seed = seedForFile(file),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // CTA: orange pill "Start AR navigation".
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(100.dp))
                        .background(TrailSight.Orange)
                        .clickable(onClick = onStart)
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
                        s.startAr,
                        color = TrailSight.Cream,
                        fontFamily = TsType.sans,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    s.holdPhoneHint,
                    color = TrailSight.Stone,
                    fontFamily = TsType.mono,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        } // end CompositionLocalProvider
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xA6_FFFFFF))
            .border(1.dp, TrailSight.StoneLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = TrailSight.Stone,
            fontFamily = TsType.mono,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            value,
            color = TrailSight.Bark,
            fontFamily = TsType.mono,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
        )
    }
}

private fun formatKm(meters: Float): String {
    if (meters <= 0f) return "—"
    return if (meters >= 1000f) "%.1f km".format(meters / 1000f)
    else "${meters.toInt()} m"
}

private fun scaleLabel(totalMeters: Float): String {
    // Rough scale bar label that fits the route distance order of magnitude.
    val target = totalMeters / 8f
    return when {
        target >= 10000f -> "${(target / 10000f).toInt() * 10} km"
        target >= 1000f -> "${(target / 1000f).toInt()} km"
        target >= 100f -> "${(target / 100f).toInt() * 100} m"
        target >= 10f -> "${(target / 10f).toInt() * 10} m"
        else -> "500 m"
    }
}

private fun seedForFile(file: GpxKmlFile): Int {
    val h = file.id.hashCode() xor file.name.hashCode()
    return abs(h % 997) + 3
}
