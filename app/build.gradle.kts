plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.rgprince.genkonotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rgprince.genkonotes"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MMKV 2.x ships 64-bit native libs only — filter 32-bit ABIs so those
        // devices are excluded at install time instead of crashing on load.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("genko-release.jks")
            storePassword = System.getenv("GENKO_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("GENKO_KEY_ALIAS")
            keyPassword = System.getenv("GENKO_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            // Signed only when the CI key is present; local R8 test builds
            // stay unsigned but still fully shrunk.
            if (System.getenv("GENKO_KEYSTORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    dependenciesInfo {
        // F-Droid flags the 'Dependency metadata' signing block,
        // so leave it out of the APK entirely.
        includeInApk = false
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Android framework calls in shared sources (e.g. Log) return defaults
        // under plain JVM unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugarJdkLibs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tencent.mmkv)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.testManifest)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
}

kotlin {
    // Bytecode level only — no strict toolchain demand, so builders
    // with a newer JDK (and no JDK 17 install, e.g. F-Droid) still work.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
