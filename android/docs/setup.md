# Setup Guide

## Prerequisites

- **Android Studio** Ladybug or newer
- **JDK 17**
- **Android SDK**: compileSdk 36, build-tools
- **Android device** with ARCore support (for testing)
- **Mapbox account** with a downloads token

## Mapbox Configuration

### 1. Downloads Token (SDK access)

The Mapbox Maps SDK is distributed via a private Maven repository that requires authentication.

Add your Mapbox secret token to `~/.gradle/gradle.properties`:

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1Ijoi...your_secret_token
```

This token is used at build time to download the Mapbox SDK artifacts.

### 2. Public Access Token (runtime)

The app needs a Mapbox public access token for routing and geocoding API calls. Set it in `ArNavApp.kt` or via a build config field.

## Building

```bash
# Debug build (includes material compilation)
./gradlew assembleDebug

# Release build with ProGuard
./gradlew assembleRelease

# Just compile materials (useful during shader development)
./gradlew compileFilamentMaterials

# Clean everything
./gradlew clean
```

The first build downloads the Filament `matc` shader compiler (~150MB tarball) automatically. Subsequent builds skip this step.

## Installing on Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Required Permissions

The app requests at runtime:
- `ACCESS_FINE_LOCATION` — GPS for navigation
- `CAMERA` — ARCore camera feed

## Testing Without GPS

Use demo mode to test the AR rendering without a real GPS signal or route:

```kotlin
viewModel.injectDemoRoute()
```

This loads a mock route and simulates navigation state.

## ProGuard

Release builds use ProGuard. Keep rules are configured in `proguard-rules.pro` for:
- ARCore classes
- Filament classes
- Route model classes (Moshi JSON serialization)

## Troubleshooting

### Material compilation fails

- Ensure you have internet access (first build downloads `matc`)
- Check that `app/src/main/materials/*.mat` files have valid syntax
- Try `./gradlew clean compileFilamentMaterials` for a fresh compile

### ARCore not available

- Ensure Google Play Services for AR is installed on the device
- Check that the device supports ARCore: [ARCore supported devices](https://developers.google.com/ar/devices)

### App crashes on launch with Filament error

- All `sampler2d` material parameters must have a texture bound before rendering
- Check logcat for `Precondition` errors from Filament — these indicate parameter type mismatches

### Mapbox SDK download fails

- Verify `MAPBOX_DOWNLOADS_TOKEN` is set in `~/.gradle/gradle.properties`
- Ensure the token has the `DOWNLOADS:READ` scope
