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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
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
        upload(Upload.PLUGIN, byteArrayOf(PluginService.BEGIN) + header, module, mtu, progress)
    }

    override suspend fun sendFirmware(image: ByteArray, signature: ByteArray, progress: (sent: Int) -> Unit) = session {
        val mtu = requestMtu(MTU)
        // Shorter connection intervals carry more pieces per second; the Knob keeps up.
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        upload(Upload.FIRMWARE, FirmwareService.update(image.size, signature), image, mtu, progress)
    }

    /**
     * Starts an upload of [kind] with the control write [begin], sends [payload] in pieces that fit [mtu] and
     * commits it; [progress] hears the bytes sent. The Knob takes it only over an encrypted link.
     */
    private suspend fun Session.upload(
        kind: Upload,
        begin: ByteArray,
        payload: ByteArray,
        mtu: Int,
        progress: (sent: Int) -> Unit,
    ) {
        val service = service(PluginService.SERVICE) ?: throw BluetoothFailed(kind.noService)
        val control = service.characteristic(PluginService.CONTROL)
        val data = service.characteristic(PluginService.DATA)
        val status = service.characteristic(PluginService.STATUS)
        if (control == null || data == null || status == null) throw BluetoothFailed(kind.noService)
        subscribe(status)
        bond()
        when (writeStatus(control, begin)) {
            BluetoothGatt.GATT_SUCCESS -> Unit

            // A firmware whose control characteristic is too short for the command.
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> throw BluetoothFailed(kind.tooOld)

            else -> throw BluetoothFailed(Res.string.bluetooth_error_refused_begin)
        }
        awaitStatus(PluginService.READY, kind.failures)
        try {
            // A write carries three bytes of ATT besides the offset and the piece.
            val piece = (mtu - 3 - PluginService.OFFSET).coerceIn(PIECE_MIN, PluginService.PIECE_MAX)
            var offset = 0
            var count = 0
            while (offset < payload.size) {
                val end = minOf(offset + piece, payload.size)
                // Only every window-th piece and the last wait for the Knob's reply; it answers in order, so that
                // reply stands for the pieces before it.
                val type = if (++count % kind.window == 0 || end == payload.size) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                if (!write(data, PluginService.pieceAt(payload, offset, piece), type)) {
                    throw BluetoothFailed(kind.refusedPiece)
                }
                checkStatuses(kind.failures)
                offset = end
                progress(offset)
            }
            if (!write(control, byteArrayOf(PluginService.COMMIT))) throw BluetoothFailed(kind.refused)
            awaitStatus(PluginService.WRITTEN, kind.failures)
        } catch (e: BluetoothFailed) {
            withTimeoutOrNull(ABORT_TIMEOUT_MS) { runCatching { write(control, byteArrayOf(PluginService.ABORT)) } }
            throw e
        }
    }

    /** What sets a plugin upload and a firmware update apart. */
    private enum class Upload(
        val window: Int,
        val failures: (Int) -> StringResource?,
        val noService: StringResource,
        val tooOld: StringResource,
        val refusedPiece: StringResource,
        val refused: StringResource,
    ) {
        PLUGIN(
            window = 1,
            failures = PluginService::failure,
            noService = Res.string.bluetooth_error_no_plugins,
            tooOld = Res.string.bluetooth_error_refused_begin,
            refusedPiece = Res.string.bluetooth_error_refused_piece,
            refused = Res.string.bluetooth_error_refused_plugin,
        ),
        FIRMWARE(
            window = FirmwareService.WINDOW,
            failures = FirmwareService::failure,
            noService = Res.string.bluetooth_error_no_firmware,
            tooOld = Res.string.bluetooth_error_no_firmware,
            refusedPiece = Res.string.bluetooth_error_refused_firmware_piece,
            refused = Res.string.bluetooth_error_refused_firmware,
        ),
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
        bond()
        when (writeStatus(control, byteArrayOf(PluginService.DELETE, slot.toByte()))) {
            BluetoothGatt.GATT_SUCCESS -> awaitStatus(PluginService.DELETED)

            // The Knob takes commands only while its Receive is open.
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> throw BluetoothFailed(Res.string.plugins_error_delete_refused)

            else -> throw BluetoothFailed(Res.string.bluetooth_error_refused_delete)
        }
    }

    /** Finds the Knob, connects, runs [block] and disconnects. */
    override suspend fun knobSettings(): KnobSettings = session(SETTINGS) {
        val characteristic = service(KnobService.SERVICE)?.characteristic(KnobService.SETTINGS)
            ?: throw BluetoothFailed(Res.string.bluetooth_error_no_settings)
        KnobSettings.decode(read(characteristic))
            ?: throw BluetoothFailed(Res.string.bluetooth_error_settings_format)
    }

    override suspend fun writeKnobSettings(settings: KnobSettings) = session(SETTINGS) {
        val characteristic = service(KnobService.SERVICE)?.characteristic(KnobService.SETTINGS)
            ?: throw BluetoothFailed(Res.string.bluetooth_error_no_settings)
        // Only a paired phone may change them.
        bond()
        if (!write(characteristic, settings.encode())) {
            throw BluetoothFailed(Res.string.bluetooth_error_refused_settings)
        }
    }

    /**
     * Connects, runs [block] and disconnects. With [needs], a service and characteristic UUID, the services are
     * discovered a second time when the first lacks it: for a paired Knob, Android keeps the services from an
     * earlier connection, which miss what a newer firmware added.
     */
    private suspend fun <T> session(needs: Pair<String, String>? = null, block: suspend Session.() -> T): T {
        val adapter = adapter ?: throw BluetoothFailed(Res.string.bluetooth_error_no_adapter)
        if (!adapter.isEnabled) throw BluetoothFailed(Res.string.bluetooth_error_off)
        val device = try {
            withTimeout(SCAN_TIMEOUT_MS) { find(adapter.bluetoothLeScanner) }
        } catch (_: TimeoutCancellationException) {
            throw BluetoothFailed(Res.string.bluetooth_error_out_of_range)
        }
        val session = connect(device)
        val gatt = session.gatt
        return try {
            gatt.discoverServices()
            if (!session.expect<Event.Discovered>().ok) throw BluetoothFailed(Res.string.bluetooth_error_services)
            if (needs != null && !gatt.has(needs) && gatt.rediscover()) {
                session.expect<Event.Discovered>()
            }
            session.block()
        } finally {
            gatt.disconnect()
            gatt.close()
        }
    }

    /**
     * Connects to [device]. Android fails an attempt now and then right away (status 133), so a
     * connection that does not come about is tried again before giving up.
     */
    private suspend fun connect(device: BluetoothDevice): Session {
        repeat(CONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(CONNECT_RETRY_MS)
            val session = Session(device)
            val gatt = device.connectGatt(context, false, session.callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw BluetoothFailed(Res.string.bluetooth_error_connect)
            session.gatt = gatt
            val event = try {
                withTimeoutOrNull(STEP_TIMEOUT_MS) { session.events.receive() }
            } catch (cancelled: CancellationException) {
                gatt.close()
                throw cancelled
            }
            if (event == Event.Connected) return session
            gatt.close()
            if (event == null) throw BluetoothFailed(Res.string.bluetooth_error_connect)
        }
        throw BluetoothFailed(Res.string.bluetooth_error_connect)
    }

    /**
     * Pairs with the Knob unless the phone already has. The Knob takes plugins and deletions only
     * over an encrypted link, which needs the bond; the phone asks the user once.
     */
    private suspend fun Session.bond() {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        val bonded = try {
            withTimeout(BOND_TIMEOUT_MS) { awaitBond(context, device) }
        } catch (_: TimeoutCancellationException) {
            false
        }
        if (!bonded) throw BluetoothFailed(Res.string.bluetooth_error_not_paired)
    }

    /** One connection: the GATT callbacks arrive as [events], the plugin status notifications as [statuses]. */
    private class Session(val device: BluetoothDevice) {
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

        /**
         * Writes [value] and waits for the Knob to take it; false when it refuses. A write of [type]
         * `WRITE_TYPE_NO_RESPONSE` is taken once the phone has queued it.
         */
        suspend fun write(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            type: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        ): Boolean = writeStatus(characteristic, value, type) == BluetoothGatt.GATT_SUCCESS

        /** Writes [value] and waits for the Knob's answer, a `BluetoothGatt` status. */
        suspend fun writeStatus(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            type: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        ): Int {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, value, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.run {
                    writeType = type
                    this.value = value
                    gatt.writeCharacteristic(this)
                }
            }
            if (!started) throw BluetoothFailed(Res.string.bluetooth_error_write)
            return when (val status = expect<Event.Written>().status) {
                // Bonded on the phone, but the Knob has since bonded with another device or forgotten this one.
                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION ->
                    throw BluetoothFailed(Res.string.bluetooth_error_bond_lost)

                else -> status
            }
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

        /** Waits for the upload status [code]; a failure on the way, named by [failures], ends the upload. */
        suspend fun awaitStatus(code: Int, failures: (Int) -> StringResource? = PluginService::failure) {
            while (true) {
                val status = withTimeoutOrNull(STEP_TIMEOUT_MS) { statuses.receive() }
                    ?: throw BluetoothFailed(Res.string.bluetooth_error_silent)
                failOn(status, failures)
                if (status.firstOrNull()?.toInt() == code) return
            }
        }

        /** Ends the upload if the Knob has reported a failure meanwhile. */
        fun checkStatuses(failures: (Int) -> StringResource?) {
            while (true) failOn(statuses.tryReceive().getOrNull() ?: return, failures)
        }

        private fun failOn(status: ByteArray, failures: (Int) -> StringResource?) {
            val code = status.firstOrNull()?.toInt()?.and(0xff) ?: return
            failures(code)?.let { throw BluetoothFailed(it) }
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
        /** The Knob's settings characteristic, as its service and its own UUID. */
        val SETTINGS = KnobService.SERVICE to KnobService.SETTINGS
        const val SCAN_TIMEOUT_MS = 10_000L
        const val STEP_TIMEOUT_MS = 15_000L
        const val CONNECT_ATTEMPTS = 3
        const val CONNECT_RETRY_MS = 500L
        const val ABORT_TIMEOUT_MS = 2_000L

        /** Long enough for the user to find and accept the phone's pairing request. */
        const val BOND_TIMEOUT_MS = 60_000L

        /** The ATT MTU the Knob's packet pool allows. */
        const val MTU = 247
        const val DEFAULT_MTU = 23
        const val PIECE_MIN = 16

        /** The Client Characteristic Configuration descriptor. */
        const val NOTIFICATIONS = "00002902-0000-1000-8000-00805f9b34fb"
    }
}

/** Starts pairing with [device] and waits until it is bonded (true) or pairing failed (false). */
@SuppressLint("MissingPermission")
private suspend fun awaitBond(context: Context, device: BluetoothDevice): Boolean =
    suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Asked of the device rather than read from the extras, which any app could send.
                when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> finish(true)
                    BluetoothDevice.BOND_NONE -> finish(false)
                }
            }

            fun finish(bonded: Boolean) {
                runCatching { context.unregisterReceiver(this) }
                if (continuation.isActive) continuation.resume(bonded)
            }
        }
        // A protected system broadcast, which needs no export flag.
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        if (!device.createBond() && device.bondState != BluetoothDevice.BOND_BONDING) {
            receiver.finish(device.bondState == BluetoothDevice.BOND_BONDED)
        }
    }

/** The first Knob that [scanner] sees. */
@SuppressLint("MissingPermission")
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

private fun BluetoothGatt.has(needs: Pair<String, String>): Boolean =
    getService(UUID.fromString(needs.first))?.getCharacteristic(UUID.fromString(needs.second)) != null

/** Drops Android's cached services of this device and starts discovering them; false when that cannot be done. */
// No public API clears the cache; BLE libraries call it the same way. Permissions as for the class.
@SuppressLint("DiscouragedPrivateApi", "MissingPermission")
private fun BluetoothGatt.rediscover(): Boolean =
    runCatching { javaClass.getMethod("refresh").invoke(this) as Boolean }.getOrDefault(false) && discoverServices()
