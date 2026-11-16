package com.mau.hub.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Light
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// MauHomeAssist's screens for the SW3's square 320px screen, in plain Compose (Wear Compose needs Android 7.1+).
// Look and logic follow io.mau.watch/Ui.kt; cameras, chats and the voice assistant aren't here yet (phase 2).

// HA dark theme palette
val HaBlue = Color(0xFF03A9F4)
val Amber = Color(0xFFFFC107)
val Violet = Color(0xFFAB47BC)
// SW3 only: pure black cards with a thin outline instead of HA's grey (the round build keeps HA's look)
val CardBg = Color.Black
val Outline = Color(0xFF3A3A3C)
val Muted = Color(0xFF9E9E9E)
val Ok = Color(0xFF4CAF50)
val Bad = Color(0xFFF44336)

private val ALARM_TEXT = mapOf(
    "disarmed" to "Desarmada", "armed_away" to "Armada (ausente)", "armed_home" to "Armada (casa)",
    "armed_night" to "Armada (noche)", "arming" to "Armando…", "pending" to "Pendiente", "triggered" to "¡Disparada!"
)
private val WEATHER_TEXT = mapOf(
    "sunny" to "Soleado", "clear-night" to "Despejado", "cloudy" to "Nublado", "partlycloudy" to "Parcialmente nublado",
    "rainy" to "Lluvia", "pouring" to "Lluvia fuerte", "fog" to "Niebla", "windy" to "Viento", "snowy" to "Nieve",
    "lightning" to "Tormenta", "lightning-rainy" to "Tormenta"
)
private val PRESS_DOMAINS = setOf("scene", "script", "button", "input_button", "camera")

/** Screens as a back stack of routes; a swipe right from the left edge goes back (AW 1.5 would close the whole app). */
@Composable
fun HaApp(ha: Ha, start: String, finish: () -> Unit) {
    var stack by remember(start) { mutableStateOf(listOf(start)) }
    val go: (String) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else finish() }
    BackHandler { back() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val route = stack.last()
        val parts = route.split('/')
        val d = ha.dash
        when {
            route == "home" -> Home(ha, go)
            route == "ring" -> Ring(ha, go)
            route == "res" -> Summaries(ha)
            d == null -> Loading(ha)
            route == "fav" -> Pages(ha, d.fav, Amber)
            route == "list/menus" -> GroupList(ha, "Menús", d.menus, HaBlue, { Icons.Filled.Dns }) { go("group/menus/$it") }
            route == "list/areas" -> GroupList(ha, "Áreas", d.areas, Ok, { Icons.Filled.Home }) {
                go(if (d.areas[it].subs.isEmpty()) "group/areas/$it" else "subs/$it")
            }
            parts[0] == "subs" -> d.areas.getOrNull(parts[1].toInt())?.let { area ->
                GroupList(ha, area.title, area.subs, Ok, { g -> groupIcon(ha, g) }) { j -> go("sub/${parts[1]}/$j") }
            }
            parts[0] == "sub" -> d.areas.getOrNull(parts[1].toInt())?.subs?.getOrNull(parts[2].toInt())?.let { Pages(ha, it, Ok) }
            parts[0] == "group" -> {
                val menus = parts[1] == "menus"
                (if (menus) d.menus else d.areas).getOrNull(parts[2].toInt())?.let { Pages(ha, it, if (menus) HaBlue else Ok) }
            }
        }
        // the edge strip that takes the back swipe
        Box(
            Modifier.fillMaxHeight().width(16.dp).pointerInput(stack) {
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx > 60.dp.toPx()) back() }) { _, d -> dx += d }
            },
        )
        ha.confirm?.let { ConfirmOverlay(ha, it) }
        if (ha.alarmPrompt) AlarmOverlay(ha)
    }
}

@Composable
fun Loading(ha: Ha) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(ha.status.ifEmpty { "Cargando…" }, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
}

