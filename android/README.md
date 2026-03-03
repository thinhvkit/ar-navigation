# AR Navigation — Android

Augmented reality pedestrian navigation app that overlays 3D directional arrows and a glowing path onto the real-world camera feed. Built with ARCore, Filament, and Mapbox.

## How It Works

1. GPS fix establishes a local coordinate origin
2. User picks a destination via search or map tap
3. Mapbox Directions API returns a walking route
4. Route coordinates are projected into local meters
5. ARCore tracks the device's position and orientation
6. Filament renders animated 3D chevrons and a ground path ribbon on the camera feed
7. ARCore Depth API hides virtual content behind real walls and objects

## Screenshots

<!-- TODO: Add screenshots -->

## Requirements

- Android device with ARCore support
- Android 7.0+ (API 24)
- GPS / location services enabled
- Mapbox API token (for routing and geocoding)

## Setup

### 1. Clone and open in Android Studio

```bash
git clone <repo-url>
cd ar-navigation/android
```

Open the `android/` directory in Android Studio.

### 2. Configure Mapbox token

Add your Mapbox downloads token to `~/.gradle/gradle.properties`:

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.your_mapbox_secret_token
```

The app also requires a Mapbox public access token at runtime — set it in `ArNavApp.kt`.

### 3. Build

```bash
./gradlew assembleDebug
```

This automatically downloads the Filament `matc` shader compiler (v1.69.4) and compiles all `.mat` materials into `.filamat` assets.

### 4. Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew assembleDebug` | Build debug APK |
| `./gradlew assembleRelease` | Build release APK (ProGuard enabled) |
| `./gradlew compileFilamentMaterials` | Compile `.mat` → `.filamat` shader assets |
| `./gradlew clean` | Clean build outputs |

## Architecture

MVVM with Jetpack Compose. The app is organized into the following packages:

```
com.ideals.arnav/
├── ar/                     # ARCore integration
│   ├── ArSessionManager    # Session lifecycle (create/resume/pause/close)
│   ├── ArCoreHeading       # Yaw extraction from camera pose
│   ├── ArrowMeshFactory    # Chevron sampling along route polyline
│   └── filament/           # Filament rendering engine
│       ├── FilamentArRenderer      # Per-frame orchestrator
│       ├── CameraBackgroundManager # YUV→RGB camera feed
│       ├── ChevronEntityManager    # Pool of 40 animated 3D arrow entities
│       ├── NavPathManager          # Ground path ribbon (glow + occluded layers)
│       ├── DepthTextureManager     # ARCore depth → Filament texture for occlusion
│       └── MaterialLoader          # .filamat asset loading
├── navigation/             # State management
│   ├── NavigationViewModel # Central state (StateFlow), calibration, route logic
│   └── NavigationState     # Immutable state model
├── location/               # GPS
│   ├── LocationManager     # Fused Location Provider, adaptive polling
│   └── KalmanFilter        # GPS position smoothing
├── route/                  # Routing
│   ├── RouteRepository     # Mapbox Directions API client
│   ├── GeocodingService    # Address → coordinates
│   ├── SnapToRoute         # Snap GPS position to nearest route segment
│   └── RouteModels         # Data classes for API responses
├── geo/                    # Coordinate math
│   └── CoordinateConverter # GPS ↔ local meters (flat-earth, ~1km range)
└── ui/                     # Compose UI
    ├── ArScreen            # Main AR view with overlays
    ├── DestinationPickerScreen # Search / map destination selection
    ├── TurnInstructionCard # Turn-by-turn banner
    ├── MiniMapView         # Mapbox mini-map overlay
    ├── CompassIndicator    # Heading compass
    ├── ManeuverIcon        # Turn arrow icons
    ├── RouteProgressBar    # Distance/ETA progress
    └── OffRouteIndicator   # Off-route warning
```

## Rendering Pipeline

Each frame (`FilamentArRenderer.doFrame()`):

1. Make EGL context current for ARCore
2. `session.update()` — get latest camera frame
3. Acquire depth image → upload to R32F Filament texture
4. Render camera background (YUV→RGB)
5. Sync ARCore camera matrices → Filament camera
6. Update 3D chevron positions, rotations, and animation states
7. Update ground path ribbon mesh and transform
8. Render: background view first, then AR content view on top

### Depth Occlusion

Virtual content (arrows and path) hides behind real-world geometry using ARCore's Depth API:

- `DepthTextureManager` acquires 16-bit depth images each frame and uploads them as R32F Filament textures
- Arrow material (`turn_arrow.mat`) and path occluded material (`nav_path_occluded.mat`) sample the depth texture in the fragment shader
- Virtual fragments behind real surfaces render at reduced opacity (ghost effect, `occlusionAlpha = 0.15`)
- Devices without depth support fall back gracefully — everything renders at full opacity

### Materials

| Material | Purpose |
|----------|---------|
| `camera_background` | YUV→RGB camera feed, unlit opaque |
| `turn_arrow` | 3D arrow chevrons — emissive pulse, depth occlusion |
| `nav_path_glow` | Ground path — additive blending, flowing animation |
| `nav_path_occluded` | Ground path — depth-tested dashed lines, ghost through walls |
| `nav_chevrons` | Legacy unlit arrows (being replaced by `turn_arrow`) |

## Coordinate System

- **GPS** (lat/lng) → **local meters** via `CoordinateConverter` (flat-earth approximation, valid ~1km)
- **ARCore**: right-handed, Y-up, -Z forward
- **GPS→AR transform**: built per-frame using heading calibration offset (ARCore yaw + exponential-smoothed GPS heading)

## Key Dependencies

| Dependency | Version |
|------------|---------|
| ARCore | 1.44.0 |
| Filament | 1.69.4 |
| Mapbox Maps | 11.18.2 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| OkHttp | 4.12.0 |
| Moshi | 1.15.1 |
| Coroutines | 1.9.0 |
| Play Services Location | 21.3.0 |

**Android**: compileSdk 36, minSdk 24, targetSdk 36, Java 17

## Demo Mode

For testing without GPS or a real route, call `NavigationViewModel.injectDemoRoute()` to load a mock route.
