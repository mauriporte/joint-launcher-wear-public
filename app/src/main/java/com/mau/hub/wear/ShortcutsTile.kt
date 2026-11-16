package com.mau.hub.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream

/** One shortcut: an app screen to open, by package/activity and an optional "route" extra. */
class Shortcut(val label: String, val icon: ImageVector, val tint: Color, val pkg: String, val cls: String, val route: String? = null)

private const val MAU = "io.mau.watch"
private const val MAU_MAIN = "io.mau.watch.MainActivity"

/** Joint Remote's watch screen (the A.R.T. Remote app): straight to the remote when a device is already picked. */
val REMOTE = "com.mau.remote" to "com.mau.remote.wear.WearActivity"
val MAUHA = MAU to MAU_MAIN

/** Opens another app's screen; a short note instead of a crash when it isn't installed. */
fun launch(ctx: android.content.Context, app: Pair<String, String>) {
    runCatching { ctx.startActivity(android.content.Intent().setClassName(app.first, app.second).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { android.widget.Toast.makeText(ctx, "No está instalada", android.widget.Toast.LENGTH_SHORT).show() }
}

/** Clockwise from 12: MauHomeAssist's screens, Spotify's watch app and Joint Remote. */
val SHORTCUTS get() = listOf(
    Shortcut("Chats", Icons.AutoMirrored.Filled.Chat, Color(0xFF25D366), MAU, MAU_MAIN, "chats"),
    Shortcut("Favoritos", Icons.Filled.Star, Color(0xFFFFC107), MAU, MAU_MAIN, "fav"),
    Shortcut("Áreas", Icons.Filled.Home, Color(0xFF4CAF50), MAU, MAU_MAIN, "list/areas"),
    Shortcut("Menús", Icons.Filled.Apps, Color(0xFF03A9F4), MAU, MAU_MAIN, "list/menus"),
    Shortcut("Spotify", Icons.Filled.LibraryMusic, Color(0xFF1DB954), "com.spotify.music", "com.spotify.wear.main.MainActivity"),
    Shortcut("Resúmenes", Icons.Filled.DonutLarge, Color(0xFF9C27B0), MAU, MAU_MAIN, "res"),
    Shortcut("Cámaras", Icons.Filled.Videocam, Color(0xFFC9CBD3), MAU, MAU_MAIN, "cams"),
    Shortcut("Remote", Icons.Filled.SettingsRemote, Accent, REMOTE.first, REMOTE.second),
)

/**
 * Round tile of shortcuts: the Joint Launcher icon in the center opens the app; 8 buttons around it open
 * MauHomeAssist's screens, Spotify and Now Playing. Nothing is fetched, so it draws instantly.
 */
class ShortcutsTile : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        loadAccent(this)
        val root = LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand())
        SHORTCUTS.forEachIndexed { i, s ->
            val button = LayoutElementBuilders.Image.Builder().setResourceId("s$i").setWidth(dp(BUTTON_DP)).setHeight(dp(BUTTON_DP))
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open(s, "s$i")).build()).build()
            val arc = LayoutElementBuilders.Arc.Builder().setAnchorAngle(degrees(i * 45f))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
                .addContent(LayoutElementBuilders.ArcAdapter.Builder().setContent(button).setRotateContents(false).build()).build()
            root.addContent(
                LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand())
                    .setModifiers(ModifiersBuilders.Modifiers.Builder().setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(INSET_DP)).build()).build())
                    .addContent(arc).build()
            )
        }
        val home = Shortcut("Joint Launcher", Icons.Filled.Home, Accent, packageName, MainActivity::class.java.name)
        root.addContent(
            LayoutElementBuilders.Image.Builder().setResourceId("center").setWidth(dp(CENTER_DP)).setHeight(dp(CENTER_DP))
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open(home, "center")).build()).build()
        )
        return now(
            TileBuilders.Tile.Builder().setResourcesVersion(resVersion())
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root.build())).build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        loadAccent(this)
        val res = ResourceBuilders.Resources.Builder().setVersion(resVersion())
        SHORTCUTS.forEachIndexed { i, s -> res.addIdToImageMapping("s$i", png(button(s.icon, Card, s.tint, BUTTON_PX))) }
        val logo = ContextCompat.getDrawable(this, R.drawable.ic_launcher)!!.toBitmap(CENTER_PX, CENTER_PX)
        res.addIdToImageMapping("center", png(logo))
        return now(res.build())
    }

    /** The accent is in the version, so a new colour redraws the Remote button instead of reusing the cached one. */
    private fun resVersion() = "$RES_VERSION-" + Integer.toHexString(Accent.toArgb())

    private fun <T> now(v: T): ListenableFuture<T> = CallbackToFutureAdapter.getFuture { it.set(v); "shortcuts" }

    private fun open(s: Shortcut, id: String): ModifiersBuilders.Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder().setPackageName(s.pkg).setClassName(s.cls)
        s.route?.let { activity.addKeyToExtraMapping("route", ActionBuilders.AndroidStringExtra.Builder().setValue(it).build()) }
        return ModifiersBuilders.Clickable.Builder().setId(id)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()).build()
    }

    companion object {
        private const val RES_VERSION = "shortcuts-2" // bump when icons/colors change so the watch re-fetches them
        private const val BUTTON_DP = 44f
        private const val CENTER_DP = 72f
        private const val INSET_DP = 8f
        private const val BUTTON_PX = 96
        private const val CENTER_PX = 160

        /** A filled circle with the icon centered (same drawing as MauHomeAssist's tile). */
        fun button(icon: ImageVector, bg: Color, fg: Color, px: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg.toArgb() }
            c.drawCircle(px / 2f, px / 2f, px / 2f, p)
            val iconPx = px * 0.52f
            c.translate((px - iconPx) / 2, (px - iconPx) / 2)
            c.scale(iconPx / icon.viewportWidth, iconPx / icon.viewportHeight)
            p.color = fg.toArgb()
            draw(icon.root, c, p)
            return bmp
        }

        /** A material icon's paths at [left],[top], [size] px square, in [color] (the watch face's buttons). */
        fun drawIcon(c: Canvas, icon: ImageVector, left: Float, top: Float, size: Float, color: Int) {
            c.save()
            c.translate(left, top)
            c.scale(size / icon.viewportWidth, size / icon.viewportHeight)
            draw(icon.root, c, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            c.restore()
        }

        private fun draw(g: VectorGroup, c: Canvas, p: Paint) {
            for (n in g) when (n) {
                is VectorPath -> {
                    val path = PathParser().addPathNodes(n.pathData).toPath().asAndroidPath()
                    path.fillType = if (n.pathFillType == PathFillType.EvenOdd) android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                    c.drawPath(path, p)
                }
                is VectorGroup -> draw(n, c, p)
            }
        }

        private fun png(b: Bitmap): ResourceBuilders.ImageResource {
            val bytes = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            return ResourceBuilders.ImageResource.Builder().setInlineResource(
                ResourceBuilders.InlineImageResource.Builder().setData(bytes).setWidthPx(b.width).setHeightPx(b.height)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED) // undefined = encoded image, decoded by the renderer
                    .build()
            ).build()
        }
    }
}