@Composable
fun Home(ha: Ha, go: (String) -> Unit) {
    val d = ha.dash
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("MauHomeAssist", color = HaBlue, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center) }
        if (ha.status.isNotEmpty()) item { Text(ha.status, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item { SectionChip("Accesos", Icons.Filled.Star, Amber, "Los 8 botones y la alarma") { go("ring") } }
        item { SectionChip("Favoritos", Icons.Filled.Star, Amber, d?.let { "${shown(it.fav.ids).size} entidades" }) { go("fav") } }
        item { SectionChip("Menús", Icons.Filled.Apps, HaBlue, d?.let { "${it.menus.size} servidores" }) { go("list/menus") } }
        item { SectionChip("Áreas", Icons.Filled.Home, Ok, d?.let { "${it.areas.size} áreas" }) { go("list/areas") } }
        item { SectionChip("Resúmenes", Icons.Filled.DonutLarge, Violet, "Casa · ${HaConfig.servers.size} servidores") { go("res") } }
    }
}

@Composable
fun SectionChip(label: String, icon: ImageVector, tint: Color, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(CardBg).border(1.dp, Outline, RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GroupList(ha: Ha, title: String, groups: List<Group>, tint: Color, icon: (Group) -> ImageVector, open: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(title, color = tint, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center) }
        itemsIndexed(groups) { i, g ->
            val on = g.ids.count { ha.states[it]?.on == true }
            val sub = if (g.subs.isEmpty()) "${shown(g.ids).size} · $on encendidos" else "${g.subs.size} grupos · $on encendidos"
            SectionChip(g.title, icon(g), tint, sub) { open(i) }
        }
    }
}

/** What a section shows on the SW3: cameras (and their capture buttons) wait for phase 2. */
fun shown(ids: List<String>): List<String> = ids.filter { !it.startsWith("camera.") && !it.endsWith("_capture") }

/** Everyday controls first, then the confirmation-protected ones; three per page, like the Fossil. */
fun pagesOf(ids: List<String>): List<List<String>> {
    val (power, everyday) = shown(ids).partition(::guarded)
    return everyday.chunked(3) + power.chunked(3)
}

@Composable
fun Pages(ha: Ha, g: Group, tint: Color) {
    val pages = remember(g) { pagesOf(g.ids) }
    Paged(pages.size, tint) { i ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(g.title, color = tint, fontSize = 12.sp, maxLines = 1)
            pages[i].forEach { EntityTile(ha, it, g.guarded || guarded(it)) }
        }
    }
}

@Composable
fun Paged(count: Int, tint: Color, content: @Composable (Int) -> Unit) {
    val state = rememberPagerState { count }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state, Modifier.fillMaxSize()) { content(it) }
        if (count in 2..12) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) {
                val sel = it == state.currentPage
                Box(Modifier.size(if (sel) 6.dp else 5.dp).clip(CircleShape).background(if (sel) tint else Color(0xFF555555)))
            }
        } else if (count > 12) Text(
            "${state.currentPage + 1}/$count", color = Muted, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

/** Kill switches, server power and locks ask for confirmation, so a stray tap can't cut a machine off. */
fun guarded(id: String) = "kill_switch" in id || "smart_switch" in id || id == "switch.trixie" || id.startsWith("lock.")

fun confirmQuestion(e: Ent, name: String): String = when {
    e.domain == "lock" -> if (e.state == "locked") "¿Desbloquear $name?" else "¿Bloquear $name?"
    e.on -> "¿Apagar $name?"
    else -> "¿Encender $name?"
}

@Composable
fun EntityTile(ha: Ha, id: String, guard: Boolean) {
    val e = ha.states[id]
    val card = ha.dash?.cards?.get(id)
    val name = card?.name ?: e?.name ?: id.substringAfter('.')
    val base = colorOf(e)
    val active = e != null && base != Muted
    val c = if (active && e?.domain != "alarm_control_panel") haColor(card?.color) ?: base else base
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (active) c.copy(alpha = 0.16f) else CardBg)
            .border(1.dp, if (active) c.copy(alpha = 0.5f) else Outline, RoundedCornerShape(22.dp))
            .clickable {
                when {
                    e == null -> {}
                    e.domain == "alarm_control_panel" -> ha.alarmPrompt = true
                    guard -> ha.confirm = id
                    else -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); act(ha, e) }
                }
            }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(c.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(mdi(card?.icon) ?: mdi(e?.attrs?.optString("icon")) ?: iconOf(id), contentDescription = null, tint = c, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stateText(e), color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        if (guard) Icon(Icons.Filled.Lock, contentDescription = "Protegido", tint = Muted, modifier = Modifier.size(13.dp))
    }
}

