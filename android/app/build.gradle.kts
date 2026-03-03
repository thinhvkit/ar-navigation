plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── Filament matc compilation ──────────────────────────────────────────────────
val matcVersion = "1.69.4"
val matcDir = layout.buildDirectory.dir("matc").get().asFile
val matcBinary = File(matcDir, "matc")
val matSrcDir = file("src/main/materials")
val matOutDir = file("src/main/assets/materials")

tasks.register("downloadMatc") {
    val markerFile = File(matcDir, ".version-$matcVersion")
    outputs.file(markerFile)
    onlyIf { !markerFile.exists() }
    doLast {
        matcDir.mkdirs()
        val os = System.getProperty("os.name").lowercase()
        // Filament release tarball names: mac, linux, windows
        val platform = when {
            os.contains("mac") -> "mac"
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> error("Unsupported platform: $os")
        }
        val tarName = "filament-v$matcVersion-$platform.tgz"
        val url = "https://github.com/google/filament/releases/download/v$matcVersion/$tarName"
        val tarFile = File(matcDir, tarName)
        logger.lifecycle("Downloading Filament tools from $url")
        uri(url).toURL().openStream().use { input: java.io.InputStream ->
            tarFile.outputStream().use { output -> input.copyTo(output) }
        }
        // Extract matc binary from tarball
        logger.lifecycle("Extracting matc from $tarName")
        exec {
            workingDir(matcDir)
            commandLine("tar", "xzf", tarFile.absolutePath, "--include=*/bin/matc", "--strip-components=2")
        }
        tarFile.delete()
        matcBinary.setExecutable(true)
        markerFile.writeText(matcVersion)
    }
}

tasks.register("compileFilamentMaterials") {
    dependsOn("downloadMatc")
    inputs.dir(matSrcDir)
    outputs.dir(matOutDir)
    doLast {
        matOutDir.mkdirs()
        matSrcDir.listFiles { f -> f.extension == "mat" }?.forEach { mat ->
            val outFile = File(matOutDir, mat.nameWithoutExtension + ".filamat")
            if (!outFile.exists() || mat.lastModified() > outFile.lastModified()) {
                logger.lifecycle("Compiling ${mat.name} → ${outFile.name}")
                exec {
                    commandLine(
                        matcBinary.absolutePath,
                        "-p", "mobile",
                        "-a", "opengl",
                        "-o", outFile.absolutePath,
                        mat.absolutePath
                    )
                }
            }
        }
    }
}

tasks.named("preBuild") { dependsOn("compileFilamentMaterials") }
// ───────────────────────────────────────────────────────────────────────────────

android {
    namespace = "com.ideals.arnav"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ideals.arnav"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ARCore
    implementation("com.google.ar:core:1.44.0")

    // Filament rendering engine
    implementation("com.google.android.filament:filament-android:1.69.4")
    implementation("com.google.android.filament:filament-utils-android:1.69.4")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Mapbox Maps SDK
    implementation("com.mapbox.maps:android:11.18.2")
    implementation("com.mapbox.extension:maps-compose:11.18.2")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
