package io.github.teetotum_rs.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Draws a 24 dp icon from strokes; `Icon` tints it. */
private fun strokes(name: String, draw: PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, pathBuilder = draw)
        .build()

/** Three bars: opens the menu. */
val MenuIcon: ImageVector = strokes("Menu") {
    for (y in listOf(6f, 12f, 18f)) {
        moveTo(4f, y)
        lineTo(20f, y)
    }
}

/** A cross: closes the menu. */
val CloseIcon: ImageVector = strokes("Close") {
    moveTo(6f, 6f)
    lineTo(18f, 18f)
    moveTo(18f, 6f)
    lineTo(6f, 18f)
}
