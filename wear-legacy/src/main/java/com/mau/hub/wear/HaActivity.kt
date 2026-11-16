package com.mau.hub.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** MauHomeAssist on the SW3; [ROUTE] picks the first screen ("ring" from the face's clock). Connected while visible. */
class HaActivity : ComponentActivity() {
    private lateinit var ha: Ha
    private var route by mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HaConfig.load(this)
        ha = Ha(applicationContext)
        route = intent.getStringExtra(ROUTE) ?: "home"
        setContent {
            MaterialTheme(colors = darkColors(primary = HaBlue, background = Color.Black, surface = CardBg)) {
                HaApp(ha, route) { finish() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route = intent.getStringExtra(ROUTE) ?: "home"
    }

    override fun onStart() {
        super.onStart()
        ha.connect()
    }

    override fun onStop() {
        ha.close()
        super.onStop()
    }

    companion object {
        const val ROUTE = "route"
    }
}
