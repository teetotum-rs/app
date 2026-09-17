package io.github.teetotum_rs.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class IconsTest {
    // Every icon goes through AppIcon, which tints it in the primary colour.
    private val material = Regex("""(?<![\w.])Icon\s*\(""")

    @Test
    fun onlyAppIconDrawsIcons() {
        val sources = listOf(File("src"), File("../androidApp/src"))
            .flatMap { root -> root.walk().filter { it.extension == "kt" && it.name != "IconsTest.kt" } }
        val found = sources.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> material.containsMatchIn(line) }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(listOf("src/commonMain/kotlin/io/github/teetotum_rs/app/Icons.kt"), found.map { it.substringBefore(':') })
    }
}
