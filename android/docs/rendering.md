# Rendering Pipeline

## Overview

The app uses Google's Filament rendering engine with ARCore for camera tracking. All rendering happens on the GL thread, driven by a Choreographer callback.

## Frame Loop

`FilamentArRenderer.doFrame()` executes the following steps each frame:

```
1. EGL context → current (for ARCore)
2. session.update() → Frame + Camera
3. Depth texture update (ARCore → Filament R32F)
4. Camera background upload (YUV → RGB)
5. Sync ARCore camera matrices → Filament camera
6. Update chevron positions and animations
7. Update path mesh and transform
8. Render: background view, then AR content view
```

## Managers

### CameraBackgroundManager

Renders the live camera feed as a fullscreen quad behind all AR content.

- Uses an OES external texture bound to ARCore
- `camera_background.mat` converts YUV to RGB
- Runs in a separate Filament `View` rendered first (lower priority)
- Updates display UV coordinates based on device rotation

### ChevronEntityManager

Manages a pool of 40 3D arrow entities.

**Geometry**: Extruded arrow head shape (tip + wings + notch). Position-only vertex buffer (FLOAT3, stride 12 bytes). The material is unlit so normals are not needed.

**Entity Pool**: All 40 entities are created at init. Inactive ones are removed from the scene (`scene.removeEntity()`), active ones are added back. This avoids per-frame allocation.

**Animation States** (distance-based):

| Distance | State | Visual |
|----------|-------|--------|
| > 20m | Far fade | Green, alpha fading to 0 |
| 5–20m | Normal | Green, wave animation |
| 2–5m | Attention | Yellow, bounce + pulse |
| < 2m | Pass-through | Fading out, lerp to confirm green |

**Material Parameters** (set per-frame per-instance):

| Parameter | Type | Description |
|-----------|------|-------------|
| `baseColor` | float4 | RGBA color (changes with distance state) |
| `time` | float | Animation time in seconds |
| `rimStrength` | float | Glow intensity |
| `depthTexture` | sampler2d | ARCore depth for occlusion |
| `screenResolution` | float2 | Viewport dimensions |
| `occlusionAlpha` | float | Opacity when behind real geometry (0.15) |
| `depthTolerance` | float | Depth comparison tolerance in meters (0.15) |

### NavPathManager

Renders the route as a ground-level ribbon mesh with two layers:

1. **Glow layer** (priority 4) — additive blending, flowing energy animation
2. **Occluded layer** (priority 5) — depth-tested dashed lines, visible through walls as ghost

**Mesh**: Triangle strip with 2 vertices per route point (left + right edge, 0.5m half-width). Vertices are in GPS-local coordinates. The GPS→AR transform is applied via the entity's `TransformManager` each frame.

**Vertex format**: Position (FLOAT3) + UV (FLOAT2), stride 20 bytes. UV.x = 0/1 for left/right edge, UV.y = cumulative distance in meters.

### DepthTextureManager

Bridges ARCore's depth API to Filament materials for real-world occlusion.

- Calls `frame.acquireDepthImage16Bits()` each frame
- Converts unsigned 16-bit depth (millimeters) to R32F float texture
- Caches texture, recreates only on dimension change (typically 160x90)
- Handles `NotYetAvailableException` by returning last valid texture
- Provides a 1x1 dummy texture (max depth) for initialization — ensures materials have a valid sampler bound before real depth data arrives

## Materials

All materials are authored as `.mat` files and compiled to `.filamat` by the Filament `matc` compiler at build time.

### turn_arrow.mat

Unlit transparent material for 3D arrow chevrons.

- Emissive pulse: `0.7 + 0.3 * sin(time * 3.5)`
- Brightness scaled by `rimStrength`
- Depth occlusion via clip-space depth comparison
- `occlusionAlpha` controls ghost opacity behind walls

### nav_path_glow.mat

Unlit additive-blended material for the bright path layer.

- Near/far color gradient
- Flowing animation along UV.y (distance)
- No depth occlusion (always fully visible)

### nav_path_occluded.mat

Unlit transparent material for the depth-tested path layer.

- Scrolling dashed line pattern
- Edge fade for soft ribbon edges
- Depth occlusion: samples ARCore depth, compares with virtual depth
- Fragments behind real surfaces render at `occlusionAlpha` (ghost effect)
- Near-user fade (first 0.5m)

### camera_background.mat

Unlit opaque material for the camera feed.

- YUV→RGB color space conversion
- Bound to ARCore's OES external texture

## Render Order

Filament render priority (lower = drawn first):

| Priority | Content |
|----------|---------|
| Background view | Camera feed (separate Filament View) |
| 4 | Path glow layer |
| 5 | Path occluded layer |
| 7 | 3D arrow chevrons |

## Depth Occlusion Algorithm

Both `turn_arrow.mat` and `nav_path_occluded.mat` use the same depth comparison:

```glsl
// Vertex shader: pass clip-space position
material.clipPosition = getClipFromWorldMatrix() * getPosition();

// Fragment shader: compare depths
float4 clip = variable_clipPosition;
float2 screenUV = (clip.xy / clip.w) * 0.5 + 0.5;

float realDepth = texture(depthTexture, float2(screenUV.x, 1.0 - screenUV.y)).r / 1000.0;
float virtualDepth = linearizeDepth(clip.z / clip.w, 0.1, 100.0);

float isVisible = step(virtualDepth, realDepth - depthTolerance);
float finalAlpha = mix(alpha * occlusionAlpha, alpha, isVisible);
```

- `realDepth`: ARCore depth in meters (stored as mm in texture, divided by 1000)
- `virtualDepth`: linearized from NDC Z
- `isVisible`: 1.0 if virtual fragment is in front of real surface, 0.0 if behind
- Behind real surfaces: alpha reduced to `occlusionAlpha` (default 0.15) for a ghost effect
