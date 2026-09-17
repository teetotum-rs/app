package io.github.teetotum_rs.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The Knob's GATT service that takes a plugin while its Settings > Receive is open. */
object PluginService {
    const val SERVICE = "4a729af2-063c-451a-8c73-60e5fab61ccb"
    const val CONTROL = "19792d5c-9458-40ba-b233-c82b87d3dd4e"
    const val DATA = "81bcd10c-d2eb-4f6a-b4db-e196026f9f7c"
    const val STATUS = "1236b81e-8a6e-49bf-817a-210b74ac7990"

    /** Control: the slot header follows. */
    const val BEGIN: Byte = 1

    /** Control: the module is complete. */
    const val COMMIT: Byte = 2

    /** Control: the upload is dropped. */
    const val ABORT: Byte = 3

    /** Status: a slot is erased and takes pieces. */
    const val READY = 1

    /** Status: the module is written, and the Knob restarts. */
    const val WRITTEN = 2

    /** Bytes in front of each piece: its offset in the module, little-endian. */
    const val OFFSET = 4

    /** The most module bytes in one piece, for an ATT MTU of 247. */
    const val PIECE_MAX = 240

    /** A slot's bytes, header included. */
    const val SLOT = 64 * 1024

    const val HEADER = 64

    const val MODULE_MAX = SLOT - HEADER

    /** Why the Knob refused, by its status code; null for a code that is no failure. */
    fun failure(code: Int): String? = when (code) {
        0x81 -> "Every plugin slot on the Knob is taken."
        0x82 -> "The Knob took it for no upload."
        0x83 -> "A piece went missing on the way."
        0x84 -> "The plugin arrived other than announced."
        0x85 -> "The Knob's flash failed."
        else -> null
    }

    /** The data write for the piece of [module] at [offset], at most [piece] bytes long. */
    fun pieceAt(module: ByteArray, offset: Int, piece: Int = PIECE_MAX): ByteArray {
        val end = minOf(offset + piece, module.size)
        return ByteArray(OFFSET) { (offset ushr 8 * it).toByte() } + module.copyOfRange(offset, end)
    }
}

