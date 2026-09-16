package io.github.teetotum_rs.app

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private sealed interface Stage {
    data object Scan : Stage
    data class Joining(val code: JoinCode) : Stage
    data class Folder(val listing: Listing) : Stage
    data class Failed(val message: String) : Stage
}

/**
 * The whole app. [scanner] shows the camera and calls back with the first Knob code it reads;
 * [radio] joins that network.
 */
@Composable
fun App(radio: Radio, scanner: @Composable (onCode: (JoinCode) -> Unit) -> Unit) {
    val client = remember { CardClient(httpClient()) }
    var stage by remember { mutableStateOf<Stage>(Stage.Scan) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                when (val current = stage) {
                    Stage.Scan -> ScanScreen(scanner) { stage = Stage.Joining(it) }
                    is Stage.Joining -> {
                        LaunchedEffect(current) {
                            stage = try {
                                radio.join(current.code)
                                Stage.Folder(client.list("/"))
                            } catch (e: Exception) {
                                radio.leave()
                                Stage.Failed(e.message ?: e.toString())
                            }
                        }
                        Waiting("Joining ${current.code.ssid}")
                    }
                    is Stage.Folder -> FolderScreen(current.listing)
                    is Stage.Failed -> Failed(current.message) { stage = Stage.Scan }
                }
            }
        }
    }
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
private fun FolderScreen(listing: Listing) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(listing.path, style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(listing.entries, key = { it.name }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
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
        Text(
            "TeeToTum ${listing.version} · ${listing.entries.size} entries",
            style = MaterialTheme.typography.bodySmall,
        )
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
