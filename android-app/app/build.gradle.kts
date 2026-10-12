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
        versionCode = 8
        versionName = "0.5.2-terranova-lotisseurs-pastilles"
    }

    // La clé signant les mises à jour doit rester identique entre builds.
    // Elle est chargée depuis les secrets GitHub et JAMAIS commitée au dépôt.
    val terraNovaKeystore = System.getenv("TERRANOVA_SIGNING_KEYSTORE")
    if (!terraNovaKeystore.isNullOrBlank()) {
        signingConfigs {
            create("terranova") {
                storeFile = file(terraNovaKeystore)
                storePassword = System.getenv("TERRANOVA_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("TERRANOVA_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("TERRANOVA_SIGNING_KEY_PASSWORD")
            }
        }
        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("terranova")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
