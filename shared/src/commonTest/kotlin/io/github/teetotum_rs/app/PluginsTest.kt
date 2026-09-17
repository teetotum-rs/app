package io.github.teetotum_rs.app

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginsTest {
    /** The HID remote from the Knob's firmware 0.1.0, as the catalogue lists it; its header is teetotum-pack's. */
    private val remote = Base64.decode(
        "AGFzbQEAAAABLwdgB39/f39/f38AYAR/f39/AGABfwBgBn9/f39/fwBgAABgBX9/f39/AGABfwF/AlUFA2VudgZtZW1vcnkCAQEB" +
            "CHRlZXRvdHVtA2FyYwAACHRlZXRvdHVtBGljb24AAQh0ZWV0b3R1bQpzZW5kX3VzYWdlAAIIdGVldG90dW0EdGV4dAADAwQDBAUG" +
            "BgcBfwFBgCALBxMCBGRyYXcABAhvbl9ldmVudAAGCt4DA80CAQN/QcuhgIAAIQBBCiEBAkBBACgCiKOAgAAiAkEvRg0AAkACQAJA" +
            "AkACQCACQc1+ag4EAQIDBAALIAJBzQFHDQRBwaGAgAAhAAwEC0GjoYCAACEAQQwhAQwDC0GvoYCAACEAQQYhAQwCC0G1oYCAACEA" +
            "QQQhAQwBC0G5oYCAACEAQQghAQtBtAFBtAFBqgFBhwFBjgJBCEGAgAQQgICAgABB2KGAgABBtAFB9gBBg4AEEIGAgIAAIAAgAUGq" +
            "AUECQYSABBCFgICAAEG4ooCAAEEUQdQBQQBBhoAEEIWAgIAAQcyigIAAQQ1B5gFBAEGGgAQQhYCAgABB2aKAgABBDEH4AUEAQYaA" +
            "BBCFgICAAAJAQQAtAIyjgIAADQBB5aKAgABBE0GKAkEAQYGABBCFgICAAA8LQfiigIAAQQ9BigJBAEGFgAQQhYCAgAALFQAgACAB" +
            "QbQBIAIgAyAEEIOAgIAAC3cBAn9BzQEhAUEAIQICQAJAAkACQAJAAkACQAJAIAAOCgUEAAECBwcHAwMHC0G1ASEBDAQLQbMBIQEM" +
            "AwtBtAEhAQwCC0EAIABBCEY6AIyjgIAADAILQbYBIQELIAEQgoCAgABBACABNgKIo4CAAAtBASECCyACCwugAwMAQYAgC4cDAwMA" +
            "AAAKSElEIHJlbW90ZQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJwDEACcAxgAnAMeAJwDHwCcwx8AnOMfAJzzHwCc8x8A" +
            "nOMfAJzDHwCcAx8AnAMeAJwDGACcAxAAAAAAAAAAAAAAAAAAAAAAAAAAAAAdcmVtb3RlIGZvciB0aGUgcGhvbmUncyBwbGF5ZXIA" +
            "AAABAAAAAAAAAEZhc3QgZm9yd2FyZFJld2luZE5leHRQcmV2aW91c1BsYXkvUGF1c2VISUQgcmVtb3RlAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAACcAxAAnAMYAJwDHgCcAx8AnMMfAJzjHwCc8x8AnPMfAJzjHwCcwx8AnAMfAJwDHgCcAxgAnAMQAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAdGFwIHRvIHBsYXkgb3IgcGF1c2Vzd2lwZSB0byBza2lwdHVybiB0byBzZWVrcGFpciBUQUlKSV9LTk9CX0hJRHBo" +
            "b25lIGNvbm5lY3RlZABBiCMLBC8AAAAAQYwjCwEAALUBEXRlZXRvdHVtLm1hbmlmZXN0AwMAAAAKSElEIHJlbW90ZQAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAJwDEACcAxgAnAMeAJwDHwCcwx8AnOMfAJzzHwCc8x8AnOMfAJzDHwCcAx8AnAMeAJwDGACc" +
            "AxAAAAAAAAAAAAAAAAAAAAAAAAAAAAAdcmVtb3RlIGZvciB0aGUgcGhvbmUncyBwbGF5ZXIAAAABAAAAAAAAAABzEnRlZXRvdHVt" +
            "LnNpZ25hdHVyZfasqF7cNUfU4uNZFo9bodFdPF6IbSsqRDAWl+igq6q6hibr57IbLMCf8iQELK6h1qOUe528f71IxadoQ1tZPmQn" +
            "3ixDoBzTkbyUHATJm9hl+37vjiOOukLF7lkpc0K6Aw==",
    )

    private val entry = CataloguePlugin(
        name = "HID remote",
        summary = "remote for the phone's player",
        version = "0.0.0",
        rights = listOf("HID", "KNOB"),
        id = "41a9ad2d2290788d",
        key = "f6aca85edc3547d4e2e359168f5ba1d15d3c5e886d2b2a44301697e8a0abaaba",
        size = 1381,
        sha256 = "ce9885d8ad7125815d665bc50088b2f461a57cd3a6b40129472417f7e6e66c0c",
        url = "https://example.invalid/hid-remote.wasm",
    )

    @Test
    fun describesThePluginAsTheCatalogueDoes() {
        val about = describe(remote)
        assertEquals("HID remote", about.name)
        assertEquals("remote for the phone's player", about.summary)
        assertEquals(listOf("HID", "KNOB"), about.rights)
        assertEquals("0.0.0", about.version)
        assertEquals(entry.id, about.id)
        assertEquals(entry.key, about.key)
        assertEquals(entry.id, checkDownload(entry, remote).id)
    }

    @Test
    fun refusesWhatTheKnobWouldNot() {
        assertFailsWith<PluginInvalid> { describe(byteArrayOf(1, 2, 3)) }
        assertFailsWith<PluginInvalid> { describe(remote.copyOf(remote.size - 1)) }
        assertFailsWith<PluginInvalid> { describe(remote.copyOf(8)) }
        val changed = remote.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertFailsWith<PluginInvalid> { checkDownload(entry, changed) }
        assertFailsWith<PluginInvalid> { checkDownload(entry.copy(id = "0000000000000000"), remote) }
    }

    @Test
    fun writesTheHeaderAsTeetotumPackDoes() {
        val header = slotHeader(remote, describe(remote))
        assertEquals(PluginService.HEADER, header.size)
        assertEquals(
            "5454505301ff000041a9ad2d2290788d65050000dfd160a3b0bffc72363be226f04d09ed" +
                "0ecb595a491d6741cc84fa2d481a357f000000000000000000000000",
            hexOf(header),
        )
    }

    @Test
    fun putsTheOffsetBeforeEachPiece() {
        val module = ByteArray(300) { it.toByte() }
        val first = PluginService.pieceAt(module, 0)
        assertEquals(244, first.size)
        val last = PluginService.pieceAt(module, 240)
        assertEquals("f0000000", hexOf(last.copyOf(4)))
        assertEquals(64, last.size)
        assertEquals(240.toByte(), last[4])
    }

    @Test
    fun readsTheCatalogue() {
        val catalogue = catalogueOf(
            """{"format": 1, "plugins": [{"name": "Teetotum", "summary": "a die", "version": "0.0.0",
            "abi": 1, "rights": ["KNOB"], "id": "6799220a4230b177", "key": "f6", "size": 2737,
            "sha256": "c5", "url": "https://x", "source": "https://y", "license": "MIT"}]}""",
        )
        assertEquals(listOf("Teetotum"), catalogue.plugins.map { it.name })
        assertFailsWith<PluginInvalid> { catalogueOf("""{"format": 2, "plugins": []}""") }
        assertFailsWith<PluginInvalid> { catalogueOf("not json") }
    }

    /** An entry as the firmware lays it out, written here byte by byte rather than with the parser's offsets. */
    private fun entryBytes(index: Int, count: Int, flags: Int, slot: Int, name: String, summary: String): ByteArray {
        val bytes = mutableListOf(index, count, flags, slot)
        bytes += listOf(0x41, 0xa9, 0xad, 0x2d, 0x22, 0x90, 0x78, 0x8d)
        bytes += listOf(0x65, 0x05, 0, 0)
        bytes += listOf(1, 0, 2, 0, 0x2c, 0x01)
        bytes += name.length
        bytes += name.encodeToByteArray().map { it.toInt() }.plus(List(20 - name.length) { 0 })
        bytes += summary.length
        bytes += summary.encodeToByteArray().map { it.toInt() }.plus(List(32 - summary.length) { 0 })
        return ByteArray(bytes.size) { bytes[it].toByte() }
    }

    @Test
    fun readsAPluginInASlot() {
        val bytes = entryBytes(1, 3, 0b10, 2, "HID remote", "remote for the phone's player")
        assertEquals(PluginService.ENTRY_SIZE, bytes.size)
        val entry = PluginService.entryOf(bytes)
        assertEquals(1, entry.index)
        assertEquals(3, entry.count)
        assertEquals(
            KnobPlugin(
                name = "HID remote",
                summary = "remote for the phone's player",
                version = "1.2.300",
                id = "41a9ad2d2290788d",
                size = 1381,
                slot = 2,
                bundled = false,
                installed = true,
            ),
            entry.plugin,
        )
        assertTrue(entry.plugin?.deletable == true)
    }

    @Test
    fun aBundledPluginHasNoSlotToDelete() {
        val plugin = PluginService.entryOf(entryBytes(0, 1, 0b01, 0xff, "Teetotum", "")).plugin
        assertEquals("", plugin?.summary)
        assertNull(plugin?.slot)
        assertTrue(plugin?.bundled == true)
        assertFalse(plugin?.installed == true)
        assertFalse(plugin?.deletable == true)
    }

    @Test
    fun anIndexPastTheEndCarriesOnlyTheCount() {
        val entry = PluginService.entryOf(ByteArray(PluginService.ENTRY_SIZE) { if (it < 2) 4 else 0 })
        assertEquals(4, entry.count)
        assertNull(entry.plugin)
        assertEquals(0, PluginService.entryOf(byteArrayOf(0, 0)).count)
    }

    @Test
    fun refusesAnEntryItCannotRead() {
        val good = entryBytes(0, 1, 0, 0, "HID remote", "remote")
        assertFailsWith<BluetoothFailed> { PluginService.entryOf(byteArrayOf()) }
        assertFailsWith<BluetoothFailed> { PluginService.entryOf(good.copyOf(40)) }
        assertFailsWith<BluetoothFailed> { PluginService.entryOf(good.copyOf().also { it[22] = 21 }) }
        assertFailsWith<BluetoothFailed> { PluginService.entryOf(good.copyOf().also { it[22] = 0 }) }
        assertFailsWith<BluetoothFailed> { PluginService.entryOf(good.copyOf().also { it[23] = 0xff.toByte() }) }
    }

    @Test
    fun namesWhyTheKnobRefused() {
        assertEquals(Res.string.plugins_error_delete_refused, PluginService.failure(0x86))
        assertEquals(Res.string.plugins_knob_slots_full, PluginService.failure(0x81))
        assertNull(PluginService.failure(PluginService.DELETED))
    }
}
