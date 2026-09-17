package io.github.teetotum_rs.app

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Every icon in the app, in the primary colour, like the dot of a selected radio button.
 *
 * The icons are Material Symbols Outlined (Apache 2.0) in `composeResources/drawable`, named after their symbol.
 */
@Composable
fun AppIcon(icon: DrawableResource, contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(vectorResource(icon), contentDescription, modifier, tint = MaterialTheme.colorScheme.primary)
}
