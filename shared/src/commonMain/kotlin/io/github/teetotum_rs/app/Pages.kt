package io.github.teetotum_rs.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantTextStyles
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryActionKind
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/** What the app shows: the card, or one of the pages from the menu. */
enum class Page(val title: String, val icon: ImageVector) {
    Card("Card over Wi-Fi", WifiIcon),
    Settings("Settings", SettingsIcon),
    Help("Help", HelpIcon),
    Imprint("Imprint", ImprintIcon),
    Privacy("Privacy", PrivacyIcon),
    Changelog("Changelog", ChangelogIcon),
    Libraries("Libraries", LibrariesIcon),
}

/** Menu and close are thin glyphs; at this size they weigh as much as the 24 dp settings gear. */
private val BAR_ICON = 28.dp

/**
 * The row above every page; the menu button sits where the menu's close button appears, the
 * settings button on the right.
 */
@Composable
fun TopBar(title: String, onMenu: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(MenuIcon, contentDescription = "Open menu", modifier = Modifier.size(BAR_ICON)) }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) { Icon(SettingsIcon, contentDescription = "Open settings") }
    }
}

/** The menu that slides in from the left. */
@Composable
fun Menu(drawer: DrawerState, page: Page, onClose: () -> Unit, onPage: (Page) -> Unit, onExit: () -> Unit) {
    // Narrower than Material's 360 dp, so the page stays in sight on a phone of that width.
    ModalDrawerSheet(drawerState = drawer, modifier = Modifier.width(300.dp)) {
        Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(CloseIcon, contentDescription = "Close menu", modifier = Modifier.size(BAR_ICON)) }
            Text("TeeToTum", style = MaterialTheme.typography.titleLarge)
        }
        Column(modifier = Modifier.padding(12.dp)) {
            for (entry in Page.entries) {
                NavigationDrawerItem(
                    label = { Text(entry.title) },
                    icon = { Icon(entry.icon, contentDescription = null) },
                    selected = entry == page,
                    onClick = { onPage(entry) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("Exit") },
                icon = { Icon(ExitIcon, contentDescription = null) },
                selected = false,
                onClick = onExit,
            )
        }
    }
}

/**
 * A page from the menu other than [Page.Card]; [libraries] reads the list for [Page.Libraries],
 * [theme] and [onTheme] are the setting shown on [Page.Settings].
 */
@OptIn(ExperimentalMaterial3Api::class) // LibrariesContainer's overload with its own dialog state
@Composable
fun PageContent(page: Page, libraries: suspend () -> String, theme: Theme, onTheme: (Theme) -> Unit) {
    if (page == Page.Settings) {
        SettingsPage(theme, onTheme)
    } else if (page == Page.Libraries) {
        val listed by produceLibraries { libraries() }
        var dialog by remember { mutableStateOf<Library?>(null) }
        var sheet by remember { mutableStateOf<Library?>(null) }
        LibrariesContainer(
            listed,
            dialogLibrary = dialog,
            sheetLibrary = sheet,
            onDialogLibraryChange = { dialog = it },
            onSheetLibraryChange = { sheet = it },
            modifier = Modifier.fillMaxSize(),
            // The library's small defaults, raised to the sizes the other pages use.
            variantTextStyles = LibraryDefaults.m3VariantTextStyles(
                nameTextStyle = MaterialTheme.typography.titleMedium,
                authorTextStyle = MaterialTheme.typography.bodyMedium,
                versionTextStyle = MaterialTheme.typography.labelMedium,
                licenseTextStyle = MaterialTheme.typography.labelMedium,
                descriptionTextStyle = MaterialTheme.typography.bodyMedium,
                sheetBodyTextStyle = MaterialTheme.typography.bodyLarge,
            ),
            // The licence text is built in; the web page would need a network the Knob does not offer.
            onActionClick = { library, kind ->
                if (kind == LibraryActionKind.License) dialog = library
                kind == LibraryActionKind.License
            },
        )
    } else {
        Markdown(
            textOf(page),
            // The library's headings default to display sizes, far larger than the page title.
            typography = markdownTypography(
                h1 = MaterialTheme.typography.titleLarge,
                h2 = MaterialTheme.typography.titleMedium,
                h3 = MaterialTheme.typography.titleSmall,
                h4 = MaterialTheme.typography.titleSmall,
                h5 = MaterialTheme.typography.titleSmall,
                h6 = MaterialTheme.typography.titleSmall,
            ),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
    }
}

private fun textOf(page: Page): String = when (page) {
    Page.Help -> HELP
    Page.Imprint -> IMPRINT
    Page.Privacy -> PRIVACY
    // The changelog's own title and preamble repeat what the page title says.
    Page.Changelog -> CHANGELOG.substring(CHANGELOG.indexOf("\n## ").coerceAtLeast(0))
    Page.Card, Page.Settings, Page.Libraries -> ""
}

private const val HELP = """
## Connect

On the Knob, open **Card over Wi-Fi**. In the app, choose **Card over Wi-Fi** in the menu, tap **Scan code**
and point the camera at the code on the Knob's screen; the phone joins the network the Knob offers and shows the card.

The Knob needs firmware 0.3.3 or later.

## On the card

- Tap a folder to open it; **Up** or the back gesture goes to the folder above.
- Tap a file to download it into `Download/TeeToTum` on the phone.
- **Upload files** sends files from the phone into the open folder, asking before a file of the
  same name is replaced.
- **New folder** makes a folder in the open one.
- Hold a file or an empty folder to delete it.

## From other apps

Share files to TeeToTum from any app. Once the card shows, they are offered for the folder you
open.
"""

private const val IMPRINT = """
The imprint will follow.
"""

private const val PRIVACY = """
TeeToTum has no account, no ads and no analytics. It sends nothing to its makers or to anyone
else.

## Camera

The camera only reads the code on the Knob's screen. No picture is stored or sent.

## Wi-Fi

The app joins the network the Knob offers and talks only to the Knob.

## Files

Files you download are saved in `Download/TeeToTum` on the phone. Files you upload or share go
only to the card in the Knob. Apart from downloads and your settings, the app stores nothing on the
phone.
"""
