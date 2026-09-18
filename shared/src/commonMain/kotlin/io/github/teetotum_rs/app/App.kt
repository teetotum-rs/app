@file:Suppress("TooManyFunctions")

package io.github.teetotum_rs.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.sqrt

private sealed interface Stage {
    /** Waiting for a Knob code; [camera] opens the camera right away. */
    data class Scan(val camera: Boolean = false) : Stage
    data class Joining(val code: JoinCode) : Stage
    data class Folder(val listing: Listing) : Stage
    data class Failed(val message: Message) : Stage
}

/** Something the user is asked before it happens. */
private sealed interface Question {
    data class Remove(val entry: Entry) : Question
    data class Replace(val picks: List<Pick>, val taken: List<String>, val shared: Boolean) : Question
    data object NewFolder : Question
}

/** A download, upload or change, running or ended; [name] is a file's or folder's, or a count of files. */
private data class Transfer(
    val name: Message,
    val done: Long = 0,
    val total: Long? = null,
    val result: Message? = null,
)

/**
 * The whole app. [scanner] shows the camera and calls back with the first Knob code it reads;
 * [radio] joins that network; [downloads] keeps what is downloaded. [bluetooth] reads the Knob's status
 * once [bluetoothAccess] has asked for Bluetooth. [picker] returns a function
 * that lets the user pick files to send; [back] takes the system's back gesture while enabled.
 * A [code] given skips the scan. [shared] holds files another app shared, offered for the folder
 * the user opens until sent or declined, which [onShareEnd] reports. [onExit] closes the app from
 * the menu; [libraries] reads the list of libraries it shows. [preferences] are the settings, [onPreferences]
 * takes a change, including the page left for [Start.Last].
 */
