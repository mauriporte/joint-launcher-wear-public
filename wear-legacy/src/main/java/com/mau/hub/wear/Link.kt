package com.mau.hub.wear

// copied from Joint Remote's wear-legacy Link.kt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/**
 * The Wear Data Layer through the 2016 API (GoogleApiClient), the newest the SmartWatch 3's Play services (8.7) has:
 * the current client library asks for 20.3+. The messages are the same, so the phone-sw3 Relay doesn't care.
 */
object Link {
    private var client: GoogleApiClient? = null
    private val main = Handler(Looper.getMainLooper())

    /** Connected client, or null; blocks up to 10 s, so call it off the main thread. */
    private fun connected(c: Context): GoogleApiClient? {
        val g = client ?: GoogleApiClient.Builder(c.applicationContext).addApi(Wearable.API).build().also { client = it }
        if (!g.isConnected) g.blockingConnect(10, TimeUnit.SECONDS)
        return g.takeIf { it.isConnected }
    }

    fun send(c: Context, path: String, data: ByteArray, onNoPhone: () -> Unit) = Thread {
        val g = connected(c)
        val nodes = g?.let { Wearable.NodeApi.getConnectedNodes(it).await(10, TimeUnit.SECONDS).nodes }.orEmpty()
        Log.i("JointSW3", "$path to ${nodes.map { it.displayName }}")
        if (g == null || nodes.isEmpty()) onNoPhone()
        else nodes.forEach { n ->
            val r = Wearable.MessageApi.sendMessage(g, n.id, path, data).await(10, TimeUnit.SECONDS)
            if (!r.status.isSuccess) Log.w("JointSW3", "$path to ${n.displayName} failed: ${r.status}")
        }
    }.start()

    /** Messages from the phone, handed to the main thread; returns the handle for [unlisten]. */
    fun listen(c: Context, on: (String, ByteArray) -> Unit): Any {
        val l = MessageApi.MessageListener { m -> main.post { on(m.path, m.data) } }
        Thread { connected(c)?.let { Wearable.MessageApi.addListener(it, l) } }.start()
        return l
    }

    fun unlisten(c: Context, handle: Any) {
        Thread { connected(c)?.let { Wearable.MessageApi.removeListener(it, handle as MessageApi.MessageListener) } }.start()
    }
}
