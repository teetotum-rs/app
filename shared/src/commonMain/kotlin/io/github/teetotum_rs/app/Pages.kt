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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantTextStyles
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryActionKind
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the app shows: home, the card, or one of the pages from the menu. */
enum class Page(val title: StringResource, val icon: DrawableResource) {
    Home(Res.string.page_home, Res.drawable.home),
    Card(Res.string.page_card, Res.drawable.wifi),
    Status(Res.string.page_status, Res.drawable.bluetooth),
    Plugins(Res.string.page_plugins, Res.drawable.extension),
    KnobSettings(Res.string.page_knob_settings, Res.drawable.tune),
    Firmware(Res.string.page_firmware, Res.drawable.memory),
    About(Res.string.page_about, Res.drawable.info),
    Help(Res.string.page_help, Res.drawable.help),
    Imprint(Res.string.page_imprint, Res.drawable.article),
    Privacy(Res.string.page_privacy, Res.drawable.shield),
    Changelog(Res.string.page_changelog, Res.drawable.history),
    Libraries(Res.string.page_libraries, Res.drawable.library_books),
    Settings(Res.string.page_settings, Res.drawable.settings),
}

/** The pages that sit under another in the menu, each with the line on its card there. */
private val GROUPS: Map<Page, List<Pair<Page, StringResource>>> = mapOf(
    Page.Home to listOf(
        Page.Card to Res.string.page_card_detail,
        Page.Status to Res.string.page_status_detail,
        Page.Plugins to Res.string.page_plugins_detail,
        Page.KnobSettings to Res.string.page_knob_settings_detail,
        Page.Firmware to Res.string.page_firmware_detail,
    ),
    Page.About to listOf(
        Page.Help to Res.string.page_help_detail,
        Page.Imprint to Res.string.page_imprint_detail,
        Page.Privacy to Res.string.page_privacy_detail,
        Page.Changelog to Res.string.page_changelog_detail,
        Page.Libraries to Res.string.page_libraries_detail,
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
        IconButton(onClick = onMenu) {
            AppIcon(
                Res.drawable.menu,
                contentDescription = stringResource(Res.string.menu_open),
                modifier = Modifier.size(BAR_ICON),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) {
            AppIcon(Res.drawable.settings, contentDescription = stringResource(Res.string.menu_open_settings))
        }
    }
}

/** The menu that slides in from the left; [folded] are the groups folded away, [onFold] takes a change of them. */
@Composable
fun Menu(
    drawer: DrawerState,
    page: Page,
    folded: Set<Page>,
    onFold: (Set<Page>) -> Unit,
    onClose: () -> Unit,
    onPage: (Page) -> Unit,
    onExit: () -> Unit,
) {
    // Narrower than Material's 360 dp, so the page stays in sight on a phone of that width.
    ModalDrawerSheet(drawerState = drawer, modifier = Modifier.width(300.dp)) {
        Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                AppIcon(
                    Res.drawable.close,
                    contentDescription = stringResource(Res.string.menu_close),
                    modifier = Modifier.size(BAR_ICON),
                )
            }
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        // The heading stays; the entries scroll, so Exit is in reach on a short screen with every group open.
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
            for (entry in Page.entries) {
                val grouped = entry in GROUPED
                if (grouped && entry.parent in folded) continue
                NavigationDrawerItem(
                    label = { Text(stringResource(entry.title)) },
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
                                onClick = { onFold(if (open) folded + entry else folded - entry) },
                                modifier = Modifier.offset(x = 20.dp),
                            ) {
                                AppIcon(
                                    if (open) Res.drawable.expand_less else Res.drawable.expand_more,
                                    contentDescription = stringResource(
                                        if (open) Res.string.menu_hide_group else Res.string.menu_show_group,
                                        stringResource(entry.title),
                                    ),
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
                label = { Text(stringResource(Res.string.menu_exit)) },
                icon = { AppIcon(Res.drawable.logout, contentDescription = null) },
                selected = false,
                shape = ButtonShape,
                onClick = onExit,
            )
        }
    }
}

/**
 * A page from the menu other than [Page.Card]; [libraries] reads the list for [Page.Libraries],
 * [preferences] and [onPreferences] are the settings shown on [Page.Settings], [onPage] opens a page
 * from a group's cards, and [scroll] keeps where the page was left.
 */
@OptIn(ExperimentalMaterial3Api::class) // LibrariesContainer's overload with its own dialog state
@Composable
fun PageContent(
    page: Page,
    libraries: suspend () -> String,
    preferences: Preferences,
    onPreferences: (Preferences) -> Unit,
    onPage: (Page) -> Unit,
    scroll: PageScroll? = null,
) {
    val cards = GROUPS[page]
    if (cards != null) {
        CardsPage(cards, onPage, scroll)
    } else if (page == Page.Settings) {
        SettingsPage(preferences, onPreferences, scroll)
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
            lazyListState = pageListState(scroll),
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
        MarkdownPage(page, scroll)
    }
}

/** A group's page: a card for each page under it, which [onPage] opens. */
@Composable
private fun CardsPage(cards: List<Pair<Page, StringResource>>, onPage: (Page) -> Unit, scroll: PageScroll?) {
    CardColumn(scroll) {
        for ((card, detail) in cards) {
            PageCard(card.icon, stringResource(card.title), onClick = { onPage(card) }) {
                Text(
                    stringResource(detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A page written in Markdown: the text before its first `## ` heading, then a card for each such section. */
@Composable
private fun MarkdownPage(page: Page, scroll: PageScroll?) {
    val (intro, sections) = sectionsOf(textOf(page))
    CardColumn(scroll) {
        if (intro.isNotBlank()) PageMarkdown(intro)
        for ((title, body) in sections) {
            PageCard(iconOf(page, title), title) { PageMarkdown(body) }
        }
    }
}

/** A page of cards that scrolls as one, back where [scroll] was left. */
@Composable
internal fun CardColumn(scroll: PageScroll?, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(pageScrollState(scroll)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

/** A card with [icon] and [title] above its [content]; with [onClick], tapping the card calls it. */
@Composable
internal fun PageCard(
    icon: DrawableResource,
    title: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
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

/** The icon on each card of the Markdown pages, by its heading; every version in the changelog has `new_releases`. */
internal val SECTION_ICONS: Map<String, DrawableResource> = mapOf(
    "Menu" to Res.drawable.menu,
    "Getting around" to Res.drawable.explore,
    "Connect" to Res.drawable.qr_code_scanner,
    "Card over Wi-Fi" to Res.drawable.wifi,
    "From other apps" to Res.drawable.share,
    "Settings" to Res.drawable.settings,
    "About" to Res.drawable.info,
    "Provider (§ 5 DDG)" to Res.drawable.badge,
    "Contact" to Res.drawable.mail,
    "The app and the Knob" to Res.drawable.code,
    "Liability" to Res.drawable.gavel,
    "Links" to Res.drawable.link,
    "Last updated" to Res.drawable.event,
    "Camera" to Res.drawable.photo_camera,
    "Wi-Fi" to Res.drawable.wifi,
    "Bluetooth" to Res.drawable.bluetooth,
    "Status over Bluetooth" to Res.drawable.bluetooth,
    "Plugins over Bluetooth" to Res.drawable.extension,
    "Knob settings over Bluetooth" to Res.drawable.tune,
    "Firmware over Bluetooth" to Res.drawable.memory,
    "Plugins" to Res.drawable.extension,
    "Files" to Res.drawable.folder,
)

private fun iconOf(page: Page, title: String): DrawableResource =
    if (page == Page.Changelog) Res.drawable.new_releases else SECTION_ICONS[title] ?: page.icon

internal fun textOf(page: Page): String = when (page) {
    Page.Help -> HELP

    Page.Imprint -> IMPRINT

    Page.Privacy -> PRIVACY

    // The changelog's own title and preamble repeat what the page title says.
    Page.Changelog -> CHANGELOG.substring(CHANGELOG.indexOf("\n## ").coerceAtLeast(0))

    Page.Home, Page.Card, Page.Status, Page.Plugins, Page.KnobSettings, Page.Firmware, Page.About, Page.Settings,
    Page.Libraries,
    -> ""
}
