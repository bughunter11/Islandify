import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.islandify.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.islandify.app"
        minSdk = 26
        targetSdk = 34   // TARGETSDK34: edge-to-edge default warning hataane ke liye
        versionCode = 7   // VERSION16 (1.5 was versionCode 6)
        versionName = "1.6"
    }

    buildFeatures { compose = true }

    val props = Properties()
    val propsFile = rootProject.file("keystore.properties")
    val hasKeystore = propsFile.exists()
    if (hasKeystore) props.load(propsFile.inputStream())

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    // Install THIS (release) build for real speed. Debug builds are 3-5x slower in Compose.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.05.01")   // last line that still builds with AGP 8.7 + compileSdk 35
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.materialkolor:material-kolor:2.0.0")   // real Material 3 (HCT) palette from a seed color
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.material:material:1.12.0")   // only for the Material 3 XML theme (Theme.Material3.DayNight)
    implementation("androidx.core:core-splashscreen:1.0.1")   // clean launch screen, no white/black flash
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")   // pre-compiles Compose on install
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media:media:1.7.0")   // MediaControllerCompat: shuffle / repeat control
    testImplementation("junit:junit:4.13.2")   // PATCHKIT15B: unit tests
}

// PATCHKIT15B-SIGN: release APK kabhi debug key se sign na ho.
// Warna in-app update purane users par "App not installed" deta hai (signature alag).
// Override sirf test ke liye: ./gradlew assembleRelease -PallowDebugRelease
gradle.taskGraph.whenReady {
    val ks = rootProject.file("keystore.properties")
    val releaseBuild = allTasks.any { t ->
        t.project == project &&
            (t.name == "assembleRelease" || t.name == "bundleRelease" || t.name == "packageRelease")
    }
    if (releaseBuild && !ks.exists() && !project.hasProperty("allowDebugRelease")) {
        throw GradleException(
            "keystore.properties not found: refusing to sign a RELEASE build with the debug key. " +
                "Add keystore.properties, or pass -PallowDebugRelease to override."
        )
    }
}
