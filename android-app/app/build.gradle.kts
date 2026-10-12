plugins {
    id("com.android.application")
}

android {
    namespace = "fr.terrainscope.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.terranova.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0-terranova-luxe"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
