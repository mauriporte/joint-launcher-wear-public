package com.mau.hub.wear

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.wear.tiles.TileService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

val ORANGE = Color(0xFFE8650A)

/** The A.R.T. accent; call [loadAccent] before drawing (app, tile, watch face). */
var Accent = ORANGE
    private set

/** files/accent ("#RRGGBB", this watch only, pushed over adb) > the master's shared "accent" (cached in files/accent_shared) > orange. */
fun loadAccent(ctx: Context) {
    Accent = readHex(ctx, "accent") ?: readHex(ctx, "accent_shared") ?: ORANGE
}

fun hexColor(s: String): Color? = s.takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) }?.substring(1)?.toLong(16)?.let { Color(0xFF000000 or it) }

private fun readHex(ctx: Context, name: String) = File(ctx.filesDir, name).takeIf { it.exists() }?.readText()?.trim()?.let(::hexColor)

/** Re-reads the master on .30 (blocking; off the main thread). Unreachable keeps the cache; a master without "accent" clears it. */
fun refreshAccent(ctx: Context) {
    val token = File(ctx.filesDir, "token").takeIf { it.exists() }?.readText()?.trim().orEmpty()
    if (Config.master.isBlank() || token.isEmpty()) return
    // shortcut: LAN address only, so away from home the cached accent stays; add the Access URL if that matters
    val accent = runCatching {
        OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(Config.master).header("Authorization", "Bearer $token").build()).execute().use {
                when {
                    it.code == 404 -> ""
                    it.isSuccessful -> JSONObject(it.body!!.string()).optString("accent").takeIf { a -> hexColor(a) != null }.orEmpty()
                    else -> null
                }
            }
    }.getOrNull() ?: return
    val f = File(ctx.filesDir, "accent_shared")
    if ((f.takeIf { it.exists() }?.readText() ?: "") == accent) return
    if (accent.isEmpty()) f.delete() else f.writeText(accent)
    loadAccent(ctx)
    TileService.getUpdater(ctx).requestUpdate(ShortcutsTile::class.java)
}
