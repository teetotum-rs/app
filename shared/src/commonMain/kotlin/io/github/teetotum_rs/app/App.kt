package io.github.teetotum_rs.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface Stage {
    data object Scan : Stage
    data class Joining(val code: JoinCode) : Stage
    data class Folder(val listing: Listing) : Stage
    data class Failed(val message: String) : Stage
}

/** A download, running or ended. */
private data class Transfer(
    val name: String,
    val done: Long = 0,
    val total: Long? = null,
    val result: String? = null,
)

/**
 * The whole app. [scanner] shows the camera and calls back with the first Knob code it reads;
 * [radio] joins that network; [downloads] keeps what is downloaded. A [code] given skips the scan.
 */
@Composable
fun App(
    radio: Radio,
    downloads: Downloads,
    code: JoinCode? = null,
    scanner: @Composable (onCode: (JoinCode) -> Unit) -> Unit,
) {
    val client = remember { CardClient(httpClient()) }
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<Stage>(code?.let { Stage.Joining(it) } ?: Stage.Scan) }
    var loading by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<Transfer?>(null) }

    fun fail(e: Exception) {
        if (e is CancellationException) throw e
        radio.leave()
        transfer = null
        stage = Stage.Failed(e.message ?: e.toString())
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
        transfer = Transfer(name)
        scope.launch {
            try {
                val sink = downloads.create(name)
                val where = client.download(path, sink) { done, total ->
                    transfer = Transfer(name, done, total)
                }
                transfer = Transfer(name, result = "Saved to $where")
            } catch (e: CardException) {
                transfer = Transfer(name, result = e.message)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                when (val current = stage) {
                    Stage.Scan -> ScanScreen(scanner) { stage = Stage.Joining(it) }
                    is Stage.Joining -> {
                        LaunchedEffect(current) {
                            try {
                                radio.join(current.code)
                                stage = Stage.Folder(client.list("/"))
                            } catch (e: Exception) {
                                fail(e)
                            }
                        }
                        Waiting("Joining ${current.code.ssid}")
                    }
                    is Stage.Folder -> FolderScreen(
                        listing = current.listing,
                        loading = loading,
                        transfer = transfer,
                        onOpen = { entry ->
                            val path = current.listing.path + entry.name
                            if (entry.directory) {
                                open("$path/")
                            } else if (transfer?.result != null || transfer == null) {
                                download(path, entry.name)
                            }
                        },
                        onUp = { open(parentOf(current.listing.path)) },
                    )
                    is Stage.Failed -> Failed(current.message) { stage = Stage.Scan }
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
    scanner: @Composable (onCode: (JoinCode) -> Unit) -> Unit,
    onCode: (JoinCode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("TeeToTum", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Open Card over Wi-Fi on the Knob and point the camera at the code on its screen.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) { scanner(onCode) }
    }
}

@Composable
private fun Waiting(text: String) {
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
private fun Failed(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text("Scan again") }
    }
}

@Composable
private fun FolderScreen(
    listing: Listing,
    loading: Boolean,
    transfer: Transfer?,
    onOpen: (Entry) -> Unit,
    onUp: () -> Unit,
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
            OutlinedButton(onClick = onUp, enabled = listing.path != "/" && !loading) { Text("Up") }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(listing.entries, key = { it.name }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !loading) { onOpen(entry) }
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
            "TeeToTum ${listing.version} · ${listing.entries.size} entries",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TransferLine(transfer: Transfer) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (transfer.result != null) {
            Text("${transfer.name}: ${transfer.result}", style = MaterialTheme.typography.bodyMedium)
        } else {
            val total = transfer.total
            val shown = if (total != null) " of ${sizeText(total)}" else ""
            Text(
                "${transfer.name}: ${sizeText(transfer.done)}$shown",
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
