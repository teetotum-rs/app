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
import org.jetbrains.compose.resources.stringResource

/** Where a send stands: bytes sent of all, or how it ended. */
internal class Sending(val sent: Int, val total: Int, val result: Message? = null)

/** Your own plugin, picked from the phone: what it says about itself, or why it is none. */
private class OwnPlugin(val name: String, val wasm: ByteArray?, val about: PluginAbout?, val problem: Message?)

private const val OWN = "own"

/**
 * The plugins on the Knob over [bluetooth], to delete, and plugins to send to it: those in the
 * catalogue, and one picked with [picker]; [access] asks for Bluetooth first. [load] says whether
 * the catalogue is read on opening or on tap.
 */
@Composable
fun PluginsPage(
    bluetooth: Bluetooth,
    access: @Composable (content: @Composable () -> Unit) -> Unit,
    picker: @Composable (onPick: (List<Pick>) -> Unit) -> () -> Unit,
    load: CatalogueLoad,
) {
    access {
        val client = remember { httpClient() }
        val scope = rememberCoroutineScope()
        // 0 until the catalogue is asked for.
        var attempt by remember { mutableIntStateOf(if (load == CatalogueLoad.Open) 1 else 0) }
        var catalogue by remember { mutableStateOf<Result<Catalogue>?>(null) }
        var own by remember { mutableStateOf<OwnPlugin?>(null) }
        // The send in progress or last ended, by the plugin's id or OWN.
        var sending by remember { mutableStateOf<Pair<String, Sending>?>(null) }
        val knob = remember { KnobPlugins(bluetooth) }
        // One Bluetooth session at a time.
        val busy = sending.let { it != null && it.second.result == null } || knob.busy

        LaunchedEffect(attempt) {
            if (attempt == 0) return@LaunchedEffect
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
            Text(stringResource(Res.string.plugins_intro), style = MaterialTheme.typography.bodyLarge)
            KnobPluginsCard(knob, busy)
            if (attempt == 0) {
                PageCard(Res.drawable.cloud_download, stringResource(Res.string.plugins_catalogue)) {
                    PluginLine(stringResource(Res.string.plugins_catalogue_hint))
                    ActionButton(stringResource(Res.string.plugins_catalogue_load), onClick = { attempt++ })
                }
            } else {
                catalogue?.fold(
                    onSuccess = { found ->
                        for (plugin in found.plugins) {
                            CataloguePluginCard(plugin, sending?.takeIf { it.first == plugin.id }?.second, busy) {
                                send(plugin.id) { download(client, plugin) }
                            }
                        }
                    },
                    onFailure = {
                        Failed(it.toMessage().text(), stringResource(Res.string.common_try_again)) { attempt++ }
                    },
                ) ?: CircularProgressIndicator()
            }
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
        throw PluginInvalid(messageOf(Res.string.plugins_error_catalogue_http, response.status.value))
    }
    Result.success(catalogueOf(response.bodyAsText()))
} catch (e: PluginInvalid) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(PluginInvalid(messageOf(Res.string.plugins_error_catalogue_offline), e))
}

/** The plugin as listed in the catalogue, downloaded and checked against it. */
private suspend fun download(client: HttpClient, plugin: CataloguePlugin): ByteArray {
    val response = client.get(plugin.url)
    if (!response.status.isSuccess()) {
        throw PluginInvalid(messageOf(Res.string.plugins_error_download_http, response.status.value))
    }
    return response.readRawBytes().also { checkDownload(plugin, it) }
}

/** Loads a plugin and sends it over [bluetooth]; the line that says how it ended. */
private suspend fun sendOutcome(
    bluetooth: Bluetooth,
    load: suspend () -> ByteArray,
    progress: (sent: Int, total: Int) -> Unit,
): Message = try {
    val wasm = load()
    val header = slotHeader(wasm, describe(wasm))
    progress(0, wasm.size)
    bluetooth.sendPlugin(wasm, header) { progress(it, wasm.size) }
    messageOf(Res.string.plugins_sent)
} catch (e: PluginInvalid) {
    e.shown
} catch (e: BluetoothFailed) {
    e.shown
} catch (e: IOException) {
    messageOf(Res.string.plugins_error_download_offline, e.message.toString())
}

private fun ownPluginOf(pick: Pick): OwnPlugin = try {
    if (pick.size > PluginService.MODULE_MAX) {
        throw PluginInvalid(messageOf(Res.string.plugins_error_too_long, PluginService.MODULE_MAX))
    }
    val wasm = readAll(pick)
    OwnPlugin(pick.name, wasm, describe(wasm), null)
} catch (e: PluginInvalid) {
    OwnPlugin(pick.name, null, null, e.shown)
} catch (e: IOException) {
    val problem = (e as? Shown)?.shown ?: e.message?.let(Message::Raw)
    OwnPlugin(pick.name, null, null, problem ?: messageOf(Res.string.file_error_read, pick.name))
}

@Composable
private fun CataloguePluginCard(plugin: CataloguePlugin, sending: Sending?, busy: Boolean, onSend: () -> Unit) {
    PageCard(Res.drawable.extension, plugin.name) {
        PluginText(null, plugin.summary, pluginFacts(plugin.version, plugin.size, plugin.rights))
        SendLine(sending)
        ActionButton(stringResource(Res.string.plugins_send), onClick = onSend, enabled = !busy)
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
    PageCard(Res.drawable.folder, stringResource(Res.string.plugins_own)) {
        when {
            own == null -> PluginLine(stringResource(Res.string.plugins_own_hint))

            own.about == null -> {
                PluginLine(stringResource(Res.string.common_name_value, own.name, own.problem?.text().orEmpty()))
            }

            else -> with(own.about) { PluginText(name, summary, pluginFacts(version, size, rights)) }
        }
        SendLine(sending)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val wasm = own?.wasm
            ActionButton(
                stringResource(Res.string.plugins_choose),
                onClick = onChoose,
                enabled = !busy,
                filled = wasm == null,
            )
            if (wasm != null) {
                ActionButton(stringResource(Res.string.plugins_send), onClick = { onSend(wasm) }, enabled = !busy)
            }
        }
    }
}

/** All bytes of [pick]; throws [IOException] if it ends before its size. */
internal fun readAll(pick: Pick): ByteArray = pick.open().use { source ->
    val bytes = ByteArray(pick.size.toInt())
    val buffer = ByteArray(READ_BUFFER)
    var at = 0
    while (at < bytes.size) {
        val count = source.read(buffer, minOf(buffer.size, bytes.size - at))
        if (count < 0) throw FileFailed(Res.string.file_error_read, pick.name)
        buffer.copyInto(bytes, at, 0, count)
        at += count
    }
    bytes
}

private const val READ_BUFFER = 8192

@Composable
internal fun PluginText(name: String?, summary: String, facts: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (name != null) Text(name, style = MaterialTheme.typography.titleSmall)
        Text(summary, style = MaterialTheme.typography.bodyLarge)
        PluginLine(facts)
    }
}

@Composable
internal fun PluginLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** How a send stands; [progress] replaces the line of bytes sent while it runs. */
@Composable
internal fun SendLine(sending: Sending?, progress: String? = null) {
    if (sending == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sending.result != null) {
            Text(sending.result.text(), style = MaterialTheme.typography.bodyMedium)
        } else if (sending.total == 0) {
            if (progress != null) Text(progress, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                progress ?: stringResource(
                    Res.string.plugins_progress,
                    percentOf(sending.sent, sending.total),
                    sizeText(sending.total.toLong()),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { sending.sent.toFloat() / sending.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
