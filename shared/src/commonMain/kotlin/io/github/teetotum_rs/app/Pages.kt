package io.github.teetotum_rs.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

/** What the app shows: home, the card, or one of the pages from the menu. */
enum class Page(val title: String, val icon: ImageVector) {
    Home("Home", HomeIcon),
    Card("Card over Wi-Fi", WifiIcon),
    Status("Status over Bluetooth", BluetoothIcon),
    About("About", AboutIcon),
    Help("Help", HelpIcon),
    Imprint("Imprint", ImprintIcon),
    Privacy("Privacy", PrivacyIcon),
    Changelog("Changelog", ChangelogIcon),
    Libraries("Libraries", LibrariesIcon),
    Settings("Settings", SettingsIcon),
}

/** The pages that sit under another in the menu, each with the line on its card there. */
private val GROUPS: Map<Page, List<Pair<Page, String>>> = mapOf(
    Page.Home to listOf(
        Page.Card to "Browse the card in the Knob over its Wi-Fi: download, upload, make folders and delete.",
    Page.Status to "See over Bluetooth how long the Knob has run and how many Wi-Fi networks it sees.",
    ),
    Page.About to listOf(
        Page.Help to "How to use the app, page by page.",
        Page.Imprint to "Who makes the app, and where its code and the Knob's live.",
        Page.Privacy to "What the app does with your data.",
        Page.Changelog to "What changed in each version.",
        Page.Libraries to "The open-source libraries the app is built on, with their licences.",
    ),
)

private val GROUPED = GROUPS.values.flatten().map { it.first }

/** The page [this] sits under: its group, or home for a page in none. */
val Page.parent: Page
    get() = GROUPS.entries.firstOrNull { (_, pages) -> pages.any { it.first == this } }?.key ?: Page.Home

/** How far the pages under a group are indented in the menu. */
private val GROUP_INDENT = 24.dp

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
        IconButton(onClick = onMenu) { AppIcon(MenuIcon, contentDescription = "Open menu", modifier = Modifier.size(BAR_ICON)) }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) { AppIcon(SettingsIcon, contentDescription = "Open settings") }
    }
}

