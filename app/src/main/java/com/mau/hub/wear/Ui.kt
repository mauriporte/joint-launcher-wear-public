package com.mau.hub.wear

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.content.ComponentName
import androidx.compose.ui.platform.LocalContext
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import org.json.JSONObject

// Joint Launcher's palette (joint-launcher-redesign Ui.kt)
val Bg = Color(0xFF050505)
val TextSoft = Color(0xFFC9CBD3)
val Muted = Color(0xFF9A9DA8)
val Card = Color(0xFF151515)

/** The player to show: the first one playing, else the first paused one, else none. */
fun activePlayer(players: List<String>, states: Map<String, Ent>): Ent? =
    players.mapNotNull { states[it] }.let { list -> list.firstOrNull { it.state == "playing" } ?: list.firstOrNull { it.state == "paused" } }

@Composable
fun App(ha: Ha, route: String? = null, routeShown: () -> Unit = {}) {
    val nav = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(nav, startDestination = "home", modifier = Modifier.background(Bg)) {
        composable("home") { Home(ha) { nav.navigate(it) } }
        composable("playing") { NowPlaying(ha) }
        composable("notifs") { NotifList() }
    }
    // opened from the Accesos tile: go there on top of home, so back returns to home
    LaunchedEffect(route) {
        if (route != null) {
            nav.popBackStack("home", inclusive = false)
            nav.navigate(route)
            routeShown()
        }
    }
}

/** Three full screens, swiped (or crown-turned) up/down: flip clock, player, notifications. */
@Composable
fun Home(ha: Ha, go: (String) -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var turned by remember { mutableFloatStateOf(0f) }
    VerticalPager(
        pager,
        Modifier.fillMaxSize().background(Bg)
            .onRotaryScrollEvent {
                // one page per firm turn of the crown
                turned += it.verticalScrollPixels
                if (abs(turned) > 60 && !pager.isScrollInProgress) {
                    val next = (pager.currentPage + if (turned > 0) 1 else -1).coerceIn(0, 2)
                    turned = 0f
                    scope.launch { pager.animateScrollToPage(next) }
                }
                true
            }
            .focusRequester(focus).focusable(),
    ) { page ->
        when (page) {
            0 -> ClockPage()
            1 -> PlayerPage(ha, go)
            else -> NotifsPage { go("notifs") }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Joint Launcher's flip clock on its own, as big as the round screen allows. */
@Composable
fun ClockPage() = FlipClock(Modifier.fillMaxSize(), widthShare = 0.42f)

/** Two flip cards side by side, each [widthShare] of the canvas width (capped by its height), centered. */
@Composable
fun FlipClock(modifier: Modifier, widthShare: Float) {
    val hours = remember { FlipCard() }
    val minutes = remember { FlipCard() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val t = java.time.LocalTime.now()
            hours.set("%02d".format(t.hour), now, animate = true)
            minutes.set("%02d".format(t.minute), now, animate = true)
            frame = now
            // ~60 fps while a card is turning, otherwise wake just after the next minute
            delay(if (hours.flipping(now) || minutes.flipping(now)) 16L else 60_000 - now % 60_000 + 20)
        }
    }
    Canvas(modifier) {
        val now = frame // read so every tick redraws
        val size = minOf(this.size.width * widthShare, this.size.height)
        val gap = size / 11
        val left = (this.size.width - 2 * size - gap) / 2
        val top = (this.size.height - size) / 2
        drawIntoCanvas {
            hours.draw(it.nativeCanvas, android.graphics.RectF(left, top, left + size, top + size), now)
            minutes.draw(it.nativeCanvas, android.graphics.RectF(left + size + gap, top, left + 2 * size + gap, top + size), now)
        }
    }
}

@Composable
fun PlayerPage(ha: Ha, go: (String) -> Unit) {
    val player = activePlayer(Config.players, ha.states)
    val ctx = LocalContext.current
    // a new song (or stop) refreshes the watch face's Sonando line now instead of within 5 minutes
    LaunchedEffect(player?.id, player?.state, player?.attrs?.optString("media_title")) {
        ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, SonandoSource::class.java)).requestUpdateAll()
    }
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.ic_launcher), contentDescription = "Joint Launcher", modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(24.dp))
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // Joint Remote and MauHomeAssist on either side of the clock, same as the watch face
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SettingsRemote, "Mando", tint = Accent, modifier = Modifier.size(30.dp).clip(CircleShape).clickable { launch(ctx, REMOTE) }.padding(3.dp))
            FlipClock(Modifier.padding(horizontal = 4.dp).width(116.dp).height(52.dp), widthShare = 0.48f)
            Icon(Icons.Filled.Home, "MauHomeAssist", tint = Color(0xFF03A9F4), modifier = Modifier.size(30.dp).clip(CircleShape).clickable { launch(ctx, MAUHA) }.padding(3.dp))
        }
        if (ha.status.isNotEmpty()) Text(ha.status, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        PlayingCard(ha, player, Modifier.fillMaxWidth(0.82f)) { go("playing") }
        if (Config.tv.isNotBlank()) {
            TvButtons(ha)
        }
    }
    }
}

