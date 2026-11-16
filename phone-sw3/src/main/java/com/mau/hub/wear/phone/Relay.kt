package com.mau.hub.wear.phone

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.mau.hub.wear.Chunks
import com.mau.hub.wear.RelayPaths
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Messages from the SW3 (already on a background thread). */
class Relay : WearableListenerService() {
    override fun onMessageReceived(m: MessageEvent) {
        Bridge.node = m.sourceNodeId
        when (m.path) {
            RelayPaths.OPEN -> Bridge.open(this)
            RelayPaths.WS -> Bridge.forward(this, String(m.data))
            RelayPaths.CLOSE -> Bridge.close()
            RelayPaths.CALL -> Bridge.call(this, String(m.data))
        }
    }
}

/** The one HA WebSocket, logged in with the token stored on this phone; every other frame goes to the watch as is. */
object Bridge {
    private val http = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
    private val seq = AtomicInteger()
    @Volatile private var ws: WebSocket? = null
    @Volatile var node: String? = null

    @Synchronized
    fun open(ctx: Context) {
        close()
        val app = ctx.applicationContext
        val (url, token) = Setup.creds(app) ?: return err(app, "Falta el token en el móvil")
        ws = http.newWebSocket(Request.Builder().url(RelayPaths.wsUrl(url)).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return
                // the login stays here; the watch only ever sees auth_ok / auth_invalid
                if (runCatching { JSONObject(text).optString("type") }.getOrNull() == "auth_required")
                    webSocket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                else toWatch(app, text.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = gone(app, webSocket, "Sin conexión con HA")

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone(app, webSocket, "Desconectado")
        })
    }

    /** A frame from the watch; if the socket died with the app (Android killed it), the watch reconnects. */
    fun forward(ctx: Context, text: String) {
        if (ws?.send(text) != true) err(ctx.applicationContext, "Desconectado")
    }

    /** One service call over REST ({domain, service, data}), no socket needed; failures only reach the log. */
    fun call(ctx: Context, body: String) {
        val (url, token) = Setup.creds(ctx.applicationContext) ?: return err(ctx.applicationContext, "Falta el token en el móvil")
        runCatching {
            val j = JSONObject(body)
            val req = Request.Builder().url("${url.trimEnd('/')}/api/services/${j.getString("domain")}/${j.getString("service")}")
                .header("Authorization", "Bearer $token")
                .post((j.optJSONObject("data") ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { if (!it.isSuccessful) Log.w("JointSW3", "call ${j.optString("service")}: HTTP ${it.code}") }
        }.onFailure { Log.w("JointSW3", "call failed: ${it.message}") }
    }

    @Synchronized
    fun close() {
        ws?.close(1000, null)
        ws = null
    }

    private fun gone(ctx: Context, sock: WebSocket, why: String) {
        if (sock !== ws) return
        ws = null
        err(ctx, why)
    }

    private fun toWatch(ctx: Context, data: ByteArray) {
        val n = node ?: return
        val mc = Wearable.getMessageClient(ctx)
        Chunks.split(seq.incrementAndGet().toString(), data).forEach { mc.sendMessage(n, RelayPaths.WS, it) }
    }

    private fun err(ctx: Context, why: String) {
        val n = node ?: return
        Wearable.getMessageClient(ctx).sendMessage(n, RelayPaths.ERR, why.toByteArray())
    }
}
