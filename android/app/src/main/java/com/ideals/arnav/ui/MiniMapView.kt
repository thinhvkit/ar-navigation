package com.ideals.arnav.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ideals.arnav.route.LatLng
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import kotlin.math.cos
import kotlin.math.sin

private val MAP_SIZE = 180.dp

// Apple-style colors
private val ROUTE_COLOR = Color(0xFF007AFF)  // Apple Blue
private val START_COLOR = Color(0xFF34C759)  // Apple Green
private val END_COLOR = Color(0xFFFF3B30)    // Apple Red
private val ARROW_COLOR = Color(0xFF1B3A4B)  // Dark teal (Apple Maps nav arrow)
private val ARROW_RING_COLOR = Color(0xFF4DA6FF) // Light blue ring
private val BORDER_COLOR = Color(0.88f, 0.88f, 0.88f, 0.4f)

@Composable
fun MiniMapView(
    route: List<LatLng>,
    userLat: Double,
    userLng: Double,
    heading: Double,
    hasPosition: Boolean,
    modifier: Modifier = Modifier
) {
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(userLng, userLat))
            zoom(17.0)
            bearing(heading)
        }
    }

    val mapState = rememberMapState {
        gesturesSettings = GesturesSettings {
            scrollEnabled = false
            rotateEnabled = false
            pinchToZoomEnabled = false
            doubleTapToZoomInEnabled = false
            doubleTouchToZoomOutEnabled = false
            pitchEnabled = false
        }
    }

    // Update camera when position/heading changes
    LaunchedEffect(userLat, userLng, heading) {
        if (userLat != 0.0 || userLng != 0.0) {
            mapViewportState.setCameraOptions {
                center(Point.fromLngLat(userLng, userLat))
                zoom(17.0)
                bearing(heading)
            }
        }
    }

    // Pulsing animation for user dot
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val routePoints = route.map { Point.fromLngLat(it.lng, it.lat) }

    Box(modifier = modifier.size(MAP_SIZE)) {
        MapboxMap(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .border(0.5f.dp, BORDER_COLOR, RoundedCornerShape(16.dp)),
            composeMapInitOptions = ComposeMapInitOptions(pixelRatio = 1f, textureView = true),
            mapViewportState = mapViewportState,
            mapState = mapState,
            compass = {},
            scaleBar = {},
            logo = {},
            attribution = {}
        ) {
            // Route polyline (blue)
            if (routePoints.size >= 2) {
                PolylineAnnotation(
                    points = routePoints
                ) {
                    lineColor = ROUTE_COLOR
                    lineWidth = 4.0
                    lineOpacity = 0.9
                }
            }

            // Start marker (green)
            if (routePoints.isNotEmpty()) {
                CircleAnnotation(
                    point = routePoints.first()
                ) {
                    circleRadius = 5.0
                    circleColor = START_COLOR
                    circleStrokeWidth = 1.5
                    circleStrokeColor = Color.White
                }
            }

            // End marker (red)
            if (routePoints.size >= 2) {
                CircleAnnotation(
                    point = routePoints.last()
                ) {
                    circleRadius = 5.0
                    circleColor = END_COLOR
                    circleStrokeWidth = 1.5
                    circleStrokeColor = Color.White
                }
            }
        }

        // User position: Apple Maps style navigation arrow at center
        // (map is heading-up & centered on user, so arrow always points up)
        if (hasPosition) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Pulsing blue ring
                val ringRadius = 20f
                drawCircle(
                    color = ARROW_RING_COLOR.copy(alpha = pulseAlpha * 0.5f),
                    radius = ringRadius * pulseScale,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )

                // Light blue halo
                drawCircle(
                    color = ARROW_RING_COLOR.copy(alpha = 0.2f),
                    radius = ringRadius,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )

                // iOS-style location arrow pointing up (heading direction)
                val s = 16f
                val arrowPath = Path().apply {
                    // Top tip
                    moveTo(cx, cy - s * 1.5f)
                    // Right wing
                    lineTo(cx + s, cy + s * 0.6f)
                    // V-notch center
                    lineTo(cx, cy - s * 0.15f)
                    // Left wing
                    lineTo(cx - s, cy + s * 0.6f)
                    close()
                }

                // White outline
                drawPath(arrowPath, Color.White, style = Stroke(width = 4f))
                // Dark teal fill
                drawPath(arrowPath, ARROW_COLOR, style = Fill)
            }
        }
    }
}
