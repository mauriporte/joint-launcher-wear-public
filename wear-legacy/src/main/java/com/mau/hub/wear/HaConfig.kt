package com.mau.hub.wear

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Server(val name: String, val cpu: String, val ram: String, val disk: String, val temp: String?) {
    val ids get() = listOfNotNull(cpu, ram, disk, temp)
}

/** One button of the 8-button ring (MauHomeAssist's tile). [route]: open that screen instead of toggling. */
class RingButton(val id: String, val icon: String, val color: String, val route: String? = null, val solidOn: Boolean = false)

/** MauHomeAssist's assets/config.json (shared through the build): dashboard, alarm, weather, away, servers, ring. */
object HaConfig {
    var dashboard = "lovelace"; private set
    var alarm = ""; private set
    var weather = ""; private set
    var away = ""; private set
    var servers: List<Server> = emptyList(); private set
    var ring: List<RingButton> = emptyList(); private set
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        // release builds: a config.json pushed with adb to /sdcard/Android/data/<package>/files moves into private storage
        ctx.getExternalFilesDir(null)?.let { File(it, "config.json") }?.takeIf { it.isFile }?.let { f ->
            runCatching { f.copyTo(File(ctx.filesDir, "config.json"), overwrite = true) }
            f.delete()
        }
        val pushed = File(ctx.filesDir, "config.json").takeIf { it.isFile }?.readText()
        val j = runCatching { JSONObject(pushed ?: ctx.assets.open("config.json").bufferedReader().use { it.readText() }) }.getOrNull() ?: return
        dashboard = j.optString("dashboard", "lovelace")
        alarm = j.optString("alarm")
        weather = j.optString("weather")
        away = j.optString("away")
        servers = j.optJSONArray("servers").objects().map {
            Server(it.optString("name"), it.optString("cpu"), it.optString("ram"), it.optString("disk"), it.str("temp"))
        }
        ring = j.optJSONArray("ring").objects().map {
            RingButton(it.optString("id"), it.optString("icon"), it.optString("color"), it.str("route"), it.optBoolean("solidOn"))
        }
        loaded = true
    }

    val extra get() = listOf(alarm, weather, away).filter { it.isNotBlank() }

    /** optString turns JSON null into "null"; this gives null for missing / null / blank. */
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
