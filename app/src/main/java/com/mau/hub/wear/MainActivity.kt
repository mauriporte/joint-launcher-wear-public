package com.mau.hub.wear

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

class MainActivity : ComponentActivity() {
    private var ha: Ha? = null

    /** Screen to open when launched from the Accesos tile ("playing"); cleared once shown. */
    private var route by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the watch face shows other apps' complications (weather, steps, heart rate) only after this one-time grant
        if (checkSelfPermission(COMPLICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(COMPLICATIONS), 1)
        if (!Config.load(this)) {
            setContent { Message("Falta config.json") }
            return
        }
        loadAccent(this)
        Thread { refreshAccent(applicationContext) }.start()
        // the token is pushed by set-token.ps1 via `run-as`, never typed on the watch
        val token = File(filesDir, "token").takeIf { it.exists() }?.readText()?.trim().orEmpty()
        val last = File(filesDir, LAST_URL)
        ha = if (token.isEmpty()) null else Ha(
            Config.urls(File(filesDir, "url").takeIf { it.exists() }?.readText()?.trim()), token, Config.ids,
            startIndex = last.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0,
            onWorking = { last.writeText(it.toString()) },
        )
        route = intent.getStringExtra("route")
        setContent { ha?.let { App(it, route) { route = null } } ?: Message("Falta el token de HA:\nset-token.ps1") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route = intent.getStringExtra("route")
    }

    override fun onStart() {
        super.onStart()
        ha?.connect()
    }

    override fun onStop() {
        ha?.close()
        super.onStop()
    }

    companion object {
        /** Index (0 = home, 1 = tunnel) of the address that worked last. */
        const val LAST_URL = "last_url"
        const val COMPLICATIONS = "com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA"
    }
}
