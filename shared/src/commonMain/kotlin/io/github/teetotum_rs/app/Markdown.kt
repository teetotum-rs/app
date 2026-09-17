package io.github.teetotum_rs.app

/** A piece of the little Markdown the app shows: its own pages and the changelog. */
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Paragraph(val text: String) : Block
}

private val LINK_DEFINITION = Regex("""^\[[^\]]+\]:\s+\S+$""")
private val LINK = Regex("""\[([^\]]+)\](\([^)]*\))?""")

/**
 * Splits [markdown] into headings, `- ` bullets and paragraphs. Lines that follow without a blank
 * line continue the block; link definitions are dropped and inline marks reduced to their text.
 */
fun blocksOf(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    var text: String? = null
    var bullet = false

    fun flush() {
        val done = text ?: return
        blocks += if (bullet) Block.Bullet(plain(done)) else Block.Paragraph(plain(done))
        text = null
    }

    for (line in markdown.lines().map { it.trim() }) {
        when {
            line.isEmpty() || LINK_DEFINITION.matches(line) -> flush()
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                blocks += Block.Heading(level, plain(line.drop(level).trim()))
            }
            line.startsWith("- ") -> {
                flush()
                bullet = true
                text = line.drop(2)
            }
            text != null -> text += " $line"
            else -> {
                bullet = false
                text = line
            }
        }
    }
    flush()
    return blocks
}

/** [text] without emphasis, code marks and link targets. */
private fun plain(text: String) =
    LINK.replace(text) { it.groupValues[1] }.replace("**", "").replace("`", "")
