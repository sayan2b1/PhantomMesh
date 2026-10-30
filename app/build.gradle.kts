plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.phantom.mesh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phantom.mesh"
        minSdk = 31
        targetSdk = 35
        versionCode = 7
        versionName = "3.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migration validation
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "PhantomMesh.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── Compose BOM ─────────────────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // ── Activity & Lifecycle ────────────────────────────────────
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.service)

    // ── Navigation ──────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Room (Local SQLite) ─────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Firebase BOM ────────────────────────────────────────────
    val firebaseBom = platform(libs.firebase.bom)
    implementation(firebaseBom)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)

    // ── Nearby Connections (P2P Mesh) ───────────────────────────
    implementation(libs.play.services.nearby)

    // ── WebRTC (Real-time Peer-to-Peer Voice & Video Engine) ─────
    implementation(libs.stream.webrtc)
    implementation(libs.stream.webrtc.compose)

    // ── Hilt DI ─────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // ── DataStore ───────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Coroutines ──────────────────────────────────────────────
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // ── Image Loading ───────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Serialization ───────────────────────────────────────────
    implementation(libs.gson)

    // ── WorkManager ─────────────────────────────────────────────
    implementation(libs.work.runtime)

    // ── Runtime Permissions ─────────────────────────────────────
    implementation(libs.accompanist.permissions)
}
