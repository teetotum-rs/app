package io.github.teetotum_rs.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A folder on the Knob's card, as the firmware answers `GET` with `Accept: application/json`. */
@Serializable
data class Listing(
    /** The firmware, as `v0.3.3 e11dd8a`. */
    val version: String,
    /** The folder, `/` or `/NAME/`. */
    val path: String,
    @SerialName("card_bytes") val cardBytes: Long? = null,
    val entries: List<Entry>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): Listing = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class Entry(
    val name: String,
    val directory: Boolean,
    val size: Long,
    /** `2026-09-16T12:00:00`, or null where the writer set none. */
    val created: String? = null,
    val modified: String? = null,
    /** Only a date. */
    val accessed: String? = null,
    /** Letters R read-only, H hidden, S system, A archive. */
    val attributes: String = "",
)

/** The oldest firmware whose folders answer as JSON. */
val OLDEST_FIRMWARE = FirmwareVersion(0, 3, 3)

data class FirmwareVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<FirmwareVersion> {
    override fun compareTo(other: FirmwareVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""^v(\d+)\.(\d+)\.(\d+)""")

        /** The release in a firmware's version text, or null for a development build. */
        fun parse(text: String): FirmwareVersion? =
            pattern.find(text)?.destructured?.let { (major, minor, patch) ->
                FirmwareVersion(major.toInt(), minor.toInt(), patch.toInt())
            }
    }
}
