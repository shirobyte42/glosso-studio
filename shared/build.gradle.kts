plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("kotlinx-serialization")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Serialization for Models
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                
                // Koin for DI
                implementation("io.insert-koin:koin-core:3.5.3")
                
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val androidMain by getting {
            dependencies {
            }
        }
    }
}

android {
    namespace = "me.shirobyte42.glosso.shared"
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
