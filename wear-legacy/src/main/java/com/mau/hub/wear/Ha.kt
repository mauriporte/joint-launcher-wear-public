package com.mau.hub.wear

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class Ent(val id: String, val state: String, val attrs: JSONObject) {
    val domain get() = id.substringBefore('.')
    val name: String get() = attrs.optString("friendly_name").ifEmpty { id.substringAfter('.') }
    val unit: String get() = attrs.optString("unit_of_measurement")
    val on get() = state in ON_STATES

    companion object {
        val ON_STATES = setOf("on", "open", "unlocked", "playing", "heat", "cool")
    }
}

/**
 * MauHomeAssist's HA client for the SW3. AW 1.5 apps can't reach HA, so the WebSocket lives on the phone (phone-sw3)
 * and its frames come and go over the Data Layer; the protocol (lovelace/config, subscribe_entities, call_service)
 * is the Fossil app's. The phone does the login: the token never reaches the watch.
 */
class Ha(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val joiner = Chunks.Joiner()
    private var listener: Any? = null
    private var nextId = 1
    private val pending = HashMap<Int, (JSONObject) -> Unit>()
    private var subId = -1
    private var wanted = false
    private var authFailed = false
    private var authed = false
    private val queued = mutableListOf<JSONObject>()
    private val retry = Runnable { if (wanted && !authed) connect() }
    private var lastError: String? = null

    val states = mutableStateMapOf<String, Ent>()
    var status by mutableStateOf("Conectando…")
        private set
    var dash by mutableStateOf<Dash?>(null)
        private set

    /** Entity waiting for ✓/✗; the app draws the prompt over whatever screen is showing. */
    var confirm by mutableStateOf<String?>(null)

    var alarmPrompt by mutableStateOf(false)
    var alarmBusy by mutableStateOf(false)
        private set
    var alarmStatus by mutableStateOf<String?>(null)

    fun connect() {
        wanted = true
        if (listener == null) listener = Link.listen(ctx, ::onFrame)
        if (authFailed) return
        authed = false
        pending.clear()
        nextId = 1 // a fresh socket on the phone: HA wants ids from 1 again
        // the phone's first answer can beat the listener's registration: try again if nothing comes back
        main.removeCallbacks(retry)
        main.postDelayed(retry, 20_000L)
        Link.send(ctx, RelayPaths.OPEN, ByteArray(0)) { main.post { down("Sin conexión con el móvil") } }
    }

    fun close() {
        wanted = false
        main.removeCallbacks(retry)
        Link.send(ctx, RelayPaths.CLOSE, ByteArray(0)) {}
        listener?.let { Link.unlisten(ctx, it) }
        listener = null
    }

    private fun onFrame(path: String, data: ByteArray) {
        when (path) {
            RelayPaths.WS -> joiner.add(data)?.let { bytes -> runCatching { JSONObject(String(bytes)) }.getOrNull()?.let(::onMsg) }
            RelayPaths.ERR -> down(String(data))
        }
    }

    private fun down(why: String) {
        authed = false
        pending.clear()
        queued.clear()
        if (authFailed) return
        status = why
        main.removeCallbacks(retry)
        if (wanted) main.postDelayed(retry, 8000L) // Bluetooth is slow; don't flood the phone
    }

    private fun send(o: JSONObject, cb: ((JSONObject) -> Unit)? = null): Int {
        val id = nextId++
        o.put("id", id)
        cb?.let { pending[id] = it }
        if (authed) Link.send(ctx, RelayPaths.WS, o.toString().toByteArray()) { main.post { down("Sin conexión con el móvil") } }
        else queued += o
        return id
    }

    private fun onMsg(m: JSONObject) {
        when (m.optString("type")) {
            // never retry a rejected token: repeated bad logins get the phone's IP banned by http.ip_ban
            "auth_invalid" -> {
                authFailed = true
                status = "Token inválido (cámbialo en el móvil)"
            }
            "auth_ok" -> {
                status = ""
                authed = true
                main.removeCallbacks(retry)
                val waiting = queued.toList()
                queued.clear()
                waiting.forEach { o -> Link.send(ctx, RelayPaths.WS, o.toString().toByteArray()) {} }
                send(JSONObject().put("type", "lovelace/config").put("url_path", HaConfig.dashboard)) { r ->
                    r.optJSONObject("result")?.let { dash = Dash.parse(it) } ?: run { status = "Sin panel Inicio" }
                    subscribe()
                }
            }
            "result" -> pending.remove(m.optInt("id"))?.invoke(m)
            "event" -> if (m.optInt("id") == subId) apply(m.getJSONObject("event"))
        }
    }

    private fun subscribe() {
        val ids = (dash?.all.orEmpty() + HaConfig.servers.flatMap { it.ids } + HaConfig.extra + HaConfig.ring.map { it.id }).toSet()
        subId = send(JSONObject().put("type", "subscribe_entities").put("entity_ids", JSONArray(ids.toList())))
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

    fun call(domain: String, service: String, id: String) {
        send(JSONObject().put("type", "call_service").put("domain", domain).put("service", service).put("target", JSONObject().put("entity_id", id)))
    }

    /** A call that waits for HA's answer (the arm service blocks until the panel replies: tens of seconds). */
    private suspend fun service(domain: String, service: String, id: String): Boolean {
        val r = withTimeoutOrNull(150_000) {
            suspendCancellableCoroutine<JSONObject> { c ->
                send(JSONObject().put("type", "call_service").put("domain", domain).put("service", service)
                    .put("target", JSONObject().put("entity_id", id))) { c.resume(it) }
            }
        } ?: run { lastError = "sin respuesta"; return false }
        if (!r.optBoolean("success")) lastError = r.optJSONObject("error")?.optString("message") ?: "falló"
        return r.optBoolean("success")
    }

    /** The panel's state from the live subscription, waiting out "arming" and a slow first load. */
    private suspend fun alarmState(): Ent? {
        repeat(10) {
            val e = states[HaConfig.alarm]
            if (e != null && e.state != "arming") return e
            delay(3000)
        }
        return states[HaConfig.alarm]
    }

    /** For the prompt: makes sure the panel's state is in before it picks arm or disarm. */
    suspend fun refreshAlarm() { alarmState() }

    suspend fun disarmAlarm() {
        alarmBusy = true
        alarmStatus = "Desarmando…"
        try {
            if (!service("alarm_control_panel", "alarm_disarm", HaConfig.alarm)) { alarmStatus = "Error: $lastError"; return }
            delay(1500) // the new state follows the service's answer
            val fin = alarmState()?.state
            alarmStatus = if (fin == "disarmed") "Alarma desarmada" else "Estado: ${fin ?: "?"}"
        } finally {
            alarmBusy = false
        }
    }

    /**
     * Full arm (alarm_arm_away = "total"). If Verisure rejects it because zones are open, force-arm, which arms
     * total skipping those zones ("partial"); the zones come from the rejected attempt's attributes.
     */
    suspend fun armAlarm() {
        alarmBusy = true
        alarmStatus = "Armando total…"
        try {
            val s0 = alarmState()?.state ?: run { alarmStatus = "Sin conexión con HA"; return }
            if (s0.startsWith("armed")) { alarmStatus = "Ya está armada"; return }
            if (!service("alarm_control_panel", "alarm_arm_away", HaConfig.alarm)) { alarmStatus = "Error: $lastError"; return }
            delay(1500)
            val after = alarmState() ?: run { alarmStatus = "Sin conexión con HA"; return }
            when {
                after.state == "armed_away" -> alarmStatus = "Alarma TOTAL armada"
                after.attrs.optBoolean("arm_exception_active") -> {
                    val zones = after.attrs.optJSONArray("arm_exceptions")?.let { a -> (0 until a.length()).map(a::optString) }.orEmpty().joinToString(", ")
                    if (!after.attrs.optBoolean("force_arm_available")) { alarmStatus = "No se puede forzar.\nAbiertas: $zones"; return }
                    alarmStatus = "Abiertas: $zones\nArmando parcial…"
                    if (!service("verisure_owa", "force_arm", HaConfig.alarm)) { alarmStatus = "Error: $lastError"; return }
                    delay(1500)
                    val fin = alarmState()?.state
                    alarmStatus = if (fin == "armed_away") "Alarma PARCIAL armada\nSin: $zones" else "Estado: ${fin ?: "?"}"
                }
                else -> alarmStatus = "Estado: ${after.state}"
            }
        } finally {
            alarmBusy = false
        }
    }

    companion object {
        /** Applies a subscribe_entities "c" diff: {"+": {s, a}, "-": {a: [keys]}}. */
        fun merge(old: Ent, d: JSONObject): Ent {
            val attrs = JSONObject(old.attrs.toString())
            val plus = d.optJSONObject("+")
            plus?.optJSONObject("a")?.let { p -> for (k in p.keys()) attrs.put(k, p.get(k)) }
            d.optJSONObject("-")?.optJSONArray("a")?.let { r -> for (i in 0 until r.length()) attrs.remove(r.getString(i)) }
            val s = plus?.optString("s").orEmpty()
            return Ent(old.id, s.ifEmpty { old.state }, attrs)
        }
    }
}
