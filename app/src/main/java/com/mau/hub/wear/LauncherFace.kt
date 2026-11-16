package com.mau.hub.wear

import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.view.SurfaceHolder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.ZonedDateTime

/**
 * Joint Launcher as the watch face: the app's middle page drawn live (icon on top, small flip clock, Now Playing pill,
 * TV buttons), and it works in place: the buttons call HA, the pill opens the full player, the clock opens the app.
 */
class LauncherFace : WatchFaceService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun createUserStyleSchema() = UserStyleSchema(emptyList())

    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository) =
        ComplicationSlotsManager(emptyList(), currentUserStyleRepository)

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        Config.load(this)
        val r = LauncherRenderer(this, scope, surfaceHolder, watchState, currentUserStyleRepository)
        // what's playing: on every wake-up, then every 15 s while the face is lit
        scope.launch { watchState.isVisible.collect { if (it == true) r.refresh() } }
        scope.launch {
            while (isActive) {
                delay(15_000)
                if (watchState.isVisible.value == true && watchState.isAmbient.value != true) r.refresh()
            }
        }
        return WatchFace(WatchFaceType.DIGITAL, r).setTapListener(object : WatchFace.TapListener {
            override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
                if (tapType == TapType.UP) r.tap(tapEvent.xPos.toFloat(), tapEvent.yPos.toFloat())
            }
        })
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class LauncherRenderer(
    private val ctx: Context,
    private val scope: CoroutineScope,
    surfaceHolder: SurfaceHolder,
    private val watchState: WatchState,
    repo: CurrentUserStyleRepository,
) : Renderer.CanvasRenderer2<LauncherRenderer.Assets>(surfaceHolder, repo, watchState, CanvasType.HARDWARE, 1000L, false) {
    class Assets : SharedAssets { override fun onDestroy() {} }

    override suspend fun createSharedAssets() = Assets()

    private var player: Ent? = null
    private var art: Bitmap? = null
    private var artSrc = ""

    private val hours = FlipCard()
    private val minutes = FlipCard()
    private val logo = ContextCompat.getDrawable(ctx, R.drawable.ic_launcher)!!
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A9DA8.toInt() }

    // layout as fractions of the screen, matching the app's middle page (208 dp wide)
    private val pillF = RectF(0.1375f, 0.442f, 0.8625f, 0.692f)
    private val clockF = RectF(0.20f, 0.154f, 0.80f, 0.404f)
    private val rowTop = 0.692f
    // the two small shortcuts on either side of the flip clock: Joint Remote (left), MauHomeAssist (right)
    private val small = 0.085f
    private val smallX = listOf(0.125f, 0.875f)
    private val button = 0.154f
    private val buttons: List<Pair<ImageVector, () -> Unit>>
        get() = buildList {
            if (Config.tvRemote.isNotBlank()) {
                add(Icons.Filled.Home to { key("HOME") })
                add(Icons.AutoMirrored.Filled.ArrowBack to { key("BACK") })
            }
            if (Config.tv.isNotBlank()) {
                add(Icons.Filled.PlayArrow to { tv("media_play_pause") })
                add(Icons.Filled.VolumeDown to { tv("volume_down") })
                add(Icons.Filled.VolumeUp to { tv("volume_up") })
            }
        }

    suspend fun refresh() {
        loadAccent(ctx)
        val states = withContext(Dispatchers.IO) { Config.players.mapNotNull { HaRest.state(ctx, it) } }
        player = activePlayer(Config.players, states.associateBy { it.id })
        val src = player?.attrs?.optString("entity_picture").orEmpty()
        if (src != artSrc) {
            artSrc = src
            art = if (src.isBlank()) null else withContext(Dispatchers.IO) { HaRest.image(ctx, src) }
        }
        invalidate()
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        val ambient = watchState.isAmbient.value == true
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        canvas.drawColor(Color.BLACK)
        val now = System.currentTimeMillis()
        for (f in listOf(hours, minutes)) f.dim(ambient)
        hours.set("%02d".format(zonedDateTime.hour), now, animate = !ambient)
        minutes.set("%02d".format(zonedDateTime.minute), now, animate = !ambient)

        // clock: two cards as tall as its band, centered
        val size = clockF.height() * h
        val gap = size / 11
        val left = (w - 2 * size - gap) / 2
        val top = clockF.top * h
        hours.draw(canvas, RectF(left, top, left + size, top + size), now)
        minutes.draw(canvas, RectF(left + size + gap, top, left + 2 * size + gap, top + size), now)

        val p = player
        title.textSize = w * 0.062f
        sub.textSize = w * 0.054f
        if (ambient) {
            // ambient: clock plus the song in dim text only (no art, buttons or bright areas)
            if (p != null) {
                sub.color = 0xFF777777.toInt()
                val line = TextUtils.ellipsize(songTitle(p), sub, w * 0.7f, TextUtils.TruncateAt.END).toString()
                canvas.drawText(line, (w - sub.measureText(line)) / 2, pillF.centerY() * h, sub)
                sub.color = 0xFF9A9DA8.toInt()
            }
        } else {
            val icon = w * 0.115f
            logo.setBounds(((w - icon) / 2).toInt(), (h * 0.02f).toInt(), ((w + icon) / 2).toInt(), (h * 0.02f + icon).toInt())
            logo.draw(canvas)
            drawPill(canvas, w, h, p)
            val bs = buttons
            val bw = button * w
            val spare = (w * 0.884f - bs.size * bw) / (bs.size + 1)
            bs.forEachIndexed { i, (vector, _) ->
                val v = if (vector == Icons.Filled.PlayArrow && Config.tv.isNotBlank() && player?.id == Config.tv && player?.state == "playing") Icons.Filled.Pause else vector
                val bx = w * 0.058f + spare * (i + 1) + bw * i
                val iconPx = bw * 0.75f
                ShortcutsTile.drawIcon(canvas, v, bx + (bw - iconPx) / 2, rowTop * h + (bw - iconPx) / 2, iconPx, 0xFFC9CBD3.toInt())
            }
            val s = small * w
            val sy = clockF.centerY() * h - s / 2
            ShortcutsTile.drawIcon(canvas, Icons.Filled.SettingsRemote, smallX[0] * w - s / 2, sy, s, Accent.toArgb())
            ShortcutsTile.drawIcon(canvas, Icons.Filled.Home, smallX[1] * w - s / 2, sy, s, 0xFF03A9F4.toInt())
        }
        if (hours.flipping(now) || minutes.flipping(now)) invalidate()
    }

    private fun drawPill(canvas: Canvas, w: Float, h: Float, p: Ent?) {
        val r = RectF(pillF.left * w, pillF.top * h, pillF.right * w, pillF.bottom * h)
        fill.color = 0xFF151515.toInt()
        fill.shader = null
        canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
        val d = w * 0.183f
        val cx = r.left + w * 0.038f + d / 2
        val cy = r.centerY()
        val a = art
        if (a != null) {
            val m = Matrix().apply {
                val s = d / minOf(a.width, a.height)
                setScale(s, s)
                postTranslate(cx - a.width * s / 2, cy - a.height * s / 2)
            }
            fill.shader = BitmapShader(a, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            canvas.drawCircle(cx, cy, d / 2, fill)
            fill.shader = null
        } else {
            fill.color = 0xFF222222.toInt()
            canvas.drawCircle(cx, cy, d / 2, fill)
            val n = d * 0.55f
            ShortcutsTile.drawIcon(canvas, Icons.Filled.MusicNote, cx - n / 2, cy - n / 2, n, Accent.toArgb())
        }
        val tx = cx + d / 2 + w * 0.036f
        val avail = r.right - tx - r.height() / 5
        val t = TextUtils.ellipsize(p?.let { songTitle(it) } ?: "Nada sonando", title, avail, TextUtils.TruncateAt.END).toString()
        val s = TextUtils.ellipsize(p?.let { subtitle(it) } ?: "Spotify · TV", sub, avail, TextUtils.TruncateAt.END).toString()
        canvas.drawText(t, tx, cy - w * 0.006f, title)
        canvas.drawText(s, tx, cy + sub.textSize, sub)
    }

    private fun songTitle(p: Ent) = p.attrs.optString("media_title").ifBlank { subtitle(p) }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {}

    fun tap(x: Float, y: Float) {
        val w = surfaceHolder.surfaceFrame.width().toFloat()
        val h = surfaceHolder.surfaceFrame.height().toFloat()
        val fx = x / w
        val fy = y / h
        when {
            pillF.contains(fx, fy) -> open(if (player != null) "playing" else null)
            fy in clockF.top..clockF.bottom && fx < 0.22f -> launch(ctx, REMOTE)
            fy in clockF.top..clockF.bottom && fx > 0.78f -> launch(ctx, MAUHA)
            fy < pillF.top -> open(null) // icon or clock
            fy in rowTop..rowTop + button -> {
                val bs = buttons
                val bw = button * w
                val spare = (w * 0.884f - bs.size * bw) / (bs.size + 1)
                bs.forEachIndexed { i, (_, act) ->
                    val bx = w * 0.058f + spare * (i + 1) + bw * i
                    if (x in bx - spare / 2..bx + bw + spare / 2) act()
                }
            }
        }
    }

    private fun open(route: String?) {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun key(command: String) = scope.launch(Dispatchers.IO) {
        HaRest.call(ctx, "remote", "send_command", JSONObject().put("entity_id", Config.tvRemote).put("command", command))
    }

    private fun tv(service: String) = scope.launch {
        withContext(Dispatchers.IO) { HaRest.call(ctx, "media_player", service, JSONObject().put("entity_id", Config.tv)) }
        delay(700) // let HA report the new state, then redraw
        refresh()
    }
}