/** The ✗ / ✓ pair both prompts use. */
@Composable
private fun YesNo(yesColor: Color, yesLabel: String, no: () -> Unit, yes: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Button(onClick = no, shape = CircleShape, colors = ButtonDefaults.buttonColors(backgroundColor = CardBg), border = BorderStroke(1.dp, Outline), modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = Color.White)
        }
        Button(onClick = yes, shape = CircleShape, colors = ButtonDefaults.buttonColors(backgroundColor = yesColor), modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Check, contentDescription = yesLabel, tint = Color.Black)
        }
    }
}

/** Full-screen ✗ / ✓ over the current screen. ✗ or back does nothing; ✓ acts on the entity's state at that moment. */
@Composable
fun ConfirmOverlay(ha: Ha, id: String) {
    val e = ha.states[id] ?: run { ha.confirm = null; return }
    val name = ha.dash?.cards?.get(id)?.name ?: e.name
    val haptic = LocalHapticFeedback.current
    BackHandler { ha.confirm = null }
    Column(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } },
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = Bad, modifier = Modifier.size(30.dp))
        Text(confirmQuestion(e, name), color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        YesNo(Bad, "Confirmar", { ha.confirm = null }) { ha.confirm = null; haptic.performHapticFeedback(HapticFeedbackType.LongPress); act(ha, e) }
    }
}

/**
 * Full-screen ALARMA prompt: waits for the panel's live state, then offers ✓ disarm when it's armed (or arming /
 * triggered), else ✓ arm total (partial if zones are open).
 */
@Composable
fun AlarmOverlay(ha: Ha) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ha.refreshAlarm(); checked = true }
    val state = ha.states[HaConfig.alarm]?.state
    val close = { ha.alarmPrompt = false; ha.alarmStatus = null }
    BackHandler { if (!ha.alarmBusy) close() }
    Column(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = Bad, modifier = Modifier.size(30.dp))
        Text("ALARMA", color = Bad, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val status = ha.alarmStatus
        val line = Modifier.padding(horizontal = 16.dp)
        when {
            ha.alarmBusy -> {
                CircularProgressIndicator(color = Bad, modifier = Modifier.size(28.dp))
                Text(status ?: "…", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = line)
            }
            !checked -> {
                CircularProgressIndicator(color = Bad, modifier = Modifier.size(28.dp))
                Text("Comprobando…", color = Color.White, fontSize = 13.sp)
            }
            status != null || state == null -> {
                Text(status ?: "Sin conexión con HA", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = line)
                Button(onClick = close, shape = CircleShape, colors = ButtonDefaults.buttonColors(backgroundColor = CardBg), border = BorderStroke(1.dp, Outline), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = "Cerrar", tint = Color.White)
                }
            }
            state != "disarmed" -> {
                Text("¿Desarmar la alarma?", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                YesNo(Ok, "Desarmar", close) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); scope.launch { ha.disarmAlarm() } }
            }
            else -> {
                Text("¿Armar la alarma?", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                YesNo(Bad, "Armar", close) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); scope.launch { ha.armAlarm() } }
            }
        }
    }
}

