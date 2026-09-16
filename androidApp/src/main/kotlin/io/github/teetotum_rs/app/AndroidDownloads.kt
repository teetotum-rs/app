package io.github.teetotum_rs.app

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.IOException

/** Files land in `Download/TeeToTum`, through the media store, which needs no permission. */
class AndroidDownloads(context: Context) : Downloads {
    private val resolver = context.contentResolver

    override fun create(name: String): FileSink {
        val extension = name.substringAfterLast('.', "").lowercase()
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?.let { put(MediaStore.Downloads.MIME_TYPE, it) }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create $name in Downloads.")
        val out = resolver.openOutputStream(uri) ?: throw IOException("Could not write $name.")

        return object : FileSink {
            override fun write(bytes: ByteArray, count: Int) = out.write(bytes, 0, count)

            override fun commit(): String {
                out.close()
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                val saved = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
                return "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$saved"
            }

            override fun abort() {
                runCatching { out.close() }
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private companion object {
        const val FOLDER = "TeeToTum"
    }
}
