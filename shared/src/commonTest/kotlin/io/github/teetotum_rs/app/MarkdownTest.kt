package io.github.teetotum_rs.app

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {
    @Test
    fun headingsBulletsAndParagraphs() {
        val text = """
            # Changelog

            All notable changes are
            listed here.

            ## [0.1.0] - 2026-09-16

            ### Added

            - Join the Knob from the QR code of **Card over Wi-Fi**.
            - Browse folders; download files into `Download/TeeToTum`,
              the phone's folder.

            [0.1.0]: https://github.com/teetotum-rs/app/releases/tag/v0.1.0
        """.trimIndent()
        assertEquals(
            listOf(
                Block.Heading(1, "Changelog"),
                Block.Paragraph("All notable changes are listed here."),
                Block.Heading(2, "0.1.0 - 2026-09-16"),
                Block.Heading(3, "Added"),
                Block.Bullet("Join the Knob from the QR code of Card over Wi-Fi."),
                Block.Bullet("Browse folders; download files into Download/TeeToTum, the phone's folder."),
            ),
            blocksOf(text),
        )
    }

    @Test
    fun linksKeepTheirText() {
        assertEquals(
            listOf(Block.Paragraph("The format follows Keep a Changelog.")),
            blocksOf("The format follows [Keep a Changelog](https://keepachangelog.com/)."),
        )
    }

    @Test
    fun paragraphAfterBulletStartsAfterBlankLine() {
        assertEquals(
            listOf(Block.Bullet("One."), Block.Paragraph("Two.")),
            blocksOf("- One.\n\nTwo."),
        )
    }
}
