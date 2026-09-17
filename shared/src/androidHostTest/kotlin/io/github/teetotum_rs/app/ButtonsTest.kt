package io.github.teetotum_rs.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ButtonsTest {
    // Every button goes through ActionButton; icon and radio buttons are not buttons in that sense.
    private val material = Regex("""\b(Button|OutlinedButton|TextButton|ElevatedButton|FilledTonalButton)\s*\(""")

    @Test
    fun onlyActionButtonDrawsButtons() {
        val sources = listOf(File("src"), File("../androidApp/src"))
            .flatMap { root -> root.walk().filter { it.extension == "kt" && it.name != "ButtonsTest.kt" } }
        val found = sources.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> material.containsMatchIn(line) }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList(), found)
    }
}
