package io.github.teetotum_rs.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Shows [content] once the app may search for and connect to the Knob; asks first. */
@Composable
fun BluetoothAccess(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val needed = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Before Android 12 a Bluetooth search needs the location permission.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    var allowed by remember {
        mutableStateOf(
            needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        allowed = granted.values.all { it }
    }
    if (allowed) {
        content()
    } else {
        LaunchedEffect(Unit) { ask.launch(needed) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ActionButton("Allow Bluetooth", onClick = { ask.launch(needed) })
        }
    }
}
