package io.github.teetotum_rs.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

/** The system's document picker, for any number of files of any type. */
@Composable
fun rememberPicker(onPicked: (List<Pick>) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onPicked(uris.mapNotNull { pickOf(context, it) })
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** A picked document, or null where the provider does not tell its size, which the Knob needs first. */
internal fun pickOf(context: Context, uri: Uri): Pick? {
    val resolver = context.contentResolver
    // All columns: a provider throws on a named column it lacks, and shared media has no last-modified.
    val (name, size, modified) = resolver.query(uri, null, null, null, null)?.use { row ->
        if (!row.moveToFirst()) return null
        fun long(column: String) = row.getColumnIndex(column).takeIf { it >= 0 && !row.isNull(it) }?.let(row::getLong)
        val name = row.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(row::getString)
        Triple(name, long(OpenableColumns.SIZE), long(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
    } ?: return null
    if (name == null || size == null) return null
    return Pick(name, size, modified?.takeIf { it > 0 }) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Could not read $name.")
        object : FileSource {
            override fun read(buffer: ByteArray, count: Int) = input.read(buffer, 0, count)

            override fun close() = input.close()
        }
    }
}
