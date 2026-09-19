package io.github.teetotum_rs.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    private lateinit var radio: AndroidRadio
    private var shared by mutableStateOf<Shared?>(null)
    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var preferences by mutableStateOf(Preferences())

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = Preferences(
            theme = Theme.entries.find { it.name == settings.getString(THEME, null) } ?: Theme.System,
            start = Start.entries.find { it.name == settings.getString(START, null) } ?: Start.Home,
            catalogue = CatalogueLoad.entries.find {
                it.name == settings.getString(
                    CATALOGUE,
                    null,
                )
            } ?: CatalogueLoad.Tap,
            lastPage = Page.entries.find { it.name == settings.getString(LAST_PAGE, null) } ?: Page.Home,
            folded = settings.getStringSet(FOLDED, null).orEmpty()
                .mapNotNull { name -> Page.entries.find { it.name == name } }
                .toSet(),
            scroll = buildMap {
                for (page in Page.entries) {
                    Position.parse(settings.getString(SCROLL + page.name, null) ?: continue)?.let { put(page, it) }
                }
            },
        )
        edgeToEdge()
        super.onCreate(savedInstanceState)
        radio = AndroidRadio(applicationContext)
        val downloads = AndroidDownloads(applicationContext)
        val bluetooth = AndroidBluetooth(applicationContext)
        shared = sharedOf(this, intent)
        setContent {
            App(
                radio,
                downloads,
                bluetooth,
                bluetoothAccess = { content -> BluetoothAccess(content) },
                picker = { onPick -> rememberPicker(onPick) },
                back = { enabled, onBack -> BackHandler(enabled, onBack) },
                code = debugCode(),
                shared = shared,
                onShareEnd = { shared = null },
                onExit = { finish() },
                libraries = { resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() } },
                preferences = preferences,
                onPreferences = {
                    preferences = it
                    settings.edit {
                        putString(THEME, it.theme.name)
                        putString(START, it.start.name)
                        putString(CATALOGUE, it.catalogue.name)
                        putString(LAST_PAGE, it.lastPage.name)
                        putStringSet(FOLDED, it.folded.map { page -> page.name }.toSet())
                        for ((page, position) in it.scroll) putString(SCROLL + page.name, position.toString())
                    }
                },
            ) { onCode -> Scanner(onCode) }
        }
    }

    /** System bar icons that stand out against the phone's mode: light ones when it is dark. */
    private fun edgeToEdge(config: Configuration = resources.configuration) {
        val night = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val bars = if (night) {
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
        const val START = "start"
        const val CATALOGUE = "catalogue"
        const val LAST_PAGE = "last_page"
        const val FOLDED = "folded"

        /** Followed by the page's name, one key per page. */
        const val SCROLL = "scroll_"
    }
}
