package io.github.teetotum_rs.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.jetbrains.compose.resources.stringResource

/** A firmware file picked from the phone: what it says about itself, or why the Knob would not take it. */
private class PickedFirmware(val name: String, val firmware: FirmwareFile?, val problem: Message?)

/**
 * Sends a signed firmware file, picked with [picker], to the Knob over [bluetooth]; [access] asks for Bluetooth
 * first. The screen stays on while it sends.
 */
@Composable
fun FirmwarePage(
    bluetooth: Bluetooth,
    access: @Composable (content: @Composable () -> Unit) -> Unit,
    picker: @Composable (onPick: (List<Pick>) -> Unit) -> () -> Unit,
) {
    access {
        val scope = rememberCoroutineScope()
        var picked by remember { mutableStateOf<PickedFirmware?>(null) }
        var sending by remember { mutableStateOf<Sending?>(null) }
        var started by remember { mutableLongStateOf(0L) }
        val busy = sending.let { it != null && it.result == null }

        val pick = picker { picks ->
            picked = picks.firstOrNull()?.let(::pickedFirmwareOf) ?: return@picker
            sending = null
        }

        fun send(firmware: FirmwareFile) {
            scope.launch {
                started = nowMillis()
                sending = Sending(0, firmware.image.size)
                val result = try {
                    bluetooth.sendFirmware(firmware.image, firmware.signature) {
                        sending = Sending(it, firmware.image.size)
                    }
                    messageOf(Res.string.firmware_sent)
                } catch (e: BluetoothFailed) {
                    e.shown
                }
                sending = Sending(0, 0, result)
            }
        }

        if (busy) KeepScreenOn()
        CardColumn {
            Text(stringResource(Res.string.firmware_intro), style = MaterialTheme.typography.bodyLarge)
            PageCard(Res.drawable.memory, stringResource(Res.string.firmware_file)) {
                val current = picked
                val firmware = current?.firmware
                when {
                    current == null -> PluginLine(stringResource(Res.string.firmware_file_hint))

                    firmware == null -> PluginLine(
                        stringResource(Res.string.common_name_value, current.name, current.problem?.text().orEmpty()),
                    )

                    else -> {
                        Text(current.name, style = MaterialTheme.typography.titleSmall)
                        PluginLine(
                            stringResource(
                                Res.string.firmware_facts,
                                firmware.name,
                                firmware.version,
                                sizeText(firmware.image.size.toLong()),
                            ),
                        )
                    }
                }
                SendLine(sending, sending?.let { progressText(it, started) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        stringResource(Res.string.plugins_choose),
                        onClick = pick,
                        enabled = !busy,
                        filled = firmware == null,
                    )
                    if (firmware != null) {
                        ActionButton(
                            stringResource(Res.string.plugins_send),
                            onClick = { send(firmware) },
                            enabled = !busy,
                        )
                    }
                }
            }
        }
    }
}

/** Percent sent of the size, the rate and the time still to go, once a few seconds give a rate worth showing. */
@Composable
private fun progressText(sending: Sending, started: Long): String? {
    val seconds = (nowMillis() - started) / 1000.0
    if (sending.result != null || sending.sent == 0 || seconds < RATE_AFTER_S) return null
    val rate = sending.sent / seconds
    return stringResource(
        Res.string.firmware_progress,
        percentOf(sending.sent, sending.total),
        sizeText(sending.total.toLong()),
        sizeText(rate.toLong()),
        uptimeText(((sending.total - sending.sent) / rate).toLong()),
    )
}

private const val RATE_AFTER_S = 3

private fun pickedFirmwareOf(pick: Pick): PickedFirmware = try {
    if (pick.size > FirmwareService.IMAGE_MAX + FirmwareService.SIGNATURE) {
        throw FirmwareInvalid(Res.string.firmware_error_too_long, FirmwareService.IMAGE_MAX)
    }
    PickedFirmware(pick.name, firmwareOf(readAll(pick)), null)
} catch (e: FirmwareInvalid) {
    PickedFirmware(pick.name, null, e.shown)
} catch (e: IOException) {
    val problem = (e as? Shown)?.shown ?: e.message?.let(Message::Raw)
    PickedFirmware(pick.name, null, problem ?: messageOf(Res.string.file_error_read, pick.name))
}
