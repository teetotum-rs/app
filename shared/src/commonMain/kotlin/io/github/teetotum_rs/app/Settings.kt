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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The app's colours. */
enum class Theme(val title: String, val detail: String) {
    System("Follow system", "Light or dark, as the phone is set"),
    Dark("Dark", "GitHub Dark Default"),
}

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

/** The colour scheme [theme] stands for on this phone. */
@Composable
fun colorSchemeOf(theme: Theme): ColorScheme = when (theme) {
    Theme.System -> if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    Theme.Dark -> GitHubDark
}

/** Settings, grouped in cards. */
@Composable
fun SettingsPage(theme: Theme, onTheme: (Theme) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard("Appearance") {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
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

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
