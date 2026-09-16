package io.github.teetotum_rs.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var radio: AndroidRadio

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        radio = AndroidRadio(applicationContext)
        setContent { App(radio) { onCode -> Scanner(onCode) } }
    }

    override fun onDestroy() {
        if (isFinishing) radio.leave()
        super.onDestroy()
    }
}
