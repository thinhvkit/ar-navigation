# Filament Materials

## Build Process

Material source files (`.mat`) live in `app/src/main/materials/`. The Gradle task `compileFilamentMaterials` compiles them to `.filamat` binary assets in `app/src/main/assets/materials/`.

The `matc` compiler is automatically downloaded from the Filament GitHub releases (v1.69.4) on first build.

```bash
./gradlew compileFilamentMaterials   # Manual compile
./gradlew assembleDebug              # Auto-compiles before build
```

Only changed `.mat` files are recompiled (timestamp-based).

## Material Reference

### camera_background.mat

**Purpose**: Render the live camera feed as a fullscreen quad.

| Property | Value |
|----------|-------|
| Shading model | Unlit |
| Blending | Opaque |
| Requires | UV0 |

Converts YUV camera data to RGB. Bound to ARCore's OES external texture.

---

### turn_arrow.mat

**Purpose**: 3D navigation arrow chevrons with depth occlusion.

| Property | Value |
|----------|-------|
| Shading model | Unlit |
| Blending | Transparent |
| Double-sided | Yes |
| Variables | clipPosition |

**Parameters**:

| Name | Type | Description |
|------|------|-------------|
| `baseColor` | float4 | RGBA color and opacity |
| `time` | float | Animation time (seconds) |
| `rimStrength` | float | Glow intensity multiplier |
| `depthTexture` | sampler2d | ARCore depth texture (R32F, mm values) |
| `screenResolution` | float2 | Viewport width/height |
| `occlusionAlpha` | float | Opacity when behind real geometry |
| `depthTolerance` | float | Depth comparison tolerance (meters) |

**Vertex shader**: Passes clip-space position to fragment for screen-space depth lookup.

**Fragment shader**:
1. Emissive pulse: `0.7 + 0.3 * sin(time * 3.5)`
2. Brightness from `rimStrength`
3. Depth occlusion: compare virtual depth vs real depth, reduce alpha if behind

---

### nav_path_glow.mat

**Purpose**: Bright, flowing ground path effect.

| Property | Value |
|----------|-------|
| Shading model | Unlit |
| Blending | Additive (transparent) |
| Double-sided | Yes |
| Requires | UV0 |

**Parameters**:

| Name | Type | Description |
|------|------|-------------|
| `colorNear` | float4 | Color for near sections |
| `colorFar` | float4 | Color for far sections |
| `time` | float | Animation time |
| `speed` | float | Flow speed (m/s) |
| `pathLength` | float | Total path length (meters) |

---

### nav_path_occluded.mat

**Purpose**: Depth-tested dashed path visible through walls as a ghost.

| Property | Value |
|----------|-------|
| Shading model | Unlit |
| Blending | Transparent |
| Double-sided | Yes |
| Culling | None |
| Requires | UV0 |
| Variables | clipPosition |

**Parameters**:

| Name | Type | Description |
|------|------|-------------|
| `time` | float | Animation time |
| `speed` | float | Dash scroll speed |
| `dashFreq` | float | Dashes per meter |
| `edgeFade` | float | Edge softness (0–0.5) |
| `color` | float4 | RGBA path color |
| `depthTexture` | sampler2d | ARCore depth texture |
| `screenResolution` | float2 | Viewport dimensions |
| `occlusionAlpha` | float | Opacity when behind real geometry |
| `depthTolerance` | float | Depth tolerance (meters) |

---

### nav_chevrons.mat

**Purpose**: Legacy unlit emissive arrows. Being replaced by `turn_arrow.mat`.

## Adding a New Material

1. Create `app/src/main/materials/your_material.mat`
2. Run `./gradlew compileFilamentMaterials`
3. Load in Kotlin: `MaterialLoader.load(context, engine, "materials/your_material.filamat")`
4. Create instance: `material.createInstance()`
5. Set parameters: `instance.setParameter("name", value)`
6. Destroy in reverse order: instance → material

## Conventions

- All sampler parameters must be bound before the first frame renders (use a dummy texture if needed)
- Material parameters are set per-frame in update loops, not cached
- Filament objects are destroyed in reverse creation order
- Use `priority` to control render order (lower = drawn first)
