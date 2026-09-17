package io.github.teetotum_rs.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
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
    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var theme by mutableStateOf(Theme.System)

    override fun onCreate(savedInstanceState: Bundle?) {
        theme = Theme.entries.find { it.name == settings.getString(THEME, null) } ?: Theme.System
        edgeToEdge()
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
                onExit = { finish() },
                libraries = { resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() } },
                theme = theme,
                onTheme = {
                    theme = it
                    settings.edit().putString(THEME, it.name).apply()
                    edgeToEdge()
                },
            ) { onCode -> Scanner(onCode) }
        }
    }

    /** System bar icons that stand out against the theme: light ones when it is dark. */
    private fun edgeToEdge(config: Configuration = resources.configuration) {
        val night = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val bars = if (theme == Theme.Dark || night) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(bars, bars)
    }

    /** The phone switching light and dark keeps the activity, and with it the joined Knob. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        edgeToEdge(newConfig)
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

    private companion object {
        const val THEME = "theme"
    }
}
