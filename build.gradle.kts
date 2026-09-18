plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt)
}

// One detekt run over both modules, without type resolution; `./gradlew detekt` is what CI runs.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt.yml")
    source.setFrom("shared/src", "androidApp/src")
}

dependencies {
    detektPlugins(libs.detekt.ktlint.wrapper)
    detektPlugins(libs.compose.rules.detekt)
}
