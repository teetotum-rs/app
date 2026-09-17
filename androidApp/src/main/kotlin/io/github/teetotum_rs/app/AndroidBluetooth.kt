package io.github.teetotum_rs.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Finds the Knob by its name or its service, connects, reads and disconnects. The permissions are
 * asked for by [BluetoothAccess] before the status page calls in.
 */
@SuppressLint("MissingPermission")
class AndroidBluetooth(private val context: Context) : Bluetooth {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override suspend fun status(): KnobStatus {
        val adapter = adapter ?: throw BluetoothFailed("This phone has no Bluetooth.")
        if (!adapter.isEnabled) throw BluetoothFailed("Bluetooth is off. Turn it on and try again.")
        val device = try {
            withTimeout(SCAN_TIMEOUT_MS) { find(adapter.bluetoothLeScanner) }
        } catch (_: TimeoutCancellationException) {
            throw BluetoothFailed("No Knob in range.")
        }
        return try {
            withTimeout(READ_TIMEOUT_MS) { read(device) }
        } catch (_: TimeoutCancellationException) {
            throw BluetoothFailed("The Knob did not answer.")
        }
    }

    private suspend fun find(scanner: android.bluetooth.le.BluetoothLeScanner?): BluetoothDevice =
        suspendCancellableCoroutine { continuation ->
            if (scanner == null) {
                continuation.resumeWithException(BluetoothFailed("Bluetooth is off. Turn it on and try again."))
                return@suspendCancellableCoroutine
            }
            val found = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    runCatching { scanner.stopScan(this) }
                    if (continuation.isActive) continuation.resume(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(BluetoothFailed("Could not search for the Knob ($errorCode)."))
                    }
                }
            }
            // The name travels in the advertisement, the service in the scan response; either finds it.
            val filters = listOf(
                ScanFilter.Builder().setDeviceName(KnobService.NAME).build(),
                ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(KnobService.SERVICE)).build(),
            )
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(filters, settings, found)
            continuation.invokeOnCancellation { runCatching { scanner.stopScan(found) } }
        }

    private suspend fun read(device: BluetoothDevice): KnobStatus {
        val events = Channel<Event>(Channel.UNLIMITED)
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(if (newState == BluetoothProfile.STATE_CONNECTED) Event.Connected else Event.Disconnected)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(Event.Discovered(status == BluetoothGatt.GATT_SUCCESS))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                events.trySend(Event.Read(value.takeIf { status == BluetoothGatt.GATT_SUCCESS }))
            }

            // Android 12 and older call this one instead.
            @Deprecated("Replaced by the overload with the value from Android 13")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                @Suppress("DEPRECATION")
                events.trySend(Event.Read(characteristic.value.takeIf { status == BluetoothGatt.GATT_SUCCESS }))
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw BluetoothFailed("Could not connect to the Knob.")
        try {
            events.expect<Event.Connected>()
            gatt.discoverServices()
            if (!events.expect<Event.Discovered>().ok) throw BluetoothFailed("The Knob did not list its services.")
            val service = gatt.getService(UUID.fromString(KnobService.SERVICE))
                ?: throw BluetoothFailed("This Knob does not report its status.")

            /** The characteristic's bytes, or null when this firmware does not have it. */
            suspend fun bytes(uuid: String): ByteArray? {
                val characteristic = service.getCharacteristic(UUID.fromString(uuid)) ?: return null
                if (!gatt.readCharacteristic(characteristic)) throw BluetoothFailed("Could not read from the Knob.")
                return events.expect<Event.Read>().value ?: throw BluetoothFailed("Could not read from the Knob.")
            }

            suspend fun number(uuid: String): Long =
                unsignedOf(bytes(uuid) ?: throw BluetoothFailed("This Knob does not report its status."))

            return KnobStatus(
                uptimeSeconds = number(KnobService.UPTIME),
                networks = number(KnobService.NETWORKS).toInt(),
                version = bytes(KnobService.VERSION)?.decodeToString(),
                cardBytes = bytes(KnobService.CARD_BYTES)?.let(::unsignedOf),
            )
        } finally {
            gatt.disconnect()
            gatt.close()
        }
    }

    /** The next event, which has to be a [T]; a disconnect on the way ends the read. */
    private suspend inline fun <reified T : Event> Channel<Event>.expect(): T =
        when (val event = receive()) {
            is T -> event
            Event.Disconnected -> throw BluetoothFailed("The Knob ended the connection.")
            else -> throw BluetoothFailed("The Knob answered out of turn.")
        }

    private sealed interface Event {
        data object Connected : Event
        data object Disconnected : Event
        data class Discovered(val ok: Boolean) : Event
        class Read(val value: ByteArray?) : Event
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 15_000L
    }
}
