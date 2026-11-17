package com.mau.hub.wear

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Home-specific addresses and entity ids, from a config.json pushed over adb or else assets/config.json (git-ignored; see config.example.json). */
object Config {
    var homeUrl = ""; private set
    var externalUrl = ""; private set
    /** media_player entities shown under Now Playing, first playing one wins (Spotify, the TV…). */
    var players: List<String> = emptyList(); private set
    /** The TV's media_player (play/pause, volume) and remote (Home/Back keys); blank = not configured. */
    var tv = ""; private set
    var tvRemote = ""; private set
    /** The shared master overlay on .30 (LAN URL), read for the A.R.T. accent (blank = always the orange). */
    var master = ""; private set
    var loaded = false; private set

    fun load(ctx: Context): Boolean {
        if (loaded) return true
        importPushed(ctx)
        // a config.json pushed over adb wins over the one built into the APK
        val pushed = File(ctx.filesDir, "config.json").takeIf { it.isFile }?.readText()
        val j = runCatching { JSONObject(pushed ?: ctx.assets.open("config.json").bufferedReader().use { it.readText() }) }.getOrNull() ?: return false
        apply(j)
        return true
    }

    fun apply(j: JSONObject) {
        homeUrl = j.optString("homeUrl")
        externalUrl = j.optString("externalUrl")
        players = j.optJSONArray("players").strings()
        tv = j.optString("tv")
        tvRemote = j.optString("tvRemote")
        master = j.optString("master")
        loaded = true
    }

    /** Every entity the app subscribes to. */
    val ids get() = (players + tv + tvRemote).filter { it.isNotBlank() }.distinct()

    /** Home address first (an override in files/url wins), then the remote one; blanks dropped. */
    fun urls(override: String?) = listOf(override ?: homeUrl, externalUrl).filter { it.isNotBlank() }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}
