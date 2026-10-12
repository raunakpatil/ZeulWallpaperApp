import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services) apply false
}

// Firebase (push, CONTRACTS §8) needs app/google-services.json, which is git-ignored. Without it the
// build still works and the app just skips push; CI restores it from the GOOGLE_SERVICES_JSON secret.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

// Secrets live in android/local.properties (git-ignored). See local.properties.example.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String, default: String = ""): String =
    localProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: default

// Release signing: android/keystore.properties (git-ignored), written by scripts/make-release-key.ps1.
// Without it, release builds are unsigned and only debug builds install.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Google's public AdMob TEST app id, used until the real one is configured at launch.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"

android {
    namespace = "com.wallpaperapp.app"
    compileSdk = 37 // newest stable; required by current AndroidX. targetSdk stays at what Play requires.

    defaultConfig {
        applicationId = "com.zeul.wallpapers" // CONTRACTS §9; namespace + Kotlin packages stay com.wallpaperapp.app
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "ADMOB_APP_ID", "\"${secret("ADMOB_APP_ID", admobTestAppId)}\"")
        buildConfigField("String", "REVENUECAT_KEY", "\"${secret("REVENUECAT_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("GOOGLE_WEB_CLIENT_ID")}\"")
        // Dev switch: USE_FAKE_BACKEND=true in local.properties runs the app on built-in test data.
        buildConfigField("boolean", "USE_FAKE_BACKEND", secret("USE_FAKE_BACKEND", "false").toBoolean().toString())
        manifestPlaceholders["admobAppId"] = secret("ADMOB_APP_ID", admobTestAppId)
        // Real ad unit ids (release only; debug always uses Google test units — see ads/AdUnits.kt)
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${secret("ADMOB_REWARDED_ID")}\"")
        buildConfigField("String", "ADMOB_NATIVE_ID", "\"${secret("ADMOB_NATIVE_ID")}\"")
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"${secret("ADMOB_APP_OPEN_ID")}\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // android.util.Log etc. return defaults in JVM tests instead of throwing "not mocked",
        // so a logged failure can't hide the real cause.
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Exported schemas are committed so future migrations can be tested against them.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)

    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.revenuecat)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.paging.testing)

    // UI tests (androidTest): run with ./gradlew connectedDebugAndroidTest on a device/emulator.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
