package com.mau.hub.wear.phone

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Where you paste HA's long-lived token (issued by the HA admin) and checks the address. The token is kept in this
 * app's private storage only: never shown again, never sent to the watch.
 */
class Setup : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val status = TextView(this)
        val url = EditText(this).apply {
            hint = "http://192.168.1.10:8123"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString(URL, null) ?: homeUrl(this@Setup))
        }
        val token = EditText(this).apply {
            hint = "Pega aquí el token de HA"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        fun show() {
            status.text = if (prefs.getString(TOKEN, "").isNullOrBlank()) "Sin token: el reloj no puede usar HA."
            else "Token guardado. Pega uno nuevo para cambiarlo."
        }
        val save = Button(this).apply {
            text = "Guardar"
            setOnClickListener {
                val e = prefs.edit().putString(URL, url.text.toString().trim())
                token.text.toString().trim().takeIf { it.isNotEmpty() }?.let { e.putString(TOKEN, it) }
                e.apply()
                token.text.clear()
                show()
                Bridge.close() // the watch reconnects with the new settings
            }
        }
        show()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(this@Setup).apply { text = "Joint Launcher SW3: enlace de HA para el SmartWatch 3"; textSize = 18f })
            listOf(TextView(this@Setup).apply { text = "Dirección de HA" }, url, TextView(this@Setup).apply { text = "Token" }, token, save, status)
                .forEach { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
        })
    }

    companion object {
        private const val URL = "url"
        private const val TOKEN = "token"

        private fun prefs(c: Context) = c.getSharedPreferences("ha", Context.MODE_PRIVATE)

        /** Address and token, or null until a token has been saved. */
        fun creds(c: Context): Pair<String, String>? {
            val p = prefs(c)
            val token = p.getString(TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
            val url = p.getString(URL, null)?.takeIf { it.isNotBlank() } ?: homeUrl(c)
            return url.takeIf { it.isNotBlank() }?.let { it to token }
        }

        private fun homeUrl(c: Context) =
            runCatching { JSONObject(c.assets.open("config.json").bufferedReader().use { it.readText() }).optString("homeUrl") }.getOrDefault("")
    }
}
