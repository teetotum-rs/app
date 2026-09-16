package io.github.teetotum_rs.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var radio: AndroidRadio
    private var shared by mutableStateOf<Shared?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        radio = AndroidRadio(applicationContext)
        val downloads = AndroidDownloads(applicationContext)
        shared = sharedOf(this, intent)
        setContent {
            App(
                radio,
                downloads,
                debugCode(),
                picker = { onPicked -> rememberPicker(onPicked) },
                back = { enabled, onBack -> BackHandler(enabled, onBack) },
                shared = shared,
                onShared = { shared = null },
            ) { onCode -> Scanner(onCode) }
        }
    }

    /** A share while the app runs keeps the Knob it has joined. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedOf(this, intent)?.let { shared = it }
    }

    /** In debug builds, `adb shell am start ... --es join 'WIFI:...'` stands in for the camera. */
    private fun debugCode(): JoinCode? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return intent.getStringExtra("join")?.let(JoinCode::parse)
    }

    override fun onDestroy() {
        if (isFinishing) radio.leave()
        super.onDestroy()
    }
}
