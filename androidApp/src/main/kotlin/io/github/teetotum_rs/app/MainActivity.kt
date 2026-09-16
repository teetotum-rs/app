package io.github.teetotum_rs.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var radio: AndroidRadio

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        radio = AndroidRadio(applicationContext)
        val downloads = AndroidDownloads(applicationContext)
        setContent {
            App(
                radio,
                downloads,
                debugCode(),
                picker = { onPicked -> rememberPicker(onPicked) },
                back = { enabled, onBack -> BackHandler(enabled, onBack) },
            ) { onCode -> Scanner(onCode) }
        }
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
