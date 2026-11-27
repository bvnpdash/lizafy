plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zlitch.clackwell"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zlitch.clackwell"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    buildTypes {
        // Shrinking stays off: sound files are looked up by name.
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
