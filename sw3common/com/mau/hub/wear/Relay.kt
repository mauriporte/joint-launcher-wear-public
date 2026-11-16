package com.mau.hub.wear

/**
 * The SW3's HA link, shared by the watch (wear-legacy) and the phone (phone-sw3): the watch speaks HA's WebSocket
 * protocol, the phone holds the socket and the token and passes frames both ways over the Wear Data Layer.
 */
object RelayPaths {
    /** watch → phone: (re)connect to HA; the phone answers with HA's frames on [WS] (auth_ok first) or an [ERR]. */
    const val OPEN = "/ha/open"
    /** both ways: one HA WebSocket frame (JSON); phone → watch frames come in [Chunks]. */
    const val WS = "/ha/ws"
    /** phone → watch: plain-text reason the socket is down ("Falta el token en el móvil", "Sin conexión con HA"). */
    const val ERR = "/ha/err"
    const val CLOSE = "/ha/close"
    /** watch → phone, fire and forget: {domain, service, data} as one REST service call (the face's TV keys). */
    const val CALL = "/ha/call"

    /** http → ws, https → wss. */
    fun wsUrl(base: String) = base.trimEnd('/').replaceFirst("http", "ws") + "/api/websocket"
}

/** Data Layer messages top out near 100 KB and lovelace/config can be bigger, so frames go as numbered parts. */
object Chunks {
    const val MAX = 90_000

    /** Each part is "key|i|n|" + its bytes. */
    fun split(key: String, data: ByteArray): List<ByteArray> {
        val n = maxOf(1, (data.size + MAX - 1) / MAX)
        return (0 until n).map { i -> "$key|$i|$n|".toByteArray() + data.copyOfRange(i * MAX, minOf(data.size, (i + 1) * MAX)) }
    }

    /** Puts frames back together; parts may arrive in any order. */
    class Joiner {
        private val parts = HashMap<String, Array<ByteArray?>>()

        /** The whole frame once its last part is in, else null. */
        fun add(msg: ByteArray): ByteArray? {
            val bars = IntArray(3)
            var found = 0
            for (p in msg.indices) if (msg[p] == '|'.code.toByte()) { bars[found++] = p; if (found == 3) break }
            if (found < 3) return null
            val key = String(msg, 0, bars[0])
            val i = String(msg, bars[0] + 1, bars[1] - bars[0] - 1).toIntOrNull() ?: return null
            val n = String(msg, bars[1] + 1, bars[2] - bars[1] - 1).toIntOrNull() ?: return null
            val slots = parts.getOrPut(key) { arrayOfNulls(n) }
            if (i !in slots.indices) return null
            slots[i] = msg.copyOfRange(bars[2] + 1, msg.size)
            if (slots.any { it == null }) return null
            parts.remove(key)
            return slots.fold(ByteArray(0)) { acc, b -> acc + b!! }
        }
    }
}
