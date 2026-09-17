package io.github.teetotum_rs.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** The Knob's status over [bluetooth], read when the page opens and again on request; [access] asks for Bluetooth first. */
@Composable
fun StatusPage(bluetooth: Bluetooth, access: @Composable (content: @Composable () -> Unit) -> Unit) {
    access {
        var attempt by remember { mutableIntStateOf(0) }
        var result by remember { mutableStateOf<Result<KnobStatus>?>(null) }
        LaunchedEffect(attempt) {
            result = null
            result = try {
                Result.success(bluetooth.status())
            } catch (e: BluetoothFailed) {
                Result.failure(e)
            }
        }
        val current = result
        when {
            current == null -> Waiting("Looking for the Knob")
            current.isFailure -> Failed(current.exceptionOrNull()?.message.orEmpty(), "Try again") { attempt++ }
            else -> StatusCards(current.getOrThrow()) { attempt++ }
        }
    }
}

@Composable
private fun StatusCards(status: KnobStatus, onRead: () -> Unit) {
    CardColumn {
        PageCard(ReleaseIcon, "Firmware") { StatusValue(firmwareText(status.version)) }
        PageCard(TimerIcon, "Running for") { StatusValue(uptimeText(status.uptimeSeconds)) }
        PageCard(WifiIcon, "Wi-Fi networks nearby") { StatusValue(status.networks.toString()) }
        ActionButton("Read again", onClick = onRead)
    }
}

@Composable
private fun StatusValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}
