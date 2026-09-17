package io.github.teetotum_rs.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagesTest {
    @Test
    fun splitsAtLevelTwoHeadings() {
        val (intro, sections) = sectionsOf("Intro.\n\n## One\n\nFirst.\n\n### Sub\n\nStill first.\n\n## [0.2.0] - 2026-09-17\n\nSecond.\n")
        assertEquals("Intro.", intro)
        assertEquals(
            listOf("One" to "First.\n\n### Sub\n\nStill first.", "0.2.0 - 2026-09-17" to "Second."),
            sections,
        )
    }

    @Test
    fun everyCardOnTheWrittenPagesHasItsOwnIcon() {
        val missing = listOf(Page.Help, Page.Imprint, Page.Privacy)
            .flatMap { page -> sectionsOf(textOf(page)).second.map { "${page.title}: ${it.first}" to it.first } }
            .filter { (_, title) -> title !in SECTION_ICONS }
            .map { it.first }
        assertTrue(missing.isEmpty(), "headings without an icon in SECTION_ICONS: $missing")
    }

    @Test
    fun theChangelogHasACardPerVersionAndNoIntro() {
        val (intro, sections) = sectionsOf(textOf(Page.Changelog))
        assertEquals("", intro)
        assertTrue(sections.isNotEmpty() && sections.all { it.first.first().isDigit() || it.first == "Unreleased" })
    }
}
