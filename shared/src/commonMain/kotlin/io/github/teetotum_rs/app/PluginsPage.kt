package io.github.teetotum_rs.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.io.IOException

/** Where a send stands: bytes sent of all, or how it ended. */
private class Sending(val sent: Int, val total: Int, val result: String? = null)

/** Your own plugin, picked from the phone: what it says about itself, or why it is none. */
private class OwnPlugin(val name: String, val wasm: ByteArray?, val about: PluginAbout?, val problem: String?)

private const val OWN = "own"

private const val SENT = "Sent. The Knob restarts and asks you to install it."

/**
 * Plugins to send to the Knob over [bluetooth]: those in the catalogue, and one picked with
 * [picker]; [access] asks for Bluetooth first.
 */
@Composable
fun PluginsPage(
    bluetooth: Bluetooth,
    access: @Composable (content: @Composable () -> Unit) -> Unit,
    picker: @Composable (onPick: (List<Pick>) -> Unit) -> () -> Unit,
) {
    access {
        val client = remember { httpClient() }
        val scope = rememberCoroutineScope()
        var attempt by remember { mutableIntStateOf(0) }
        var catalogue by remember { mutableStateOf<Result<Catalogue>?>(null) }
        var own by remember { mutableStateOf<OwnPlugin?>(null) }
        // The send in progress or last ended, by the plugin's id or OWN.
        var sending by remember { mutableStateOf<Pair<String, Sending>?>(null) }
        val busy = sending.let { it != null && it.second.result == null }

        LaunchedEffect(attempt) {
            catalogue = null
            catalogue = loadCatalogue(client)
        }

        fun send(key: String, load: suspend () -> ByteArray) {
            scope.launch {
                sending = key to Sending(0, 0)
                val result = sendOutcome(bluetooth, load) { sent, total -> sending = key to Sending(sent, total) }
                sending = key to Sending(0, 0, result)
            }
        }

        val pick = picker { picks ->
            val picked = picks.firstOrNull() ?: return@picker
            own = ownPluginOf(picked)
            if (sending?.first == OWN) sending = null
        }

        CardColumn {
            Text(
                "Open Settings > Receive on the Knob, then send a plugin. " +
                    "The Knob restarts and asks you to install it.",
                style = MaterialTheme.typography.bodyLarge,
            )
            catalogue?.fold(
                onSuccess = { found ->
                    for (plugin in found.plugins) {
                        CataloguePluginCard(plugin, sending?.takeIf { it.first == plugin.id }?.second, busy) {
                            send(plugin.id) { download(client, plugin) }
                        }
                    }
                },
                onFailure = { Failed(it.message.orEmpty(), "Try again") { attempt++ } },
            ) ?: CircularProgressIndicator()
            OwnPluginCard(own, sending?.takeIf { it.first == OWN }?.second, busy, onChoose = pick) { wasm ->
                send(OWN) { wasm }
            }
        }
    }
}

/** The catalogue, or why it could not be read. */
private suspend fun loadCatalogue(client: HttpClient): Result<Catalogue> = try {
    val response = client.get(CATALOGUE_URL)
    if (!response.status.isSuccess()) {
        throw PluginInvalid("The catalogue could not be read: HTTP ${response.status.value}.")
    }
    Result.success(catalogueOf(response.bodyAsText()))
} catch (e: PluginInvalid) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(PluginInvalid("The catalogue could not be read. Is the phone online?", e))
}

/** The plugin as listed in the catalogue, downloaded and checked against it. */
private suspend fun download(client: HttpClient, plugin: CataloguePlugin): ByteArray {
    val response = client.get(plugin.url)
    if (!response.status.isSuccess()) throw PluginInvalid("The download failed: HTTP ${response.status.value}.")
    return response.readRawBytes().also { checkDownload(plugin, it) }
}

/** Loads a plugin and sends it over [bluetooth]; the line that says how it ended. */
private suspend fun sendOutcome(
    bluetooth: Bluetooth,
    load: suspend () -> ByteArray,
    progress: (sent: Int, total: Int) -> Unit,
): String = try {
    val wasm = load()
    val header = slotHeader(wasm, describe(wasm))
    progress(0, wasm.size)
    bluetooth.sendPlugin(wasm, header) { progress(it, wasm.size) }
    SENT
} catch (e: PluginInvalid) {
    e.message.orEmpty()
} catch (e: BluetoothFailed) {
    e.message.orEmpty()
} catch (e: IOException) {
    "The plugin could not be downloaded. Is the phone online? (${e.message})"
}

private fun ownPluginOf(pick: Pick): OwnPlugin = try {
    if (pick.size > PluginService.MODULE_MAX) throw PluginInvalid("Longer than ${PluginService.MODULE_MAX} bytes.")
    val wasm = readAll(pick)
    OwnPlugin(pick.name, wasm, describe(wasm), null)
} catch (e: PluginInvalid) {
    OwnPlugin(pick.name, null, null, e.message)
} catch (e: IOException) {
    OwnPlugin(pick.name, null, null, e.message ?: "Could not read ${pick.name}.")
}

@Composable
private fun CataloguePluginCard(plugin: CataloguePlugin, sending: Sending?, busy: Boolean, onSend: () -> Unit) {
    PageCard(PluginIcon, plugin.name) {
        PluginText(null, plugin.summary, pluginFacts(plugin.version, plugin.size, plugin.rights))
        SendLine(sending)
        ActionButton("Send to Knob", onClick = onSend, enabled = !busy)
    }
}

@Composable
private fun OwnPluginCard(
    own: OwnPlugin?,
    sending: Sending?,
    busy: Boolean,
    onChoose: () -> Unit,
    onSend: (ByteArray) -> Unit,
) {
    PageCard(FolderIcon, "Your own plugin") {
        when {
            own == null -> PluginLine("A plugin you built and signed yourself, as a .wasm file.")
            own.about == null -> PluginLine("${own.name}: ${own.problem}")
            else -> with(own.about) { PluginText(name, summary, pluginFacts(version, size, rights)) }
        }
        SendLine(sending)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val wasm = own?.wasm
            ActionButton("Choose file", onClick = onChoose, enabled = !busy, filled = wasm == null)
            if (wasm != null) ActionButton("Send to Knob", onClick = { onSend(wasm) }, enabled = !busy)
        }
    }
}

/** All bytes of [pick]; throws [IOException] if it ends before its size. */
private fun readAll(pick: Pick): ByteArray = pick.open().use { source ->
    val bytes = ByteArray(pick.size.toInt())
    val buffer = ByteArray(READ_BUFFER)
    var at = 0
    while (at < bytes.size) {
        val count = source.read(buffer, minOf(buffer.size, bytes.size - at))
        if (count < 0) throw IOException("Could not read ${pick.name}.")
        buffer.copyInto(bytes, at, 0, count)
        at += count
    }
    bytes
}

private const val READ_BUFFER = 8192

@Composable
private fun PluginText(name: String?, summary: String, facts: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (name != null) Text(name, style = MaterialTheme.typography.titleSmall)
        Text(summary, style = MaterialTheme.typography.bodyLarge)
        PluginLine(facts)
    }
}

@Composable
private fun PluginLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SendLine(sending: Sending?) {
    if (sending == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sending.result != null) {
            Text(sending.result, style = MaterialTheme.typography.bodyMedium)
        } else if (sending.total == 0) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "${sizeText(sending.sent.toLong())} of ${sizeText(sending.total.toLong())}",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { sending.sent.toFloat() / sending.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
