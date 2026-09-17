package io.github.teetotum_rs.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Finds the Knob by its name or its service, connects, talks and disconnects. The permissions are
 * asked for by [BluetoothAccess] before a page calls in.
 */
@SuppressLint("MissingPermission")
class AndroidBluetooth(private val context: Context) : Bluetooth {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override suspend fun status(): KnobStatus = session {
        val service = service(KnobService.SERVICE) ?: throw BluetoothFailed(Res.string.bluetooth_error_no_status)

        /** The characteristic's bytes, or null when this firmware does not have it. */
        suspend fun bytes(uuid: String): ByteArray? = service.characteristic(uuid)?.let { read(it) }

        suspend fun number(uuid: String): Long =
            unsignedOf(bytes(uuid) ?: throw BluetoothFailed(Res.string.bluetooth_error_no_status))

        KnobStatus(
            uptimeSeconds = number(KnobService.UPTIME),
            networks = number(KnobService.NETWORKS).toInt(),
            version = bytes(KnobService.VERSION)?.decodeToString(),
            cardBytes = bytes(KnobService.CARD_BYTES)?.let(::unsignedOf),
        )
    }

    override suspend fun sendPlugin(module: ByteArray, header: ByteArray, progress: (sent: Int) -> Unit) = session {
        val mtu = requestMtu(MTU)
        val service = service(PluginService.SERVICE)
            ?: throw BluetoothFailed(Res.string.bluetooth_error_no_plugins)
        val control = service.characteristic(PluginService.CONTROL)
        val data = service.characteristic(PluginService.DATA)
        val status = service.characteristic(PluginService.STATUS)
        if (control == null || data == null || status == null) {
            throw BluetoothFailed(Res.string.bluetooth_error_no_plugins)
        }
        subscribe(status)
        if (!write(control, byteArrayOf(PluginService.BEGIN) + header)) {
            throw BluetoothFailed(Res.string.bluetooth_error_refused_begin)
        }
        awaitStatus(PluginService.READY)
        try {
            // A write carries three bytes of ATT besides the offset and the piece.
            val piece = (mtu - 3 - PluginService.OFFSET).coerceIn(PIECE_MIN, PluginService.PIECE_MAX)
            var offset = 0
            while (offset < module.size) {
                if (!write(data, PluginService.pieceAt(module, offset, piece))) {
                    throw BluetoothFailed(Res.string.bluetooth_error_refused_piece)
                }
                checkStatuses()
                offset = minOf(offset + piece, module.size)
                progress(offset)
            }
            if (!write(control, byteArrayOf(PluginService.COMMIT))) {
                throw BluetoothFailed(Res.string.bluetooth_error_refused_plugin)
            }
            awaitStatus(PluginService.WRITTEN)
        } catch (e: BluetoothFailed) {
            withTimeoutOrNull(ABORT_TIMEOUT_MS) { runCatching { write(control, byteArrayOf(PluginService.ABORT)) } }
            throw e
        }
    }

    override suspend fun plugins(): List<KnobPlugin> = session {
        requestMtu(MTU)
        val service = service(PluginService.SERVICE)
            ?: throw BluetoothFailed(Res.string.bluetooth_error_no_plugin_list)
        val select = service.characteristic(PluginService.SELECT)
        val entry = service.characteristic(PluginService.ENTRY)
        if (select == null || entry == null) throw BluetoothFailed(Res.string.bluetooth_error_no_plugin_list)
        buildList {
            var index = 0
            do {
                if (!write(select, byteArrayOf(index.toByte()))) throw BluetoothFailed(Res.string.bluetooth_error_write)
                val read = PluginService.entryOf(read(entry))
                if (read.index != index) throw BluetoothFailed(Res.string.bluetooth_error_plugin_entry)
                read.plugin?.let(::add)
                index++
            } while (index < read.count)
        }
    }

    override suspend fun deletePlugin(slot: Int) = session {
        val service = service(PluginService.SERVICE)
            ?: throw BluetoothFailed(Res.string.bluetooth_error_no_plugins)
        val control = service.characteristic(PluginService.CONTROL)
        val status = service.characteristic(PluginService.STATUS)
        if (control == null || status == null) throw BluetoothFailed(Res.string.bluetooth_error_no_plugins)
        subscribe(status)
        when (writeStatus(control, byteArrayOf(PluginService.DELETE, slot.toByte()))) {
            BluetoothGatt.GATT_SUCCESS -> awaitStatus(PluginService.DELETED)

            // The Knob takes commands only while its Receive is open.
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> throw BluetoothFailed(Res.string.plugins_error_delete_refused)

            else -> throw BluetoothFailed(Res.string.bluetooth_error_refused_delete)
        }
    }

