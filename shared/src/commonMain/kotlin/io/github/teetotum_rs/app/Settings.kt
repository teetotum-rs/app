package io.github.teetotum_rs.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The app's colours. */
enum class Theme(val title: String, val detail: String) {
    System("Follow system", "Light or dark, as the phone is set"),
    GitHub("GitHub", "GitHub Light or Dark Default, as the phone is set"),
    Red("Red", "The knob's red theme, light or dark as the phone is set"),
}

/** GitHub Light Default, from Primer's github-light-default. */
private val GitHubLight = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0969DA),
    inversePrimary = Color(0xFF58A6FF),
    secondary = Color(0xFF656D76),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAEEF2),
    onSecondaryContainer = Color(0xFF1F2328),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFF6F8FA),
    onSurfaceVariant = Color(0xFF656D76),
    surfaceDim = Color(0xFFEAEEF2),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F8FA),
    surfaceContainer = Color(0xFFF6F8FA),
    surfaceContainerHigh = Color(0xFFEAEEF2),
    surfaceContainerHighest = Color(0xFFD0D7DE),
    inverseSurface = Color(0xFF1F2328),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFD0D7DE),
)

/** GitHub Dark Default, from Primer's github-dark-default. */
private val GitHubDark = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF1F6FEB),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF1F6FEB),
    secondary = Color(0xFF8B949E),
    onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF21262D),
    onSecondaryContainer = Color(0xFFE6EDF3),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF161B22),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceDim = Color(0xFF0D1117),
    surfaceBright = Color(0xFF262C36),
    surfaceContainerLowest = Color(0xFF010409),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerHigh = Color(0xFF21262D),
    surfaceContainerHighest = Color(0xFF262C36),
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = Color(0xFF0D1117),
    error = Color(0xFFF85149),
    onError = Color(0xFF0D1117),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF30363D),
)

/** The knob's red theme: its ring shades on black, cyan where the knob draws icons. */
private val RedDark = darkColorScheme(
    primary = Color(0xFFF0283A),
    onPrimary = Color(0xFF1A0406),
    primaryContainer = Color(0xFF5E0E14),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFC41E2E),
    secondary = Color(0xFF55F4FF),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF2F070A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF90EE90),
    onTertiary = Color(0xFF0A2A0A),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A0507),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF3D0B10),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF120305),
    surfaceContainer = Color(0xFF1A0507),
    surfaceContainerHigh = Color(0xFF2F070A),
    surfaceContainerHighest = Color(0xFF3D0B10),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFB547),
    onError = Color(0xFF1A0E00),
    outline = Color(0xFF5E0E14),
    outlineVariant = Color(0xFF5E0E14),
)

/** [RedDark] on white: the red and cyan darkened until text on and beside them stays readable. */
private val RedLight = lightColorScheme(
    primary = Color(0xFFC41E2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADC),
    onPrimaryContainer = Color(0xFF5E0E14),
    inversePrimary = Color(0xFFF0283A),
    secondary = Color(0xFF006874),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCE8E9),
    onSecondaryContainer = Color(0xFF2F070A),
    tertiary = Color(0xFF1B6E2A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1012),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1012),
    surfaceVariant = Color(0xFFFFF4F4),
    onSurfaceVariant = Color(0xFF666666),
    surfaceDim = Color(0xFFF5D6D8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF4F4),
    surfaceContainer = Color(0xFFFFF0F0),
    surfaceContainerHigh = Color(0xFFFCE8E9),
    surfaceContainerHighest = Color(0xFFF5D6D8),
    inverseSurface = Color(0xFF1F1012),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF9A5B00),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFE8B4B8),
    outlineVariant = Color(0xFFE8B4B8),
)

/** The colour scheme [theme] stands for on this phone. */
@Composable
fun colorSchemeOf(theme: Theme): ColorScheme = when (theme) {
    Theme.System -> if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    Theme.GitHub -> if (isSystemInDarkTheme()) GitHubDark else GitHubLight
    Theme.Red -> if (isSystemInDarkTheme()) RedDark else RedLight
}

/** Settings, grouped in cards. */
@Composable
fun SettingsPage(theme: Theme, onTheme: (Theme) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard("Appearance", AppearanceIcon) {
            Row(
                modifier = Modifier.padding(start = SETTING_INDENT),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(ThemeIcon, contentDescription = null)
                Text("Theme", style = MaterialTheme.typography.titleSmall)
            }
            Column(Modifier.padding(start = SETTING_INDENT * 2).selectableGroup()) {
                for (option in Theme.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(option == theme, role = Role.RadioButton) { onTheme(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == theme, onClick = null)
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How far a setting sits in from its card's title. */
private val SETTING_INDENT = 16.dp

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