class PluginInvalid(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a plugin module says about itself. Its signature is not checked here: the Knob does that before it asks. */
class PluginAbout(
    val name: String,
    val summary: String,
    val rights: List<String>,
    val version: String,
    /** The signing key, in hex. */
    val key: String,
    /** Eight bytes that name the plugin on the Knob, in hex. */
    val id: String,
    val size: Int,
)

private const val MANIFEST = "teetotum.manifest"
private const val SIGNATURE = "teetotum.signature"
private const val MANIFEST_VERSION = 3
private const val KEY_LEN = 32
private const val SIGNATURE_LEN = 64
private const val NAME_MAX = 20
private const val ICON_SIZE = 24
private const val SUMMARY_AT = 6 + NAME_MAX + 4 * ICON_SIZE
private const val SUMMARY_MAX = 32
private const val ABI_AT = SUMMARY_AT + 1 + SUMMARY_MAX
private const val VERSION_AT = ABI_AT + 2
private const val ID_LEN = 8
private const val HASH_LEN = 32
private val RIGHTS = listOf("HID", "KNOB", "RANDOM", "RADIO", "HAPTIC")
private val WASM_MAGIC = byteArrayOf(0, 0x61, 0x73, 0x6d, 1, 0, 0, 0)

/** The `SHA-256` or `SHA-512` digest of [bytes]. */
expect fun digest(algorithm: String, bytes: ByteArray): ByteArray

fun hexOf(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private class Section(val name: String, val end: Int, val contents: ByteArray)

/** Every custom section of [wasm], as the Knob's `walk` reads them. */
private fun customSections(wasm: ByteArray): List<Section> {
    fun invalid(): Nothing = throw PluginInvalid("Not a WebAssembly module.")

    fun leb128(at: Int): Pair<Int, Int> {
        var value = 0L
        for (i in 0 until 5) {
            val byte = wasm.getOrNull(at + i)?.toInt() ?: invalid()
            value += (byte and 0x7f).toLong() shl 7 * i
            if (byte and 0x80 == 0) return (value.takeIf { it <= Int.MAX_VALUE } ?: invalid()).toInt() to at + i + 1
        }
        invalid()
    }
    if (wasm.size < WASM_MAGIC.size || !wasm.copyOf(WASM_MAGIC.size).contentEquals(WASM_MAGIC)) invalid()
    val sections = mutableListOf<Section>()
    var at = WASM_MAGIC.size
    while (at < wasm.size) {
        val id = wasm[at]
        val (size, body) = leb128(at + 1)
        val end = body + size
        if (end > wasm.size) invalid()
        if (id == 0.toByte()) {
            val (length, name) = leb128(body)
            if (name + length > end) invalid()
            sections += Section(
                wasm.copyOfRange(name, name + length).decodeToString(),
                end,
                wasm.copyOfRange(name + length, end),
            )
        }
        at = end
    }
    return sections
}

/** What [wasm] says about itself; throws [PluginInvalid] for anything the Knob would not take. */
fun describe(wasm: ByteArray): PluginAbout {
    if (wasm.size > PluginService.MODULE_MAX) throw PluginInvalid("Longer than ${PluginService.MODULE_MAX} bytes.")
    val sections = customSections(wasm)
    val manifest = sections.filter { it.name == MANIFEST }.singleOrNull()?.contents
        ?: throw PluginInvalid("No TeeToTum plugin.")
    val signature = sections.filter { it.name == SIGNATURE }.singleOrNull()
        ?: throw PluginInvalid("The plugin is not signed.")
    if (signature.end != wasm.size || signature.contents.size != KEY_LEN + SIGNATURE_LEN) {
        throw PluginInvalid("The plugin's signature is malformed.")
    }
    return aboutOf(manifest, signature.contents.copyOf(KEY_LEN), wasm.size)
}

/** The plugin that [manifest] names, signed with [key]. */
private fun aboutOf(manifest: ByteArray, key: ByteArray, size: Int): PluginAbout {
    fun byte(at: Int) = manifest[at].toInt() and 0xff
    fun number(at: Int) = byte(at) or (byte(at + 1) shl 8)
    fun text(at: Int, length: Int) = try {
        manifest.copyOfRange(at, at + length).decodeToString(throwOnInvalidSequence = true)
    } catch (e: CharacterCodingException) {
        throw PluginInvalid("The plugin's manifest is malformed.", e)
    }
    if (manifest.isEmpty() || byte(0) != MANIFEST_VERSION || manifest.size < VERSION_AT + 6) {
        throw PluginInvalid("Manifest format ${manifest.firstOrNull()}, not $MANIFEST_VERSION.")
    }
    val nameLength = byte(5)
    val summaryLength = byte(SUMMARY_AT)
    if (nameLength == 0 || nameLength > NAME_MAX || summaryLength > SUMMARY_MAX) {
        throw PluginInvalid("The plugin's manifest is malformed.")
    }
    val bits = (1..4).fold(0) { value, i -> value or (byte(i) shl 8 * (i - 1)) }
    val name = text(6, nameLength)
    return PluginAbout(
        name = name,
        summary = text(SUMMARY_AT + 1, summaryLength),
        rights = RIGHTS.filterIndexed { i, _ -> bits and (1 shl i) != 0 },
        version = listOf(0, 2, 4).joinToString(".") { number(VERSION_AT + it).toString() },
        key = hexOf(key),
        id = hexOf(digest("SHA-512", key + name.encodeToByteArray()).copyOf(ID_LEN)),
        size = size,
    )
}

/** The slot header in front of [wasm], waiting to be accepted, as teetotum-pack writes it. */
fun slotHeader(wasm: ByteArray, about: PluginAbout): ByteArray {
    val header = ByteArray(PluginService.HEADER)
    byteArrayOf(0x54, 0x54, 0x50, 0x53, 1, 0xff.toByte()).copyInto(header)
    about.id.chunked(2).map { it.toInt(16).toByte() }.toByteArray().copyInto(header, ID_LEN)
    ByteArray(4) { (wasm.size ushr 8 * it).toByte() }.copyInto(header, 16)
    digest("SHA-512", wasm).copyOf(HASH_LEN).copyInto(header, 20)
    return header
}

/** Where the plugin catalogue lives, the same the Knob's web page reads. */
const val CATALOGUE_URL = "https://raw.githubusercontent.com/teetotum-rs/plugins/main/index.json"

@Serializable
data class Catalogue(val format: Int, val plugins: List<CataloguePlugin>)

@Serializable
data class CataloguePlugin(
    val name: String,
    val summary: String,
    val version: String,
    val rights: List<String> = emptyList(),
    val id: String,
    val key: String,
    val size: Int,
    val sha256: String,
    val url: String,
    val tags: List<String> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

/** The catalogue in [text]; throws [PluginInvalid] for a format this app does not read. */
fun catalogueOf(text: String): Catalogue {
    val catalogue = try {
        json.decodeFromString<Catalogue>(text)
    } catch (e: IllegalArgumentException) {
        throw PluginInvalid("The catalogue could not be read: ${e.message?.lineSequence()?.first()}", e)
    }
    if (catalogue.format != 1) throw PluginInvalid("Catalogue format ${catalogue.format}; this app reads 1.")
    return catalogue
}

/** [wasm] as downloaded for [plugin], described; throws [PluginInvalid] if it is not what the catalogue says. */
fun checkDownload(plugin: CataloguePlugin, wasm: ByteArray): PluginAbout {
    if (wasm.size != plugin.size || hexOf(digest("SHA-256", wasm)) != plugin.sha256) {
        throw PluginInvalid("The download does not match the catalogue.")
    }
    val about = describe(wasm)
    if (about.id != plugin.id || about.key != plugin.key) {
        throw PluginInvalid("The plugin names another id or key than the catalogue.")
    }
    return about
}

/** The facts line under a plugin's summary: version, size and what it may use. */
fun pluginFacts(version: String, size: Int, rights: List<String>): String =
    "Version $version · ${sizeText(size.toLong())} · uses ${rights.joinToString(", ").ifEmpty { "nothing" }}"
