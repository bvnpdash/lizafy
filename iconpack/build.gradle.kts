plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.lizafy.iconpack"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions += "style"
    productFlavors {
        create("light") {
            dimension = "style"
            applicationId = "com.lizafy.iconpack.light"
            resValue("string", "app_name", "Lizafy Icons Light")
        }
        create("dark") {
            dimension = "style"
            applicationId = "com.lizafy.iconpack.dark"
            resValue("string", "app_name", "Lizafy Icons Dark")
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/lizafy-debug.keystore")
            storePassword = "android"
            keyAlias = "lizafy"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
