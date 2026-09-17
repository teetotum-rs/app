package io.github.teetotum_rs.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The corners of every button, and of the menu entries beside them. */
val ButtonShape = RoundedCornerShape(8.dp)

/**
 * The one button of the app, drawn like the action chips on the libraries page: filled for the
 * main action, outlined for the others. Material's own buttons are not used anywhere else.
 */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val faded = colors.onSurface.copy(alpha = 0.38f)
    val container = when {
        !filled -> Color.Transparent
        enabled -> colors.primary
        else -> colors.onSurface.copy(alpha = 0.12f)
    }
    val content = when {
        !enabled -> faded
        filled -> colors.onPrimary
        else -> colors.onSurface
    }
    // Surface keeps the 48 dp touch target around the smaller chip.
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        color = container,
        contentColor = content,
        border = if (filled) null else BorderStroke(1.dp, if (enabled) colors.outline else faded),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