/**
 * MauHomeAssist's tile as a screen (AW 1.5 has no tiles): the M (★) in the center opens ALARMA, the 8 buttons from
 * config.json sit around it, lit when on; a button with a route opens that screen instead of toggling.
 */
@Composable
fun Ring(ha: Ha, go: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HaConfig.ring.forEachIndexed { i, b ->
            val s = ha.states[b.id]?.state
            val c = haColor(b.color) ?: hexColor(b.color) ?: HaBlue
            val icon = mdi(b.icon) ?: iconOf(b.id)
            val (bg, fg) = when {
                s == null || s == "unavailable" || s == "unknown" -> CardBg to Color(0xFF4A4A4A)
                b.id.startsWith("scene.") || s == "on" || (b.id.startsWith("alarm_control_panel.") && s != "disarmed") ->
                    if (b.solidOn) c to Color(0xFF424242) else c.copy(alpha = 0.35f).compositeOver(Color.Black) to c
                else -> CardBg to Muted
            }
            val a = Math.toRadians(i * 45.0 - 90.0) // one every 45°, from 12 o'clock
            Box(
                Modifier.offset((cos(a) * 66).dp, (sin(a) * 66).dp).size(42.dp).clip(CircleShape).background(bg)
                    .border(1.dp, if (bg == CardBg) Outline else Color.Transparent, CircleShape).clickable {
                    val r = b.route
                    when {
                        r == "alarm" -> ha.alarmPrompt = true
                        r != null -> go(if (r == "cams") "fav" else r) // cameras are phase 2
                        else -> ha.states[b.id]?.let { haptic.performHapticFeedback(HapticFeedbackType.LongPress); act(ha, it) }
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = b.id, tint = fg, modifier = Modifier.size(22.dp)) }
        }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(CardBg).border(1.dp, Outline, CircleShape).clickable { ha.alarmPrompt = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Star, contentDescription = "ALARMA", tint = Amber, modifier = Modifier.size(34.dp)) }
    }
}

@Composable
fun Summaries(ha: Ha) {
    val servers = HaConfig.servers
    Paged(servers.size + 1, Violet) { i -> if (i == 0) HomeSummary(ha) else GaugePage(ha, servers[i - 1]) }
}

@Composable
fun HomeSummary(ha: Ha) {
    val lights = ha.states.values.filter { it.domain == "light" }
    val on = lights.count { it.on }
    val w = ha.states[HaConfig.weather]
    val temp = w?.attrs?.optDouble("temperature")?.takeIf { !it.isNaN() }?.let { " ${it.roundToInt()}°" }.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text("Casa", color = Violet, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        SummaryRow(Icons.Filled.Shield, colorOf(ha.states[HaConfig.alarm]), "Alarma", stateText(ha.states[HaConfig.alarm]))
        SummaryRow(Icons.Filled.Lightbulb, if (on > 0) Amber else Muted, "Luces", "$on/${lights.size} encendidas")
        SummaryRow(Icons.Filled.WbSunny, HaBlue, "Tiempo", w?.let { (WEATHER_TEXT[it.state] ?: it.state) + temp } ?: "…")
        SummaryRow(Icons.AutoMirrored.Filled.DirectionsWalk, colorOf(ha.states[HaConfig.away]), "Modo ausente", stateText(ha.states[HaConfig.away]))
    }
}

