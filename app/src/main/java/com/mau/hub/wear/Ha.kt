package com.mau.hub.wear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Ent(val id: String, val state: String, val attrs: JSONObject) {
    val name: String get() = attrs.optString("friendly_name").ifEmpty { id.substringAfter('.') }
}

/**
 * One WebSocket to Home Assistant while the app is visible (same client as MauHomeAssist, trimmed to what a
 * media watch needs). [urls]: home address first, then the tunnel; a failed connection moves to the next one.
 */
class Ha(
    private val urls: List<String>,
    private val token: String,
    private val ids: List<String>,
    startIndex: Int = 0,
    /** Called with the address index that just authenticated, so the next start tries it first (Bluetooth is slow). */
    private val onWorking: (Int) -> Unit = {},
) {
    private var urlIndex = startIndex.coerceIn(0, urls.lastIndex)
    val url get() = urls[urlIndex]
    private val http = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var nextId = 1
    private val pending = HashMap<Int, (JSONObject) -> Unit>()
    private var subId = -1
    private var wanted = false
    private var authFailed = false
    // HA treats the first message on a connection as the login, so commands wait for auth_ok
    private var authed = false
    private val queued = mutableListOf<String>()

    val states = mutableStateMapOf<String, Ent>()
    var status by mutableStateOf("Conectando…")
        private set

    fun connect() {
        wanted = true
        if (ws != null || authFailed) return
        authed = false
        ws = http.newWebSocket(Request.Builder().url(wsUrl(url)).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = JSONObject(text)
                main.post { if (webSocket === ws) onMsg(m) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { dropped(webSocket, "Sin conexión") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { dropped(webSocket, "Desconectado") }
            }
        })
    }

    fun close() {
        wanted = false
        ws?.close(1000, null)
        ws = null
    }

    private fun dropped(sock: WebSocket, why: String) {
        if (sock !== ws) return
        ws = null
        authed = false
        pending.clear()
        queued.clear()
        if (authFailed) return
        status = why
        // try the other address right away; once both have failed, back off
        urlIndex = (urlIndex + 1) % urls.size
        val delay = if (urlIndex == 0) 5000L else 500L
        if (wanted) main.postDelayed({ if (wanted) connect() }, delay)
    }

    /** Any websocket command; [cb] gets the "result" message. */
    fun send(o: JSONObject, cb: ((JSONObject) -> Unit)? = null): Int {
        val id = nextId++
        o.put("id", id)
        cb?.let { pending[id] = it }
        if (authed) ws?.send(o.toString()) else queued += o.toString()
        return id
    }

    private fun onMsg(m: JSONObject) {
        when (m.optString("type")) {
            "auth_required" -> ws?.send(JSONObject().put("type", "auth").put("access_token", token).toString())
            // never retry a rejected token: repeated bad logins get the watch's IP banned by http.ip_ban
            "auth_invalid" -> {
                authFailed = true
                status = "Token inválido"
                ws?.close(1000, null)
            }
            "auth_ok" -> {
                status = ""
                authed = true
                queued.forEach { ws?.send(it) }
                queued.clear()
                onWorking(urlIndex)
                subId = send(JSONObject().put("type", "subscribe_entities").put("entity_ids", JSONArray(ids)))
            }
            "result" -> pending.remove(m.optInt("id"))?.invoke(m)
            "event" -> if (m.optInt("id") == subId) apply(m.getJSONObject("event"))
        }
    }

    private fun apply(e: JSONObject) {
        e.optJSONObject("a")?.let { a ->
            for (k in a.keys()) {
                val o = a.getJSONObject(k)
                states[k] = Ent(k, o.optString("s"), o.optJSONObject("a") ?: JSONObject())
            }
        }
        e.optJSONObject("c")?.let { c ->
            for (k in c.keys()) {
                val old = states[k] ?: continue
                states[k] = merge(old, c.getJSONObject(k))
            }
        }
        e.optJSONArray("r")?.let { r -> for (i in 0 until r.length()) states.remove(r.getString(i)) }
    }

    fun call(domain: String, service: String, id: String, data: JSONObject = JSONObject()) {
        send(
            JSONObject().put("type", "call_service").put("domain", domain).put("service", service)
                .put("service_data", data).put("target", JSONObject().put("entity_id", id))
        )
    }

    private val images = LruCache<String, Bitmap>(20)

    /** Album art / posters: entity_picture is a path on HA (/api/media_player_proxy/…) or a full URL. */
    suspend fun image(src: String): Bitmap? {
        val full = if (src.startsWith("/")) url + src else src
        images[full]?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(full).apply { if (full.startsWith(url)) header("Authorization", "Bearer $token") }.build()
                http.newCall(req).execute().use { if (it.isSuccessful) decodeScaled(it.body!!.bytes(), 300) else null }
            }.getOrNull()
        }
        bmp?.let { images.put(full, it) }
        return bmp
    }

    companion object {
        /** http → ws, https → wss. */
        fun wsUrl(base: String) = base.trimEnd('/').replaceFirst("http", "ws") + "/api/websocket"

        /** Applies a subscribe_entities "c" diff: {"+": {s, a}, "-": {a: [keys]}}. */
        fun merge(old: Ent, d: JSONObject): Ent {
            val attrs = JSONObject(old.attrs.toString())
            val plus = d.optJSONObject("+")
            plus?.optJSONObject("a")?.let { p -> for (k in p.keys()) attrs.put(k, p.get(k)) }
            d.optJSONObject("-")?.optJSONArray("a")?.let { r -> for (i in 0 until r.length()) attrs.remove(r.getString(i)) }
            val s = plus?.optString("s").orEmpty()
            return Ent(old.id, s.ifEmpty { old.state }, attrs)
        }

        /** Decodes at the smallest power-of-two scale that keeps both sides ≥ [maxPx], so big pictures fit in memory. */
        fun decodeScaled(bytes: ByteArray, maxPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
