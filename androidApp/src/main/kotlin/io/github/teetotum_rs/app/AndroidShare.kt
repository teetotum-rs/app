package io.github.teetotum_rs.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.IntentCompat

/** The files in a share [intent], or null where the intent is not a share. */
fun sharedOf(context: Context, intent: Intent): Shared? {
    val uris = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> return null
    }
    val picks = uris.mapNotNull { uri ->
        runCatching { pickOf(context, uri) }
            .onFailure { Log.w("TeeToTum", "Could not read the shared $uri", it) }
            .getOrNull()
    }
    return Shared(picks, uris.size - picks.size)
}