    /** Finds the Knob, connects, runs [block] and disconnects. */
    private suspend fun <T> session(block: suspend Session.() -> T): T {
        val adapter = adapter ?: throw BluetoothFailed(Res.string.bluetooth_error_no_adapter)
        if (!adapter.isEnabled) throw BluetoothFailed(Res.string.bluetooth_error_off)
        val device = try {
            withTimeout(SCAN_TIMEOUT_MS) { find(adapter.bluetoothLeScanner) }
        } catch (_: TimeoutCancellationException) {
            throw BluetoothFailed(Res.string.bluetooth_error_out_of_range)
        }
        val session = Session()
        val gatt = device.connectGatt(context, false, session.callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw BluetoothFailed(Res.string.bluetooth_error_connect)
        session.gatt = gatt
        return try {
            session.expect<Event.Connected>()
            gatt.discoverServices()
            if (!session.expect<Event.Discovered>().ok) throw BluetoothFailed(Res.string.bluetooth_error_services)
            session.block()
        } finally {
            gatt.disconnect()
            gatt.close()
        }
    }

    private suspend fun find(scanner: android.bluetooth.le.BluetoothLeScanner?): BluetoothDevice =
        suspendCancellableCoroutine { continuation ->
            if (scanner == null) {
                continuation.resumeWithException(BluetoothFailed(Res.string.bluetooth_error_off))
                return@suspendCancellableCoroutine
            }
            val found = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    runCatching { scanner.stopScan(this) }
                    if (continuation.isActive) continuation.resume(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(BluetoothFailed(Res.string.bluetooth_error_search, errorCode))
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

    /** One connection: the GATT callbacks arrive as [events], the plugin status notifications as [statuses]. */
    private class Session {
        lateinit var gatt: BluetoothGatt
        val events = Channel<Event>(Channel.UNLIMITED)
        val statuses = Channel<ByteArray>(Channel.UNLIMITED)

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(
                    if (newState == BluetoothProfile.STATE_CONNECTED) Event.Connected else Event.Disconnected,
                )
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(Event.Discovered(status == BluetoothGatt.GATT_SUCCESS))
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                events.trySend(Event.Mtu(if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU))
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

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                events.trySend(Event.Written(status))
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                events.trySend(Event.Written(status))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                statuses.trySend(value)
            }

            // Android 12 and older call this one instead.
            @Deprecated("Replaced by the overload with the value from Android 13")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                statuses.trySend(characteristic.value.copyOf())
            }
        }

        /** The next event, which has to be a [T]; a disconnect on the way ends the session. */
        suspend inline fun <reified T : Event> expect(): T {
            val event = try {
                withTimeout(STEP_TIMEOUT_MS) { events.receive() }
            } catch (_: TimeoutCancellationException) {
                throw BluetoothFailed(Res.string.bluetooth_error_silent)
            }
            return when (event) {
                is T -> event
                Event.Disconnected -> throw BluetoothFailed(Res.string.bluetooth_error_disconnected)
                else -> throw BluetoothFailed(Res.string.bluetooth_error_out_of_turn)
            }
        }

        fun service(uuid: String): BluetoothGattService? = gatt.getService(UUID.fromString(uuid))

        fun BluetoothGattService.characteristic(uuid: String): BluetoothGattCharacteristic? =
            getCharacteristic(UUID.fromString(uuid))

        suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray {
            if (!gatt.readCharacteristic(characteristic)) throw BluetoothFailed(Res.string.bluetooth_error_read)
            return expect<Event.Read>().value ?: throw BluetoothFailed(Res.string.bluetooth_error_read)
        }

        /** Writes [value] and waits for the Knob to take it; false when it refuses. */
        suspend fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean =
            writeStatus(characteristic, value) == BluetoothGatt.GATT_SUCCESS

        /** Writes [value] and waits for the Knob's answer, a `BluetoothGatt` status. */
        suspend fun writeStatus(characteristic: BluetoothGattCharacteristic, value: ByteArray): Int {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.run {
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    this.value = value
                    gatt.writeCharacteristic(this)
                }
            }
            if (!started) throw BluetoothFailed(Res.string.bluetooth_error_write)
            return expect<Event.Written>().status
        }

        /** The MTU the Knob agrees to, at most [mtu]. */
        suspend fun requestMtu(mtu: Int): Int = if (gatt.requestMtu(mtu)) expect<Event.Mtu>().mtu else DEFAULT_MTU

        /** Turns on the notifications of [characteristic]. */
        suspend fun subscribe(characteristic: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFICATIONS))
                ?: throw BluetoothFailed(Res.string.bluetooth_error_no_progress)
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.run {
                    this.value = value
                    gatt.writeDescriptor(this)
                }
            }
            if (!started || !expect<Event.Written>().ok) {
                throw BluetoothFailed(Res.string.bluetooth_error_no_progress)
            }
        }

        /** Waits for the plugin status [code]; a failure on the way ends the upload. */
        suspend fun awaitStatus(code: Int) {
            while (true) {
                val status = withTimeoutOrNull(STEP_TIMEOUT_MS) { statuses.receive() }
                    ?: throw BluetoothFailed(Res.string.bluetooth_error_silent)
                failOn(status)
                if (status.firstOrNull()?.toInt() == code) return
            }
        }

        /** Ends the upload if the Knob has reported a failure meanwhile. */
        fun checkStatuses() {
            while (true) failOn(statuses.tryReceive().getOrNull() ?: return)
        }

        private fun failOn(status: ByteArray) {
            val code = status.firstOrNull()?.toInt()?.and(0xff) ?: return
            PluginService.failure(code)?.let { throw BluetoothFailed(it) }
        }
    }

    private sealed interface Event {
        data object Connected : Event
        data object Disconnected : Event
        data class Discovered(val ok: Boolean) : Event
        data class Mtu(val mtu: Int) : Event
        data class Written(val status: Int) : Event {
            val ok get() = status == BluetoothGatt.GATT_SUCCESS
        }
        class Read(val value: ByteArray?) : Event
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
        const val STEP_TIMEOUT_MS = 15_000L
        const val ABORT_TIMEOUT_MS = 2_000L

        /** The ATT MTU the Knob's packet pool allows. */
        const val MTU = 247
        const val DEFAULT_MTU = 23
        const val PIECE_MIN = 16

        /** The Client Characteristic Configuration descriptor. */
        const val NOTIFICATIONS = "00002902-0000-1000-8000-00805f9b34fb"
    }
}
