package io.github.teetotum_rs.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One choice of a setting, with a line on what it does. */
interface Choice {
    val title: StringResource
    val detail: StringResource
}

/** The app's colours. */
enum class Theme(override val title: StringResource, override val detail: StringResource) : Choice {
    System(Res.string.settings_theme_system, Res.string.settings_theme_system_detail),
    GitHub(Res.string.settings_theme_github, Res.string.settings_theme_github_detail),
    Red(Res.string.settings_theme_red, Res.string.settings_theme_red_detail),
}

/** The page the app opens on. */
enum class Start(override val title: StringResource, override val detail: StringResource) : Choice {
    Home(Res.string.settings_start_home, Res.string.settings_start_home_detail),
    Last(Res.string.settings_start_last, Res.string.settings_start_last_detail),
}

/** When Plugins over Bluetooth reads the catalogue from GitHub; nothing leaves the phone unasked by default. */
enum class CatalogueLoad(override val title: StringResource, override val detail: StringResource) : Choice {
    Tap(Res.string.settings_catalogue_tap, Res.string.settings_catalogue_tap_detail),
    Open(Res.string.settings_catalogue_open, Res.string.settings_catalogue_open_detail),
}

/**
 * Everything the user sets, kept between runs; [lastPage] is where [Start.Last] opens, [folded] the menu's groups
 * folded away, [scroll] where each page was left.
 */
data class Preferences(
    val theme: Theme = Theme.System,
    val start: Start = Start.Home,
    val catalogue: CatalogueLoad = CatalogueLoad.Tap,
    val lastPage: Page = Page.Home,
    val folded: Set<Page> = emptySet(),
    val scroll: Map<Page, Position> = emptyMap(),
)

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

/** Settings, grouped in cards; [scroll] keeps where the page was left. */
@Composable
fun SettingsPage(preferences: Preferences, onPreferences: (Preferences) -> Unit, scroll: PageScroll? = null) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(pageScrollState(scroll)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard(stringResource(Res.string.settings_general), Res.drawable.tune) {
            ChoiceSetting(
                Res.drawable.home,
                stringResource(Res.string.settings_start),
                Start.entries,
                preferences.start,
            ) {
                onPreferences(preferences.copy(start = it))
            }
        }
        SettingsCard(stringResource(Res.string.settings_appearance), Res.drawable.visibility) {
            ChoiceSetting(
                Res.drawable.palette,
                stringResource(Res.string.settings_theme),
                Theme.entries,
                preferences.theme,
            ) {
                onPreferences(preferences.copy(theme = it))
            }
        }
        SettingsCard(stringResource(Res.string.page_plugins), Res.drawable.extension) {
            ChoiceSetting(
                Res.drawable.cloud_download,
                stringResource(Res.string.settings_catalogue),
                CatalogueLoad.entries,
                preferences.catalogue,
            ) { onPreferences(preferences.copy(catalogue = it)) }
        }
    }
}

/** A setting with [icon] and [title] above its [choices] as radio buttons. */
@Composable
private fun <T : Choice> ChoiceSetting(
    icon: DrawableResource,
    title: String,
    choices: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    // The card spaces its content by 8 dp; the title and its choices keep that.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(start = SETTING_INDENT),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.padding(start = SETTING_INDENT * 2).selectableGroup()) {
            for (option in choices) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(option == selected, role = Role.RadioButton) { onSelect(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(stringResource(option.title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(option.detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** How far a setting sits in from its card's title. */
private val SETTING_INDENT = 16.dp

@Composable
private fun SettingsCard(title: String, icon: DrawableResource, content: @Composable () -> Unit) {
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