/** Count and the newest notification; a tap opens the full list. */
@Composable
fun NotifsPage(open: () -> Unit) {
    val latest = Notifs.list.firstOrNull()
    Column(
        Modifier.fillMaxSize().clickable(onClick = open).padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Notifications, null, tint = Accent, modifier = Modifier.size(34.dp))
        Text(
            when {
                !Notifs.connected && Notifs.list.isEmpty() -> "Sin acceso a notificaciones"
                Notifs.list.isEmpty() -> "Sin notificaciones"
                else -> "${Notifs.list.size} notificaciones"
            },
            color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        if (latest != null) {
            Spacer(Modifier.height(8.dp))
            Text(latest.title.ifBlank { latest.app }, color = TextSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(latest.text, color = Muted, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NotifList() {
    val list = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(list) }) {
        ScalingLazyColumn(Modifier.fillMaxSize().background(Bg), state = list) {
            item { ListHeader { Text("Notificaciones", color = Accent) } }
            if (Notifs.list.isEmpty()) item { Text("Nada nuevo", color = Muted) }
            items(Notifs.list.size) { i ->
                val n = Notifs.list[i]
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card)
                        .clickable { runCatching { n.open?.send() } }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(n.app, color = Accent, fontSize = 11.sp, maxLines = 1)
                    if (n.title.isNotBlank()) Text(n.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(n.text, color = TextSoft, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PlayingCard(ha: Ha, player: Ent?, modifier: Modifier = Modifier.fillMaxWidth(), open: () -> Unit) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(30.dp)).background(Card)
            .clickable(enabled = player != null, onClick = open).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(ha, player, Modifier.size(38.dp).clip(CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(player?.attrs?.optString("media_title")?.ifBlank { null } ?: "Nada sonando", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(player?.let { subtitle(it) } ?: "Spotify · TV", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Artist, else the app or player name ("Netflix", "Spotify"). */
fun subtitle(e: Ent): String =
    e.attrs.optString("media_artist").ifBlank { e.attrs.optString("app_name") }.ifBlank { e.name }

@Composable
fun Art(ha: Ha, player: Ent?, modifier: Modifier) {
    val src = player?.attrs?.optString("entity_picture").orEmpty()
    var bmp by remember(src) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(src) { if (src.isNotBlank()) bmp = ha.image(src) }
    Box(modifier.background(Color(0xFF222222)), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Filled.MusicNote, null, tint = Accent)
    }
}

@Composable
fun NowPlaying(ha: Ha) {
    val player = activePlayer(Config.players, ha.states)
    Box(Modifier.fillMaxSize().background(Bg)) {
        Art(ha, player, Modifier.fillMaxSize())
        // darken the art so the controls read over any cover
        Box(Modifier.fillMaxSize().background(Color(0xB3050505)))
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(player?.attrs?.optString("media_title")?.ifBlank { null } ?: "Nada sonando", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            Text(player?.let { subtitle(it) }.orEmpty(), color = TextSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            if (player != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Round(Icons.Filled.SkipPrevious, "Anterior") { ha.call("media_player", "media_previous_track", player.id) }
                    Round(if (player.state == "playing") Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pausa", big = true) { ha.call("media_player", "media_play_pause", player.id) }
                    Round(Icons.Filled.SkipNext, "Siguiente") { ha.call("media_player", "media_next_track", player.id) }
                }
                Row {
                    Round(Icons.Filled.VolumeDown, "Bajar volumen") { ha.call("media_player", "volume_down", player.id) }
                    Round(Icons.Filled.VolumeUp, "Subir volumen") { ha.call("media_player", "volume_up", player.id) }
                }
            }
        }
    }
}

@Composable
fun TvButtons(ha: Ha) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (Config.tvRemote.isNotBlank()) {
            Round(Icons.Filled.Home, "Inicio") { ha.call("remote", "send_command", Config.tvRemote, JSONObject().put("command", "HOME")) }
            Round(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") { ha.call("remote", "send_command", Config.tvRemote, JSONObject().put("command", "BACK")) }
        }
        Round(Icons.Filled.PlayArrow, "Play/pausa") { ha.call("media_player", "media_play_pause", Config.tv) }
        Round(Icons.Filled.VolumeDown, "Bajar volumen") { ha.call("media_player", "volume_down", Config.tv) }
        Round(Icons.Filled.VolumeUp, "Subir volumen") { ha.call("media_player", "volume_up", Config.tv) }
    }
}

/** Bare icon button (A.R.T. Tablet's style: no filled circle), with a tick on press. */
@Composable
fun Round(icon: ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val size = if (big) 56.dp else 32.dp // five small ones fit the round screen's lower chord
    Box(
        Modifier.size(size).clip(CircleShape).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (big) Accent else TextSoft, modifier = Modifier.size(if (big) 40.dp else 24.dp))
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxSize().background(Bg).padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSoft, textAlign = TextAlign.Center)
    }
}
