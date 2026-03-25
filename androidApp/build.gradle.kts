plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
    id("com.google.devtools.ksp")
}

import java.util.Properties
import java.io.ByteArrayOutputStream

fun getGitHash(): String {
    val ciHash = System.getenv("CI_COMMIT_SHORT_SHA")
    if (!ciHash.isNullOrEmpty()) return ciHash
    
    return try {
        val stdout = ByteArrayOutputStream()
        project.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = stdout
        }
        stdout.toString().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

android {
    namespace = "me.shirobyte42.glosso"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    val gitHash = getGitHash()
    val baseVersionName = "1.0.4"

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { 
            localProperties.load(it)
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = System.getenv("SIGNING_ALIAS") ?: localProperties.getProperty("signing.alias")
            keyPassword = System.getenv("SIGNING_KEY_PASS") ?: localProperties.getProperty("signing.keyPass")
            val storeFilePath = System.getenv("SIGNING_STORE_FILE") ?: localProperties.getProperty("signing.storeFile")
            storeFile = if (storeFilePath != null) rootProject.file(storeFilePath) else null
            storePassword = System.getenv("SIGNING_STORE_PASS") ?: localProperties.getProperty("signing.storePass")
        }
    }
defaultConfig {
    applicationId = "me.shirobyte42.glosso"
    minSdk = 26
    targetSdk = 35
    versionCode = 1004
    versionName = "$baseVersionName-$gitHash"
}

applicationVariants.all {
    val variant = this
    variant.outputs.all {
        val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
        output.outputFileName = "glosso-studio-v${variant.versionName}.apk"
    }
}

dependenciesInfo {
    includeInApk = false
    includeInBundle = false
}

buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        // Ensure full debug symbols for native libs
        ndk {
            debugSymbolLevel = "full"
        }
    }
}

// Disable all non-deterministic resource generation
androidResources {
    @Suppress("UnstableApiUsage")
    generateLocaleConfig = false
}

packaging {
    jniLibs {
        @Suppress("UnstableApiUsage")
        keepDebugSymbols.add("**/*.so")
    }
    resources {
        // Strip non-deterministic files from APK
        excludes.add("META-INF/*.version")
        excludes.add("*.prof")
        excludes.add("META-INF/version-control-info.textproto")
    }
}

// Completely disable non-deterministic background tasks
tasks.configureEach {
    if (name.contains("ArtProfile", ignoreCase = true) || 
        name.contains("BaselineProfile", ignoreCase = true) ||
        name.contains("vcsInfo", ignoreCase = true)) {
        enabled = false
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Koin
    implementation("io.insert-koin:koin-android:3.5.3")
    implementation("io.insert-koin:koin-androidx-compose:3.5.3")

    // Lottie
    implementation("com.airbnb.android:lottie-compose:6.1.0")

    // ONNX Runtime
    implementation("com.microsoft.onnxruntime:onnxruntime-android:latest.release")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Ktor
    val ktorVersion = "2.3.7"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
}
