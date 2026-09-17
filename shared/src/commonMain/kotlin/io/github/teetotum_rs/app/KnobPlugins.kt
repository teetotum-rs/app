package io.github.teetotum_rs.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Where a delete stands: the plugin's slot, and how it ended. */
internal class Deleting(val slot: Int, val result: Message? = null)

/** The plugins on the Knob over [bluetooth], and the delete in progress or last ended. */
@Stable
internal class KnobPlugins(private val bluetooth: Bluetooth) {
    /** The plugins, or why they could not be read; null while they are read. */
    var list by mutableStateOf<Result<List<KnobPlugin>>?>(null)
        private set

    var deleting by mutableStateOf<Deleting?>(null)
        private set

    /** Bumped to read the list again. */
    var reading by mutableIntStateOf(0)
        private set

    val busy: Boolean get() = list == null || deleting.let { it != null && it.result == null }

    fun readAgain() {
        reading++
    }

    suspend fun read() {
        list = null
        list = try {
            Result.success(bluetooth.plugins())
        } catch (e: BluetoothFailed) {
            Result.failure(e)
        }
    }

    suspend fun delete(slot: Int) {
        deleting = Deleting(slot)
        deleting = try {
            bluetooth.deletePlugin(slot)
            // The Knob restarts, so its list is not read again here.
            list = list?.map { plugins -> plugins.filter { it.slot != slot } }
            Deleting(slot, messageOf(Res.string.plugins_deleted))
        } catch (e: BluetoothFailed) {
            Deleting(slot, e.shown)
        }
    }
}

/** The plugins on [knob], read when the card appears, each in a slot with a delete button while not [busy]. */
@Composable
internal fun KnobPluginsCard(knob: KnobPlugins, busy: Boolean) {
    val scope = rememberCoroutineScope()
    var asked by remember { mutableStateOf<KnobPlugin?>(null) }
    LaunchedEffect(knob.reading) { knob.read() }

    asked?.let { plugin ->
        DeleteDialog(plugin, onDismiss = { asked = null }) {
            asked = null
            plugin.slot?.let { slot -> scope.launch { knob.delete(slot) } }
        }
    }

    PageCard(Res.drawable.memory, stringResource(Res.string.plugins_on_knob)) {
        val list = knob.list
        when {
            list == null -> {
                PluginLine(stringResource(Res.string.plugins_on_knob_looking))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            list.isFailure -> PluginLine(list.exceptionOrNull()?.toMessage()?.text().orEmpty())

            list.getOrThrow().isEmpty() -> PluginLine(stringResource(Res.string.plugins_on_knob_none))

            else -> for (plugin in list.getOrThrow()) {
                KnobPluginLine(plugin, busy) { asked = plugin }
            }
        }
        knob.deleting?.let { deleting ->
            if (deleting.result == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(deleting.result.text(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        ActionButton(
            stringResource(Res.string.plugins_on_knob_read),
            onClick = knob::readAgain,
            enabled = !busy,
            filled = false,
        )
    }
}

@Composable
private fun KnobPluginLine(plugin: KnobPlugin, busy: Boolean, onDelete: () -> Unit) {
    val state = when {
        plugin.bundled -> Res.string.plugins_state_bundled
        plugin.installed -> Res.string.plugins_state_installed
        else -> Res.string.plugins_state_waiting
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PluginText(
            plugin.name,
            plugin.summary,
            stringResource(
                Res.string.plugins_on_knob_facts,
                plugin.version,
                sizeText(plugin.size),
                stringResource(state),
            ),
        )
        if (plugin.deletable) {
            ActionButton(stringResource(Res.string.plugins_delete), onClick = onDelete, enabled = !busy, filled = false)
        }
    }
}

@Composable
private fun DeleteDialog(plugin: KnobPlugin, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(Res.drawable.delete, contentDescription = null) },
        title = { Text(stringResource(Res.string.plugins_delete_title, plugin.name)) },
        text = { Text(stringResource(Res.string.plugins_delete_text)) },
        confirmButton = { ActionButton(stringResource(Res.string.plugins_delete), onClick = onDelete) },
        dismissButton = { ActionButton(stringResource(Res.string.common_cancel), onClick = onDismiss, filled = false) },
    )
}
