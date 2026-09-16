package io.github.teetotum_rs.app

/** Where downloaded files go on this platform. */
interface Downloads {
    /** A new file named [name]; nothing is visible to other apps before [FileSink.commit]. */
    fun create(name: String): FileSink
}

interface FileSink {
    fun write(bytes: ByteArray, count: Int)

    /** Finishes the file and returns where it went, for the user. */
    fun commit(): String

    /** Removes what was written. */
    fun abort()
}
