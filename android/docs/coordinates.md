# Coordinate Systems

## GPS → Local Meters → AR World

The app converts between three coordinate systems each frame.

### 1. GPS Coordinates (WGS84)

Standard latitude/longitude in degrees. Source: Android Fused Location Provider.

### 2. Local Meters (Flat-Earth)

`CoordinateConverter` projects GPS to a local Cartesian frame using flat-earth approximation:

```
x = (lng - originLng) * EARTH_RADIUS * cos(originLat)    → East (+x)
z = -(lat - originLat) * EARTH_RADIUS                     → South (+z), North (-z)
y = 0                                                      → Ground plane
```

- Origin is set on first GPS fix and locked for the session
- Accurate within ~1km of origin
- Matches ARCore convention: right-handed, Y-up

### 3. AR World (ARCore)

ARCore's coordinate frame:
- **Y-up**: perpendicular to gravity
- **-Z forward**: initial device facing direction
- **X right**: completes right-handed system
- Origin: device position at session start

### GPS → AR Transform

Each frame, a 4x4 matrix transforms GPS-local coordinates to AR world:

```
userLocal = CoordinateConverter.gpsToLocal(smoothedLat, smoothedLng)
effectiveHeading = arCoreYaw + calibrationAngle

dx = gpsX - userLocalX
dz = gpsZ - userLocalZ

arX =  dx * cos(heading) + dz * sin(heading) + camX
arY =  groundY  (camY - 1.5m + offset)
arZ = -dx * sin(heading) + dz * cos(heading) + camZ
```

The transform rotates GPS-local offsets by the effective heading, then translates to the AR camera position. This accounts for:

1. **Position offset**: GPS user position vs ARCore camera position
2. **Heading alignment**: GPS north vs ARCore's initial forward direction
3. **Ground plane**: AR content placed ~1.5m below camera (approximate ground)

### Heading Calibration

ARCore yaw and GPS heading reference different norths. The calibration angle bridges them:

```
calibrationAngle = gpsHeading - arCoreYaw
```

Updated with exponential smoothing:
- First 10 samples: factor 0.2 (fast convergence)
- After 10 samples: factor 0.05 (stability)

The effective heading used for all transforms:

```
effectiveHeading = arCoreYaw + calibrationAngle
```

## Path Coordinates

The navigation path ribbon mesh stores vertices in GPS-local coordinates (not AR world). The GPS→AR transform is applied via the entity's `TransformManager` each frame. This avoids rebuilding the mesh when the heading or position changes.

Chevron (arrow) positions are computed directly in AR world coordinates each frame, since they need per-entity distance culling and animation.
