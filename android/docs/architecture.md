# Architecture

## Overview

The app follows MVVM with Jetpack Compose. A single `NavigationViewModel` manages all navigation state, while the Filament rendering pipeline runs on the GL thread independently.

```
┌──────────────────────────────────────────────────────────────┐
│  UI Layer (Compose)                                          │
│  ArScreen, DestinationPickerScreen, TurnInstructionCard, ... │
│  Observes NavigationState via StateFlow                      │
└───────────────────────┬──────────────────────────────────────┘
                        │ StateFlow<NavigationState>
┌───────────────────────▼──────────────────────────────────────┐
│  NavigationViewModel                                         │
│  Central state manager — route, GPS, calibration, ETA        │
│  Owns: LocationManager, RouteRepository, GeocodingService    │
└──┬──────────────┬──────────────┬─────────────────────────────┘
   │              │              │
   ▼              ▼              ▼
┌──────┐   ┌───────────┐   ┌──────────┐
│ GPS  │   │ Mapbox API│   │ ARCore   │
│ Fused│   │ Directions│   │ Session  │
│ Loc. │   │ Geocoding │   │ Manager  │
└──────┘   └───────────┘   └────┬─────┘
                                │
┌───────────────────────────────▼──────────────────────────────┐
│  Filament Rendering (GL Thread)                              │
│  FilamentArRenderer.doFrame() — per-frame orchestrator       │
│  ├── DepthTextureManager — depth occlusion                   │
│  ├── CameraBackgroundManager — camera feed                   │
│  ├── ChevronEntityManager — 40 animated 3D arrows            │
│  └── NavPathManager — ground path ribbon                     │
└──────────────────────────────────────────────────────────────┘
```

## Navigation State Machine

```
WAITING_FOR_GPS → SELECTING_DESTINATION → FETCHING_ROUTE → NAVIGATING
                                                              │
                                                              ▼
                                                        RECALCULATING
                                                              │
                                                              ▼
                                                          NAVIGATING
```

`NavigationState` is an immutable data class. All mutations go through:

```kotlin
_state.update { it.copy(field = newValue) }
```

### State Fields

| Field | Type | Description |
|-------|------|-------------|
| `phase` | `Phase` | Current navigation phase |
| `userLat`, `userLng` | `Double` | Raw GPS position |
| `smoothedLat`, `smoothedLng` | `Double` | Kalman-filtered position |
| `heading` | `Double` | GPS heading in degrees |
| `route` | `List<LatLng>` | Route polyline from Mapbox |
| `routeWorldPositions` | `List<FloatArray>` | Route in local meters |
| `currentSegment` | `Int` | Nearest route segment index |
| `distanceToRoute` | `Float` | Distance from user to nearest route point |
| `turnSteps` | `List<TurnStep>` | Turn-by-turn instructions |
| `currentStepIndex` | `Int` | Current turn step |
| `distanceToNextTurn` | `Float` | Meters to next maneuver |
| `distanceRemaining` | `Float` | Total remaining distance |
| `etaSeconds` | `Double` | Estimated time of arrival |
| `isOffRoute` | `Boolean` | User is too far from route |
| `calibrationAngle` | `Double` | ARCore→GPS heading offset |
| `calibrationInitialized` | `Boolean` | Calibration complete |

## Data Flow

### GPS → AR Rendering

1. `LocationManager` collects fused location updates
2. `KalmanFilter` smooths raw GPS positions
3. `CoordinateConverter.gpsToLocal()` converts to local meters
4. `SnapToRoute` projects position onto nearest route segment
5. `NavigationViewModel` updates state with distances, ETA, current step
6. `FilamentArRenderer` reads `currentState` and builds GPS→AR transform
7. `ChevronEntityManager` positions arrows; `NavPathManager` positions path

### ARCore Heading Calibration

The AR coordinate frame and GPS heading must be aligned:

1. `ArCoreHeading.extractYawDegrees()` gets yaw from ARCore camera pose
2. `NavigationViewModel` receives both ARCore yaw and GPS heading
3. Calibration angle = GPS heading - ARCore yaw
4. Exponential smoothing: factor 0.2 for first 10 updates, then 0.05
5. The calibration offset is applied to all GPS→AR transforms per frame

## Threading Model

| Thread | Responsibility |
|--------|---------------|
| Main (UI) | Compose UI, ViewModel state updates |
| GL Thread | Filament rendering, ARCore session.update(), depth texture upload |
| Coroutine | Network calls (route fetch, geocoding), location collection |

`NavigationState` is read from the GL thread via `FilamentArRenderer.currentState` (marked `@Volatile`). The ViewModel writes it from the main thread. This is safe because the state is an immutable data class — the GL thread always reads a consistent snapshot.
