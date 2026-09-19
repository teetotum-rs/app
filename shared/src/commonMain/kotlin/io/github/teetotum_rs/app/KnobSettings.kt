package io.github.teetotum_rs.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The Knob's settings the app changes, as the steps of its Settings ring: [theme] an index into
 * [THEMES], [brightness] in [BRIGHTNESS], [haptics] in [HAPTICS] with 0 for off, and [orientation]
 * in quarter turns clockwise.
 */
data class KnobSettings(val theme: Int, val brightness: Int, val haptics: Int, val orientation: Int) {
    /** The bytes the Knob's settings characteristic takes. */
    fun encode(): ByteArray =
        byteArrayOf(FORMAT, theme.toByte(), brightness.toByte(), haptics.toByte(), orientation.toByte())

    companion object {
        /** The layout of the bytes; the Knob refuses any other. */
        const val FORMAT: Byte = 1
        const val SIZE = 5
        val BRIGHTNESS = 1..10
        val HAPTICS = 0..9
        const val ORIENTATIONS = 4

        /** The Knob's themes, in the order its firmware numbers them. */
        val THEMES: List<StringResource> = listOf(
            Res.string.knob_theme_teal,
            Res.string.knob_theme_orange,
            Res.string.knob_theme_magenta,
            Res.string.knob_theme_violet,
            Res.string.knob_theme_blue,
            Res.string.knob_theme_pink,
            Res.string.knob_theme_red,
            Res.string.knob_theme_green,
            Res.string.knob_theme_cyan,
            Res.string.knob_theme_indigo,
            Res.string.knob_theme_grey,
        )

        /** Settings from the Knob's bytes, or null for another format or a step out of range. */
        fun decode(bytes: ByteArray): KnobSettings? {
            if (bytes.size != SIZE || bytes[0] != FORMAT) return null
            val steps = bytes.map { it.toInt() and 0xff }
            return KnobSettings(theme = steps[1], brightness = steps[2], haptics = steps[3], orientation = steps[4])
                .takeIf {
                    it.theme in THEMES.indices &&
                        it.brightness in BRIGHTNESS &&
                        it.haptics in HAPTICS &&
                        it.orientation in 0 until ORIENTATIONS
                }
        }
    }
}

/**
 * The Knob's theme, brightness, clicks and orientation over [bluetooth], read when the page opens.
 * A change is written at once and shows on the Knob; [access] asks for Bluetooth first.
 */
@Composable
fun KnobSettingsPage(
    bluetooth: Bluetooth,
    access: @Composable (content: @Composable () -> Unit) -> Unit,
    scroll: PageScroll? = null,
) {
    access {
        var attempt by remember { mutableIntStateOf(0) }
        var result by remember { mutableStateOf<Result<KnobSettings>?>(null) }
        LaunchedEffect(attempt) {
            result = null
            result = try {
                Result.success(bluetooth.knobSettings())
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

            else -> KnobSettingsCards(bluetooth, current.getOrThrow(), scroll) { attempt++ }
        }
    }
}

@Composable
private fun KnobSettingsCards(bluetooth: Bluetooth, read: KnobSettings, scroll: PageScroll?, onRead: () -> Unit) {
    val scope = rememberCoroutineScope()
    // What the Knob holds as far as the app knows, and the write under way.
    var settings by remember(read) { mutableStateOf(read) }
    var writing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Message?>(null) }
    val write = { changed: KnobSettings ->
        if (!writing && changed != settings) {
            val before = settings
            settings = changed
            writing = true
            failure = null
            scope.launch {
                try {
                    bluetooth.writeKnobSettings(changed)
                } catch (e: BluetoothFailed) {
                    settings = before
                    failure = e.shown
                } finally {
                    writing = false
                }
            }
        }
    }
    CardColumn(scroll) {
        Text(stringResource(Res.string.knob_settings_hint), style = MaterialTheme.typography.bodyMedium)
        if (writing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        failure?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }
        PageCard(Res.drawable.palette, stringResource(Res.string.knob_settings_theme)) {
            Chips(KnobSettings.THEMES.map { stringResource(it) }, settings.theme, !writing) {
                write(settings.copy(theme = it))
            }
        }
        PageCard(Res.drawable.light_mode, stringResource(Res.string.knob_settings_brightness)) {
            StepSlider(settings.brightness, KnobSettings.BRIGHTNESS, !writing, { "${it * 10} %" }) {
                write(settings.copy(brightness = it))
            }
        }
        PageCard(Res.drawable.vibration, stringResource(Res.string.knob_settings_clicks)) {
            val off = stringResource(Res.string.knob_settings_clicks_off)
            StepSlider(settings.haptics, KnobSettings.HAPTICS, !writing, { if (it == 0) off else "$it" }) {
                write(settings.copy(haptics = it))
            }
        }
        PageCard(Res.drawable.screen_rotation, stringResource(Res.string.knob_settings_orientation)) {
            Chips((0 until KnobSettings.ORIENTATIONS).map { "${it * 90}°" }, settings.orientation, !writing) {
                write(settings.copy(orientation = it))
            }
        }
        ActionButton(stringResource(Res.string.status_read_again), onClick = onRead, enabled = !writing)
    }
}

/** One chip per label, the one at [selected] marked; [onSelect] hears the index tapped. */
@Composable
private fun Chips(labels: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(label) },
                enabled = enabled,
            )
        }
    }
}

/**
 * A slider over the whole steps of [range] with the step shown by [label]; [onChange] hears the step
 * once the finger lifts, so dragging writes once.
 */
@Composable
private fun StepSlider(step: Int, range: IntRange, enabled: Boolean, label: (Int) -> String, onChange: (Int) -> Unit) {
    var position by remember(step) { mutableFloatStateOf(step.toFloat()) }
    Column {
        Text(label(position.roundToInt()), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onChange(position.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            enabled = enabled,
        )
    }
}