@Suppress(
    // The root holds the navigation and the card's actions, and shows any failure of those to the user.
    "CyclomaticComplexMethod",
    "LongMethod",
    "TooGenericExceptionCaught",
    // [back] registers one handler per call.
    "ContentSlotReused",
)
@Composable
fun App(
    radio: Radio,
    downloads: Downloads,
    bluetooth: Bluetooth,
    bluetoothAccess: @Composable (content: @Composable () -> Unit) -> Unit,
    picker: @Composable (onPick: (List<Pick>) -> Unit) -> () -> Unit,
    back: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
    code: JoinCode? = null,
    shared: Shared? = null,
    onShareEnd: () -> Unit = {},
    onExit: () -> Unit = {},
    libraries: suspend () -> String = { "{}" },
    preferences: Preferences = Preferences(),
    onPreferences: (Preferences) -> Unit = {},
    scanner: @Composable (onCode: (JoinCode) -> Unit) -> Unit,
) {
    val client = remember { CardClient(httpClient()) }
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<Stage>(code?.let { Stage.Joining(it) } ?: Stage.Scan()) }
    var loading by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<Transfer?>(null) }
    var question by remember { mutableStateOf<Question?>(null) }
    var page by remember {
        mutableStateOf(
            when {
                code != null || shared != null -> Page.Card
                preferences.start == Start.Last -> preferences.lastPage
                else -> Page.Home
            },
        )
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val busy = loading || transfer?.let { it.result == null } == true

    fun fail(e: Exception) {
        if (e is CancellationException) throw e
        radio.leave()
        transfer = null
        stage = Stage.Failed(e.toMessage())
    }

    fun open(path: String) {
        loading = true
        scope.launch {
            try {
                stage = Stage.Folder(client.list(path))
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading = false
            }
        }
    }

    fun download(path: String, name: String) {
        val named = Message.Raw(name)
        transfer = Transfer(named)
        scope.launch {
            try {
                val sink = downloads.create(name)
                val where = client.download(path, sink) { done, total ->
                    transfer = Transfer(named, done, total)
                }
                transfer = Transfer(named, result = messageOf(Res.string.card_saved_to, where))
            } catch (e: CardException) {
                transfer = Transfer(named, result = e.shown)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun upload(path: String, picks: List<Pick>) {
        scope.launch {
            try {
                for (pick in picks) {
                    val named = Message.Raw(pick.name)
                    transfer = Transfer(named, total = pick.size)
                    client.upload(path, pick) { done, total -> transfer = Transfer(named, done, total) }
                }
                transfer = Transfer(
                    picks.singleOrNull()?.let { Message.Raw(it.name) }
                        ?: Message.Plural(Res.plurals.card_files, picks.size),
                    result = messageOf(Res.string.card_sent),
                )
            } catch (e: CardException) {
                transfer = Transfer(transfer?.name ?: Message.Raw(""), result = e.shown)
            } catch (e: Exception) {
                fail(e)
                return@launch
            }
            open(path)
        }
    }

    /** Runs a change to the card, then lists [path] again to show it. */
    fun change(path: String, name: String, done: StringResource, action: suspend () -> Unit) {
        loading = true
        scope.launch {
            try {
                action()
                transfer = Transfer(Message.Raw(name), result = messageOf(done))
            } catch (e: CardException) {
                transfer = Transfer(Message.Raw(name), result = e.shown)
            } catch (e: Exception) {
                loading = false
                fail(e)
                return@launch
            }
            open(path)
        }
    }

    /** Sends [picks] into the open folder, asking first where names are taken. */
    fun send(picks: List<Pick>, fromShare: Boolean) {
        val listing = (stage as? Stage.Folder)?.listing ?: return
        if (picks.isEmpty()) return
        val taken = picks.map { it.name }.filter { name -> listing.entries.any { it.name == name } }
        if (taken.isNotEmpty()) {
            question = Question.Replace(picks, taken, fromShare)
            return
        }
        if (fromShare) onShareEnd()
        upload(listing.path, picks)
    }

    val folder = stage as? Stage.Folder
    val pick = picker { picks -> send(picks, fromShare = false) }
    // The handler registered last wins: going up a folder comes before going home.
    back(page != Page.Home) { page = page.parent }
    back(page == Page.Card && folder != null && folder.listing.path != "/" && !busy) {
        folder?.let { open(parentOf(it.listing.path)) }
    }
    // Files shared from another app are sent from the card.
    LaunchedEffect(shared) { if (shared != null) page = Page.Card }
    val latest by rememberUpdatedState(preferences)
    val keep by rememberUpdatedState(onPreferences)
    LaunchedEffect(page) {
        if (page != Page.Settings && page != latest.lastPage) keep(latest.copy(lastPage = page))
    }

    MaterialTheme(colorScheme = colorSchemeOf(preferences.theme)) {
        ModalNavigationDrawer(
            drawerState = drawer,
            // Opened by the button only: a swipe from the left edge is Android's back gesture.
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                Menu(
                    drawer,
                    page,
                    onClose = { scope.launch { drawer.close() } },
                    onPage = {
                        page = it
                        scope.launch { drawer.close() }
                    },
                    onExit = onExit,
                )
            },
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    TopBar(
                        stringResource(page.title),
                        onMenu = { scope.launch { drawer.open() } },
                        onSettings = { page = Page.Settings },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        if (page == Page.Status) {
                            StatusPage(bluetooth, bluetoothAccess)
                        } else if (page == Page.KnobSettings) {
                            KnobSettingsPage(bluetooth, bluetoothAccess)
                        } else if (page == Page.Plugins) {
                            PluginsPage(bluetooth, bluetoothAccess, picker, preferences.catalogue)
                        } else if (page == Page.Firmware) {
                            FirmwarePage(bluetooth, bluetoothAccess, picker)
                        } else if (page != Page.Card) {
                            PageContent(
                                page,
                                libraries,
                                preferences,
                                onPreferences,
                                onPage = { page = it },
                            )
                        } else {
                            when (val current = stage) {
                                is Stage.Scan -> ScanScreen(current.camera, shared, scanner) {
                                    stage = Stage.Joining(it)
                                }

                                is Stage.Joining -> {
                                    LaunchedEffect(current) {
                                        try {
                                            radio.join(current.code)
                                            stage = Stage.Folder(client.list("/"))
                                        } catch (e: Exception) {
                                            fail(e)
                                        }
                                    }
                                    Waiting(stringResource(Res.string.card_joining, current.code.ssid))
                                }

                                is Stage.Folder -> {
                                    val here = current.listing.path
                                    FolderScreen(
                                        listing = current.listing,
                                        loading = loading,
                                        busy = busy,
                                        transfer = transfer,
                                        shared = shared,
                                        onSendShare = { shared?.let { send(it.picks, fromShare = true) } },
                                        onDropShare = onShareEnd,
                                        onOpen = { entry ->
                                            val path = here + entry.name
                                            if (entry.directory) open("$path/") else download(path, entry.name)
                                        },
                                        onHold = { question = Question.Remove(it) },
                                        onUp = { open(parentOf(here)) },
                                        onNewFolder = { question = Question.NewFolder },
                                        onUpload = pick,
                                    )
                                    question?.let { asked ->
                                        Ask(asked, onDismiss = { question = null }) { name ->
                                            question = null
                                            when (asked) {
                                                is Question.Remove -> {
                                                    change(here, asked.entry.name, Res.string.card_deleted) {
                                                        client.delete(here + asked.entry.name)
                                                    }
                                                }

                                                is Question.Replace -> {
                                                    if (asked.shared) onShareEnd()
                                                    upload(here, asked.picks)
                                                }

                                                Question.NewFolder -> change(here, name, Res.string.card_made) {
                                                    client.makeFolder(here + name)
                                                }
                                            }
                                        }
                                    }
                                }

                                is Stage.Failed -> Failed(current.message.text()) { stage = Stage.Scan(camera = true) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The folder above [path], which ends in `/`. */
fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    return trimmed.substring(0, trimmed.lastIndexOf('/') + 1).ifEmpty { "/" }
}

@Composable
private fun ScanScreen(
    camera: Boolean,
    shared: Shared?,
    scanner: @Composable (onCode: (JoinCode) -> Unit) -> Unit,
    onCode: (JoinCode) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(camera) }
    BoxWithConstraints {
        val wide = maxWidth > maxHeight
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.card_scan_intro), style = MaterialTheme.typography.bodyLarge)
            if (shared != null && shared.picks.isNotEmpty()) {
                Text(
                    pluralStringResource(Res.plurals.card_scan_shared, shared.picks.size, shared.picks.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (open && wide) {
                // Landscape: button beside the image, the square as tall as the remaining height.
                Row(modifier = Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Viewfinder(onCode, Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true), scanner)
                    CloseCamera { open = false }
                }
            } else if (open) {
                // Square on the shorter free side, so the button below always stays on screen.
                Viewfinder(
                    onCode,
                    Modifier.weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true),
                    scanner,
                )
                CloseCamera { open = false }
            } else {
                ActionButton(stringResource(Res.string.card_scan_code), onClick = { open = true })
            }
        }
    }
}

@Composable
private fun CloseCamera(onClick: () -> Unit) {
    ActionButton(stringResource(Res.string.card_close_camera), onClick = onClick, filled = false)
}

/** The camera with corner marks around its middle half, a hint at how large the code should appear. */
@Composable
private fun Viewfinder(
    onCode: (JoinCode) -> Unit,
    modifier: Modifier = Modifier,
    scanner: @Composable (onCode: (JoinCode) -> Unit) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        scanner(onCode)
        Canvas(modifier = Modifier.matchParentSize()) {
            // A square of side 1/√2 covers half the image.
            val side = size.minDimension / sqrt(2f)
            val arm = side / 8
            val stroke = 4.dp.toPx()
            val left = (size.width - side) / 2
            val top = (size.height - side) / 2
            for ((x, dx) in listOf(left to 1f, left + side to -1f)) {
                for ((y, dy) in listOf(top to 1f, top + side to -1f)) {
                    drawLine(accent, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
                    drawLine(accent, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
internal fun Waiting(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun Failed(message: String, retry: String = stringResource(Res.string.card_scan_again), onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        ActionButton(retry, onClick = onRetry)
    }
}

@Composable
private fun FolderScreen(
    listing: Listing,
    loading: Boolean,
    busy: Boolean,
    transfer: Transfer?,
    shared: Shared?,
    onSendShare: () -> Unit,
    onDropShare: () -> Unit,
    onOpen: (Entry) -> Unit,
    onHold: (Entry) -> Unit,
    onUp: () -> Unit,
    onNewFolder: () -> Unit,
    onUpload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                listing.path,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f),
            )
            if (loading) CircularProgressIndicator()
        }
        FolderButtons(canGoUp = listing.path != "/" && !busy, busy, onUp, onNewFolder, onUpload)
        if (shared != null) SharedOffer(shared, busy, onSendShare, onDropShare)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(listing.entries, key = { it.name }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            enabled = !busy,
                            onLongClick = { onHold(entry) },
                            onClick = { onOpen(entry) },
                        )
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (entry.directory) "${entry.name}/" else entry.name,
                        modifier = Modifier.weight(1f),
                    )
                    if (!entry.directory) Text(sizeText(entry.size))
                }
                HorizontalDivider()
            }
        }
        if (transfer != null) TransferLine(transfer)
        Text(
            pluralStringResource(
                Res.plurals.card_footer,
                listing.entries.size,
                listing.version,
                listing.entries.size,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Up, new folder and upload, above the folder's entries. */
@Composable
private fun FolderButtons(
    canGoUp: Boolean,
    busy: Boolean,
    onUp: () -> Unit,
    onNewFolder: () -> Unit,
    onUpload: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(stringResource(Res.string.card_up), onClick = onUp, enabled = canGoUp, filled = false)
        ActionButton(stringResource(Res.string.card_new_folder), onClick = onNewFolder, enabled = !busy, filled = false)
        ActionButton(stringResource(Res.string.card_upload), onClick = onUpload, enabled = !busy)
    }
}

/** The files another app shared, offered for the folder on screen. */
@Composable
private fun SharedOffer(shared: Shared, busy: Boolean, onSend: () -> Unit, onDrop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val count = shared.picks.size
            Text(
                if (count == 0) {
                    stringResource(Res.string.card_share_empty)
                } else {
                    pluralStringResource(Res.plurals.card_share_send, count, count)
                },
            )
            if (shared.skipped > 0) {
                Text(
                    pluralStringResource(Res.plurals.card_share_skipped, shared.skipped, shared.skipped),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (count > 0) {
                    ActionButton(stringResource(Res.string.card_send_here), onClick = onSend, enabled = !busy)
                }
                ActionButton(
                    stringResource(if (count == 0) Res.string.common_ok else Res.string.common_cancel),
                    onClick = onDrop,
                    filled = false,
                )
            }
        }
    }
}

/** Asks [question]; [onYes] gets the folder name the user typed, or "" where none is asked. */
@Composable
private fun Ask(question: Question, onDismiss: () -> Unit, onYes: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val (title, text, yes) = when (question) {
        is Question.Remove -> Triple(
            stringResource(Res.string.card_delete_title, question.entry.name),
            stringResource(
                if (question.entry.directory) Res.string.card_delete_folder else Res.string.card_delete_file,
            ),
            stringResource(Res.string.card_delete),
        )

        is Question.Replace -> Triple(
            pluralStringResource(Res.plurals.card_replace_title, question.taken.size, question.taken.size),
            question.taken.joinToString("\n"),
            stringResource(Res.string.card_replace),
        )

        Question.NewFolder -> Triple(
            stringResource(Res.string.card_new_folder),
            null,
            stringResource(Res.string.card_make),
        )
    }
    val ready = question != Question.NewFolder || name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (question == Question.NewFolder) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("/", "") },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.card_folder_name)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            } else if (text != null) {
                Text(text)
            }
        },
        confirmButton = { ActionButton(yes, onClick = { onYes(name.trim()) }, enabled = ready) },
        dismissButton = { ActionButton(stringResource(Res.string.common_cancel), onClick = onDismiss, filled = false) },
    )
}

@Composable
private fun TransferLine(transfer: Transfer) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val name = transfer.name.text()
        if (transfer.result != null) {
            Text(
                stringResource(Res.string.common_name_value, name, transfer.result.text()),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val total = transfer.total
            val done = sizeText(transfer.done)
            Text(
                if (total != null) {
                    stringResource(Res.string.card_progress, name, done, sizeText(total))
                } else {
                    stringResource(Res.string.common_name_value, name, done)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { transfer.done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A size in the largest unit that keeps it at one or more, with one decimal from KiB on. */
fun sizeText(bytes: Long): String {
    val units = listOf("KiB", "MiB", "GiB")
    if (bytes < 1024) return "$bytes B"
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val tenths = (value * 10).toLong()
    return "${tenths / 10}.${tenths % 10} ${units[unit]}"
}
