# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (ProGuard enabled)
./gradlew compileFilamentMaterials  # Compile .mat → .filamat shader assets
./gradlew clean                  # Clean build outputs
```

Material compilation (`compileFilamentMaterials`) downloads the Filament `matc` compiler (v1.69.4) automatically and compiles `.mat` files from `app/src/main/materials/` into `.filamat` assets at `app/src/main/assets/materials/`. This runs automatically before builds.

No tests or lint tooling are currently configured.

## Architecture

**AR navigation app** using ARCore + Filament + Mapbox. MVVM with Jetpack Compose UI.

### Core Flow

GPS location → Mapbox route fetch → Route projected to local meters → ARCore camera tracking → Filament renders 3D chevrons + path overlay on camera feed

### Key Packages

- **`ar/`** — ARCore session lifecycle (`ArSessionManager`), heading extraction (`ArCoreHeading`), geometry/chevron positioning (`ArrowMeshFactory`)
- **`ar/filament/`** — Filament rendering engine. `FilamentArRenderer` orchestrates per-frame: syncs ARCore camera matrices, updates chevron transforms via `ChevronEntityManager`, renders ground path via `NavPathManager`, draws camera background via `CameraBackgroundManager`
- **`navigation/`** — `NavigationViewModel` is the central state manager. `NavigationState` is the immutable state model (route, GPS, calibration, distances, ETA). UI observes via StateFlow.
- **`location/`** — GPS via Fused Location Provider with Kalman filter smoothing. Adaptive polling (2s on-route, 1s off-route).
- **`route/`** — Mapbox Directions API client (`RouteRepository`), geocoding (`GeocodingService`), snap-to-route projection (`SnapToRoute`)
- **`geo/`** — `CoordinateConverter` converts GPS ↔ local meters using flat-earth approximation. Origin locked at first GPS fix.
- **`ui/`** — Compose screens: `ArScreen` (main AR view + overlays), `DestinationPickerScreen`, turn instructions, minimap, compass

### Coordinate System

GPS (lat/lng) → local meters via `CoordinateConverter` (flat-earth, valid ~1km). ARCore convention: right-handed, Y-up, -Z forward. GPS→AR transform built per-frame in `FilamentArRenderer.updateChevrons()` using heading calibration offset.

### Filament Rendering Pipeline

1. `FilamentArRenderer.doFrame()` called from Choreographer
2. ARCore `session.update()` (requires current EGL context)
3. Camera background rendered as YUV→RGB via `camera_background.mat`
4. Chevrons: pool of 40 entities with shared geometry, distance-based animation states, FLOAT3 normals in TANGENTS attribute
5. Path: ribbon mesh with glow layer (priority 4) + occluded layer (priority 5, reads ARCore depth)
6. Render order controlled by priority values (lower = drawn first)

### Materials (`.mat` → `.filamat`)

- `camera_background` — YUV→RGB, unlit opaque
- `turn_arrow` — Lit PBR, rim glow + emissive pulse. Params: `baseColor(float4)`, `time(float)`, `rimStrength(float)`
- `nav_path_glow` — Additive blending, flowing animation, near/far color gradient
- `nav_path_occluded` — Depth-tested dashed lines, reduces opacity behind geometry
- `nav_chevrons` — Unlit emissive arrows (legacy, being replaced by turn_arrow)

### Vertex Format Convention

Filament auto-generates tangent frames from FLOAT3 normals. Use `TANGENTS` attribute with `AttributeType.FLOAT3` (normals), not FLOAT4 quaternions. Reference: `PathMeshBuilder` in the STS reference repo.

### ARCore Heading Calibration

`ArCoreHeading` extracts yaw from ARCore camera pose. `NavigationViewModel` bridges ARCore yaw to GPS heading via exponential smoothing (0.2 factor for first 10 updates, then 0.05). The calibration angle offsets all GPS→AR transforms.

## Key Dependencies

- ARCore 1.44.0, Filament 1.69.4, Mapbox Maps 11.18.2
- Kotlin 2.0.21, Compose BOM 2024.12.01, compileSdk 36, minSdk 24
- OkHttp 4.12.0, Moshi 1.15.1, Coroutines 1.9.0

## Conventions

- Filament objects must be destroyed in reverse creation order (entity → buffer → material)
- Material parameters are set per-frame in update loops, not cached
- `NavigationState` is immutable; all mutations go through `NavigationViewModel._state.update {}`
- ProGuard keeps ARCore, Filament, and route model classes (see `proguard-rules.pro`)
- Demo mode (`NavigationViewModel.injectDemoRoute()`) provides a mock route for testing without GPS

## File management KML/GPX

Handles all file I/O:

- Copies files from device URIs into app-private storage (filesDir/gpx_kml/)
- Persists metadata as JSON
- Validates extension (.gpx/.kml only), size (≤ 5 MB), and XML content (checks for <gpx or <kml  
  header)
- Returns clear error messages for each failure case
