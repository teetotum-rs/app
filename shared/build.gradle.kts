plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.roborazzi)
}

// The Markdown pages, compiled in as strings named after their files: src/commonMain/markdown/help.md
// becomes HELP, and CHANGELOG.md becomes CHANGELOG so the app shows the changelog of its own build.
val markdown = tasks.register("generateMarkdown") {
    val changelog = rootProject.layout.projectDirectory.file("CHANGELOG.md")
    val pages = layout.projectDirectory.dir("src/commonMain/markdown")
    val output = layout.buildDirectory.dir("generated/markdown/commonMain/kotlin")
    inputs.file(changelog)
    inputs.dir(pages)
    outputs.dir(output)
    doLast {
        val sources = listOf(changelog.asFile) + pages.asFile.listFiles { f -> f.extension == "md" }!!.sortedBy { it.name }
        val constants = sources.joinToString("") { source ->
            val escaped = source.readText()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("\n", "\\n")
            "\nval ${source.nameWithoutExtension.uppercase()} = \"$escaped\"\n"
        }
        val file = output.get().file("io/github/teetotum_rs/app/Markdown.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package io.github.teetotum_rs.app\n$constants")
    }
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "io.github.teetotum_rs.app.shared"
        compileSdk = libs.versions.compile.sdk.get().toInt()
        minSdk = libs.versions.min.sdk.get().toInt()
        // Robolectric runs the Compose UI tests on the host and needs the merged resources and manifest.
        withHostTest { isIncludeAndroidResources = true }
        // Compose resources reach the APK as Android assets.
        androidResources { enable = true }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(markdown)
        }
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.markdown.renderer.m3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.ui.test)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.teetotum_rs.app"
    // The app module shows these strings too.
    publicResClass = true
}