@Composable
fun SummaryRow(icon: ImageVector, c: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(c.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun pct(e: Ent?): Float? = if (e?.unit == "%") e.state.toFloatOrNull()?.coerceIn(0f, 100f) else null

fun shortValue(e: Ent?): String =
    e?.state?.toDoubleOrNull()?.let { v -> v.roundToInt().toString() + if (e.unit == "%") "%" else " ${e.unit}" } ?: "—"

/** Three arcs (cpu, ram, disk); red past 85%. Temperature in the middle. */
@Composable
fun GaugePage(ha: Ha, s: Server) {
    val rings = listOf(Triple("cpu", ha.states[s.cpu], HaBlue), Triple("ram", ha.states[s.ram], Violet), Triple("disco", ha.states[s.disk], Amber))
    val temp = s.temp?.let { ha.states[it]?.state?.toDoubleOrNull() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val sw = 8.dp.toPx()
            rings.forEachIndexed { i, (_, e, c) ->
                val inset = i * 12.dp.toPx() + sw / 2
                val tl = Offset(inset, inset)
                val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
                drawArc(Color(0xFF2A2A2A), 120f, 300f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                pct(e)?.let { p -> drawArc(if (p >= 85f) Bad else c, 120f, 300f * p / 100f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round)) }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.name, color = Muted, fontSize = 11.sp)
            Text(temp?.let { "${it.roundToInt()}°" } ?: "—", color = if ((temp ?: 0.0) >= 80) Bad else Color.White, fontSize = 28.sp)
            Row {
                rings.forEachIndexed { i, (_, e, c) ->
                    if (i > 0) Text(" · ", color = Muted, fontSize = 10.sp)
                    Text(shortValue(e), color = c, fontSize = 10.sp)
                }
            }
            Text("cpu · ram · disco", color = Muted, fontSize = 9.sp)
        }
    }
}

/** A sub-menu's icon: its first entity's card/entity icon. */
fun groupIcon(ha: Ha, g: Group): ImageVector {
    val id = g.ids.firstOrNull() ?: return Icons.Filled.Home
    return mdi(ha.dash?.cards?.get(id)?.icon) ?: mdi(ha.states[id]?.attrs?.optString("icon")) ?: iconOf(id)
}

/** HA's named theme colors as used in tile cards. */
fun haColor(name: String?): Color? = when (name) {
    "primary", "accent" -> HaBlue
    "red" -> Color(0xFFF44336)
    "pink" -> Color(0xFFE91E63)
    "purple" -> Color(0xFF926BC7)
    "deep-purple" -> Color(0xFF6E41AB)
    "indigo" -> Color(0xFF3F51B5)
    "blue" -> Color(0xFF2196F3)
    "light-blue" -> Color(0xFF03A9F4)
    "cyan" -> Color(0xFF00BCD4)
    "teal" -> Color(0xFF009688)
    "green" -> Color(0xFF4CAF50)
    "light-green" -> Color(0xFF8BC34A)
    "lime" -> Color(0xFFCDDC39)
    "yellow" -> Color(0xFFFFEB3B)
    "amber" -> Amber
    "orange" -> Color(0xFFFF9800)
    "deep-orange" -> Color(0xFFFF6F22)
    "brown" -> Color(0xFF795548)
    "grey", "blue-grey" -> Color(0xFF9E9E9E)
    else -> null
}

/** "#RRGGBB" → Color, for ring colors that aren't HA theme names. */
fun hexColor(s: String): Color? = s.takeIf { it.startsWith("#") && it.length == 7 }
    ?.substring(1)?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** Bedside lamp (shade, stem, base): Material Icons has no nightstand lamp. */
val TableLamp: ImageVector by lazy {
    ImageVector.Builder("TableLamp", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 3f); horizontalLineTo(17f); lineTo(20f, 11f); horizontalLineTo(4f); close()
            moveTo(11f, 11f); horizontalLineTo(13f); verticalLineTo(18f); horizontalLineTo(11f); close()
            moveTo(7f, 18f); horizontalLineTo(17f); verticalLineTo(21f); horizontalLineTo(7f); close()
        }
    }.build()
}

