plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // Lists the app's libraries and their licences in R.raw.aboutlibraries.
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "io.github.teetotum_rs.app"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.teetotum_rs.app"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = 2
        versionName = "0.2.0"
    }

    // Release signing comes from the environment; without it the release APK stays unsigned.
    val keystore = System.getenv("TEETOTUM_APP_KEYSTORE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storeType = "PKCS12"
                storePassword = System.getenv("TEETOTUM_APP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TEETOTUM_APP_KEY_ALIAS")
                keyPassword = System.getenv("TEETOTUM_APP_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to a signed release instead of failing on the other key.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    lint {
        warningsAsErrors = true
    }

    dependenciesInfo {
        // The signing block Google Play reads; F-Droid rejects APKs that carry it.
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.zxing.core)
}
