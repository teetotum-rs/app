plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

// CHANGELOG.md, compiled in as the string CHANGELOG so the app shows the changelog of its own build.
val changelog = tasks.register("generateChangelog") {
    val source = rootProject.layout.projectDirectory.file("CHANGELOG.md")
    val output = layout.buildDirectory.dir("generated/changelog/commonMain/kotlin")
    inputs.file(source)
    outputs.dir(output)
    doLast {
        val escaped = source.asFile.readText()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("\n", "\\n")
        val file = output.get().file("io/github/teetotum_rs/app/Changelog.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package io.github.teetotum_rs.app\n\nval CHANGELOG = \"$escaped\"\n")
    }
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "io.github.teetotum_rs.app.shared"
        compileSdk = libs.versions.compile.sdk.get().toInt()
        minSdk = libs.versions.min.sdk.get().toInt()
        withHostTest {}
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(changelog)
        }
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.markdown.renderer.m3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}