/** The MDI icons used on the Inicio dashboard; anything unmapped falls back to a domain icon. */
fun mdi(name: String?): ImageVector? = when (name?.removePrefix("mdi:")) {
    "power", "power-standby" -> Icons.Filled.PowerSettingsNew
    "server", "server-network" -> Icons.Filled.Dns
    "raspberry-pi" -> Icons.Filled.Memory
    "desktop-tower", "desktop-classic" -> Icons.Filled.Computer
    "monitor", "monitor-multiple" -> Icons.Filled.Monitor
    "weather-night" -> Icons.Filled.Bedtime
    "movie-open", "movie" -> Icons.Filled.Movie
    "home-export-outline" -> Icons.AutoMirrored.Filled.ExitToApp
    "ceiling-light" -> Icons.Filled.Light
    "lamp-night" -> TableLamp
    "desk-lamp" -> Icons.Filled.Highlight
    "lightbulb", "lightbulb-group", "lamp", "floor-lamp" -> Icons.Filled.Lightbulb
    "music", "speaker" -> Icons.Filled.MusicNote
    "camera", "cctv" -> Icons.Filled.CameraAlt
    "television", "television-classic" -> Icons.Filled.Tv
    "gesture-tap-button" -> Icons.Filled.TouchApp
    "android" -> Icons.Filled.Android
    "docker" -> Icons.Filled.Inventory2
    "shield-home", "shield-lock" -> Icons.Filled.Shield
    else -> null
}

fun act(ha: Ha, e: Ent) {
    val d = e.domain
    when (d) {
        "light", "switch", "input_boolean", "fan", "cover", "automation" -> ha.call(d, "toggle", e.id)
        "scene", "script" -> ha.call(d, "turn_on", e.id)
        "button", "input_button" -> ha.call(d, "press", e.id)
        "lock" -> ha.call(d, if (e.state == "locked") "unlock" else "lock", e.id)
        "media_player" -> ha.call(d, "media_play_pause", e.id)
    }
}

fun colorOf(e: Ent?): Color = when {
    e == null || e.state == "unavailable" -> Muted
    e.domain == "alarm_control_panel" -> if (e.state == "disarmed") Ok else Bad
    e.domain in PRESS_DOMAINS -> HaBlue
    !e.on -> Muted
    e.domain == "light" -> Amber
    else -> HaBlue
}

fun stateText(e: Ent?): String = when {
    e == null -> "…"
    e.state == "unavailable" -> "No disponible"
    e.domain == "light" && e.on && e.attrs.has("brightness") -> "${(e.attrs.optDouble("brightness") / 2.55).roundToInt()}%"
    e.domain in setOf("scene", "script") -> "Activar"
    e.domain in setOf("button", "input_button") -> "Pulsar"
    e.domain == "camera" -> "Cámara"
    e.domain == "alarm_control_panel" -> ALARM_TEXT[e.state] ?: e.state
    e.state == "on" -> "Encendido"
    e.state == "off" -> "Apagado"
    else -> listOf(e.state, e.unit).filter { it.isNotEmpty() }.joinToString(" ")
}

fun iconOf(id: String): ImageVector = when {
    "kill_switch" in id -> Icons.Filled.PowerSettingsNew
    "smart_switch" in id || id == "switch.trixie" -> Icons.Filled.Dns
    "noche" in id -> Icons.Filled.Bedtime
    "peli" in id -> Icons.Filled.Movie
    "ausente" in id -> Icons.AutoMirrored.Filled.DirectionsWalk
    "monitor" in id -> Icons.Filled.Monitor
    "contenedor" in id -> Icons.Filled.Inventory2
    "capture" in id -> Icons.Filled.CameraAlt
    id.startsWith("media_player.") || "tv" in id -> Icons.Filled.Tv
    else -> when (id.substringBefore('.')) {
        "light" -> Icons.Filled.Lightbulb
        "scene" -> Icons.Filled.Palette
        "script", "automation" -> Icons.Filled.PlayArrow
        "button", "input_button" -> Icons.Filled.RadioButtonChecked
        "alarm_control_panel" -> Icons.Filled.Shield
        "camera" -> Icons.Filled.Videocam
        "lock" -> Icons.Filled.Lock
        else -> Icons.Filled.ToggleOn
    }
}
