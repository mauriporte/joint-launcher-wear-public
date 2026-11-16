package com.mau.hub.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import android.graphics.Bitmap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * "Sonando" for the watch face: what Spotify or the TV is playing, from HA (empty when nothing plays).
 * Refreshed every 5 minutes by the system, and right away whenever the app sees a new song.
 */
class SonandoSource : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Config.load(this)
        val states = withContext(Dispatchers.IO) { Config.players.mapNotNull { HaRest.state(this@SonandoSource, it) } }
        val p = activePlayer(Config.players, states.associateBy { it.id }) ?: return NoDataComplicationData()
        return build(request.complicationType, p.attrs.optString("media_title").ifBlank { subtitle(p) }, p.attrs.optString("media_artist"))
    }

    override fun getPreviewData(type: ComplicationType) = build(type, "Canción", "Artista")

    private fun build(type: ComplicationType, title: String, artist: String): ComplicationData {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("route", "playing").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val line = PlainComplicationText.Builder("♪ " + listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · ")).build()
        return if (type == ComplicationType.SHORT_TEXT)
            ShortTextComplicationData.Builder(PlainComplicationText.Builder(title).build(), line).setTapAction(open).build()
        else LongTextComplicationData.Builder(line, line).setTapAction(open).build()
    }
}

/** One-shot REST reads for the watch face's data source: same token and addresses as the app, home first then the tunnel. */
object HaRest {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    fun state(ctx: Context, id: String): Ent? {
        val j = request(ctx, "/api/states/$id") { it.body?.string() }?.let(::JSONObject) ?: return null
        return Ent(id, j.optString("state"), j.optJSONObject("attributes") ?: JSONObject())
    }

    /** A service call from the watch face's buttons, e.g. ("media_player", "volume_up", {"entity_id": …}). */
    fun call(ctx: Context, domain: String, service: String, data: JSONObject) =
        request(ctx, "/api/services/$domain/$service", data.toString()) { true } != null

    /** Album art / poster: entity_picture is a path on HA or a full URL. */
    fun image(ctx: Context, src: String): Bitmap? =
        if (src.startsWith("http")) runCatching {
            http.newCall(Request.Builder().url(src).build()).execute().use { r -> r.body?.bytes()?.let { Ha.decodeScaled(it, 160) } }
        }.getOrNull()
        else request(ctx, src) { r -> r.body?.bytes()?.let { Ha.decodeScaled(it, 160) } }

    /** Home address first (the one that worked last), then the other; null when none answers. */
    private fun <T> request(ctx: Context, path: String, post: String? = null, read: (Response) -> T?): T? {
        val token = File(ctx.filesDir, "token").takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (token.isEmpty()) return null
        val urls = Config.urls(File(ctx.filesDir, "url").takeIf { it.exists() }?.readText()?.trim())
        val last = File(ctx.filesDir, MainActivity.LAST_URL)
        val first = last.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()?.coerceIn(0, urls.lastIndex) ?: 0
        for (i in urls.indices) {
            val base = urls[(first + i) % urls.size]
            val b = Request.Builder().url(base + path).header("Authorization", "Bearer $token")
            if (post != null) b.post(post.toRequestBody("application/json".toMediaType()))
            val v = runCatching { http.newCall(b.build()).execute().use { if (it.isSuccessful) read(it) else null } }.getOrNull()
            if (v != null) return v
        }
        return null
    }
}