/** The menu that slides in from the left. */
@Composable
fun Menu(drawer: DrawerState, page: Page, onClose: () -> Unit, onPage: (Page) -> Unit, onExit: () -> Unit) {
    var folded by rememberSaveable { mutableStateOf(emptyList<Page>()) }
    // Narrower than Material's 360 dp, so the page stays in sight on a phone of that width.
    ModalDrawerSheet(drawerState = drawer, modifier = Modifier.width(300.dp)) {
        Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { AppIcon(CloseIcon, contentDescription = "Close menu", modifier = Modifier.size(BAR_ICON)) }
            Text("TeeToTum", style = MaterialTheme.typography.titleLarge)
        }
        Column(modifier = Modifier.padding(12.dp)) {
            for (entry in Page.entries) {
                val grouped = entry in GROUPED
                if (grouped && entry.parent in folded) continue
                NavigationDrawerItem(
                    label = { Text(entry.title) },
                    icon = { AppIcon(entry.icon, contentDescription = null) },
                    selected = entry == page,
                    shape = ButtonShape,
                    onClick = { onPage(entry) },
                    // Grouped pages sit indented under their group, which folds them away with its trailing button.
                    modifier = if (grouped) Modifier.padding(start = GROUP_INDENT) else Modifier,
                    badge = if (entry in GROUPS) {
                        {
                            val open = entry !in folded
                            // Pulled into the item's end padding, so the arrow sits at the end of the entry.
                            IconButton(
                                onClick = { folded = if (open) folded + entry else folded - entry },
                                modifier = Modifier.offset(x = 20.dp),
                            ) {
                                AppIcon(
                                    if (open) CollapseIcon else ExpandIcon,
                                    contentDescription = if (open) "Hide pages under ${entry.title}" else "Show pages under ${entry.title}",
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("Exit") },
                icon = { AppIcon(ExitIcon, contentDescription = null) },
                selected = false,
                shape = ButtonShape,
                onClick = onExit,
            )
        }
    }
}

/**
 * A page from the menu other than [Page.Card]; [libraries] reads the list for [Page.Libraries],
 * [theme] and [onTheme] are the setting shown on [Page.Settings], [onPage] opens a page from a group's cards.
 */
@OptIn(ExperimentalMaterial3Api::class) // LibrariesContainer's overload with its own dialog state
@Composable
fun PageContent(
    page: Page,
    libraries: suspend () -> String,
    theme: Theme,
    onTheme: (Theme) -> Unit,
    onPage: (Page) -> Unit,
) {
    val cards = GROUPS[page]
    if (cards != null) {
        CardsPage(cards, onPage)
    } else if (page == Page.Settings) {
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
        MarkdownPage(page)
    }
}

/** A group's page: a card for each page under it, which [onPage] opens. */
@Composable
private fun CardsPage(cards: List<Pair<Page, String>>, onPage: (Page) -> Unit) {
    CardColumn {
        for ((card, detail) in cards) {
            PageCard(card.icon, card.title, onClick = { onPage(card) }) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A page written in Markdown: the text before its first `## ` heading, then a card for each such section. */
@Composable
private fun MarkdownPage(page: Page) {
    val (intro, sections) = sectionsOf(textOf(page))
    CardColumn {
        if (intro.isNotBlank()) PageMarkdown(intro)
        for ((title, body) in sections) {
            PageCard(iconOf(page, title), title) { PageMarkdown(body) }
        }
    }
}

@Composable
internal fun CardColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

/** A card with [icon] and [title] above its [content]; with [onClick], tapping the card calls it. */
@Composable
internal fun PageCard(icon: ImageVector, title: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val inner: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) { inner() }
    } else {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) { inner() }
    }
}

@Composable
private fun PageMarkdown(text: String) {
    Markdown(
        text,
        // The library's headings default to display sizes, far larger than the page title.
        typography = markdownTypography(
            h1 = MaterialTheme.typography.titleLarge,
            h2 = MaterialTheme.typography.titleMedium,
            h3 = MaterialTheme.typography.titleSmall,
            h4 = MaterialTheme.typography.titleSmall,
            h5 = MaterialTheme.typography.titleSmall,
            h6 = MaterialTheme.typography.titleSmall,
        ),
    )
}

/** Markdown [text] split at its `## ` headings: the text before the first, then each heading's title and body. */
internal fun sectionsOf(text: String): Pair<String, List<Pair<String, String>>> {
    val parts = text.split(Regex("""^## """, RegexOption.MULTILINE))
    val sections = parts.drop(1).map { part ->
        // A heading that links, like the changelog's `[0.2.0]`, shows as plain text on its card.
        val title = part.substringBefore('\n').replace(Regex("""\[([^\]]*)]"""), "$1").trim()
        title to part.substringAfter('\n', "").trim()
    }
    return parts.first().trim() to sections
}

/** The icon on each card of the Markdown pages, by its heading; every version in the changelog has [ReleaseIcon]. */
internal val SECTION_ICONS: Map<String, ImageVector> = mapOf(
    "Getting around" to GettingAroundIcon,
    "Connect" to ScanIcon,
    "On the card" to CardIcon,
    "From other apps" to ShareIcon,
    "Settings" to SettingsIcon,
    "About" to AboutIcon,
    "Provider (§ 5 DDG)" to ProviderIcon,
    "Contact" to ContactIcon,
    "The app and the Knob" to CodeIcon,
    "Liability" to LiabilityIcon,
    "Links" to LinkIcon,
    "Last updated" to DateIcon,
    "Camera" to CameraIcon,
    "Wi-Fi" to WifiIcon,
    "Bluetooth" to BluetoothIcon,
    "Status over Bluetooth" to BluetoothIcon,
    "Files" to FolderIcon,
)

private fun iconOf(page: Page, title: String): ImageVector =
    if (page == Page.Changelog) ReleaseIcon else SECTION_ICONS[title] ?: page.icon

internal fun textOf(page: Page): String = when (page) {
    Page.Help -> HELP
    Page.Imprint -> IMPRINT
    Page.Privacy -> PRIVACY
    // The changelog's own title and preamble repeat what the page title says.
    Page.Changelog -> CHANGELOG.substring(CHANGELOG.indexOf("\n## ").coerceAtLeast(0))
    Page.Home, Page.Card, Page.Status, Page.About, Page.Settings, Page.Libraries -> ""
}
