package io.github.teetotum_rs.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource

/**
 * The Knob's status over [bluetooth], read when the page opens and again on request, with the phone's time
 * of the read from [now], since the Knob has no clock; [access] asks for Bluetooth first.
 */
@Composable
fun StatusPage(
    bluetooth: Bluetooth,
    access: @Composable (content: @Composable () -> Unit) -> Unit,
    now: () -> Long = ::nowMillis,
    scroll: PageScroll? = null,
) {
    access {
        var attempt by remember { mutableIntStateOf(0) }
        var result by remember { mutableStateOf<Result<KnobStatus>?>(null) }
        var readAt by remember { mutableLongStateOf(0L) }
        val clock by rememberUpdatedState(now)
        LaunchedEffect(attempt) {
            result = null
            result = try {
                Result.success(bluetooth.status()).also { readAt = clock() }
            } catch (e: BluetoothFailed) {
                Result.failure(e)
            }
        }
        val current = result
        when {
            current == null -> Waiting(stringResource(Res.string.status_looking))

            current.isFailure -> Failed(
                current.exceptionOrNull()?.toMessage()?.text().orEmpty(),
                stringResource(Res.string.common_try_again),
            ) { attempt++ }

            else -> StatusCards(current.getOrThrow(), readAt, scroll) { attempt++ }
        }
    }
}

@Composable
private fun StatusCards(status: KnobStatus, readAt: Long, scroll: PageScroll?, onRead: () -> Unit) {
    CardColumn(scroll) {
        PageCard(Res.drawable.new_releases, stringResource(Res.string.status_firmware)) {
            StatusValue(firmwareText(status.version).text())
        }
        PageCard(Res.drawable.sd_card, stringResource(Res.string.status_card)) {
            StatusValue(cardText(status.cardBytes).text())
        }
        PageCard(Res.drawable.timer, stringResource(Res.string.status_running)) {
            StatusValue(uptimeText(status.uptimeSeconds))
        }
        PageCard(Res.drawable.wifi, stringResource(Res.string.status_networks)) {
            StatusValue(status.networks.toString())
        }
        PageCard(Res.drawable.schedule, stringResource(Res.string.status_read_at)) { StatusValue(dateTimeText(readAt)) }
        ActionButton(stringResource(Res.string.status_read_again), onClick = onRead)
    }
}

@Composable
private fun StatusValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}
