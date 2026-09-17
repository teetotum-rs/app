package io.github.teetotum_rs.app

/** A Knob in memory: [plugins] as it lists them, [receiving] as if its Settings > Receive were open. */
class FakeBluetooth(var plugins: List<KnobPlugin> = emptyList(), var receiving: Boolean = true) : Bluetooth {
    val deleted = mutableListOf<Int>()

    override suspend fun status() = KnobStatus(uptimeSeconds = 42, networks = 3, version = "v0.4.0", cardBytes = 0)

    override suspend fun sendPlugin(module: ByteArray, header: ByteArray, progress: (sent: Int) -> Unit) {
        if (!receiving) throw BluetoothFailed(Res.string.bluetooth_error_refused_begin)
        progress(module.size)
    }

    override suspend fun plugins(): List<KnobPlugin> = plugins

    override suspend fun deletePlugin(slot: Int) {
        if (!receiving) throw BluetoothFailed(Res.string.plugins_error_delete_refused)
        if (plugins.none { it.slot == slot && it.deletable }) {
            throw BluetoothFailed(PluginService.failure(0x86) ?: Res.string.bluetooth_error_refused_delete)
        }
        deleted += slot
        plugins = plugins.filter { it.slot != slot }
    }
}
