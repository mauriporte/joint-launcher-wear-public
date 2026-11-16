package com.mau.hub.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.Gravity
import android.view.SurfaceHolder
import android.widget.Toast
import org.json.JSONObject
import java.util.Calendar

/**
 * Joint Launcher for Android Wear 1.5 (SWR50, 320x320 square): the launcher face's flip clock with Joint Remote
 * on its left and MauHomeAssist on its right, like the Fossil. HA goes through the phone (see Ha, phone-sw3).
 */
class SquareFace : CanvasWatchFaceService() {
    override fun onCreateEngine(): Engine = FaceEngine()

    inner class FaceEngine : CanvasWatchFaceService.Engine() {
        private val hours = FlipCard()
        private val minutes = FlipCard()
        private val logo by lazy { getDrawable(R.drawable.ic_launcher)!! }
        private val remote by lazy { getDrawable(R.drawable.ic_remote)!! }
        private val mauha by lazy { getDrawable(R.drawable.ic_mauha)!! }
        private val dnd by lazy { getDrawable(R.drawable.ic_dnd)!! }
        private val bolt by lazy { getDrawable(R.drawable.ic_charging)!! }
        /** Home, Back, Vol−, Vol+ under the clock, like the Fossil's row (play/pause left out on the SW3). */
        private val keys by lazy {
            listOf(
                getDrawable(R.drawable.ic_key_home)!! to { tvKey("HOME") },
                getDrawable(R.drawable.ic_key_back)!! to { tvKey("BACK") },
                getDrawable(R.drawable.ic_vol_down)!! to { tvVolume("volume_down") },
                getDrawable(R.drawable.ic_vol_up)!! to { tvVolume("volume_up") },
            ).takeIf { BuildConfig.TV_REMOTE.isNotBlank() && BuildConfig.TV.isNotBlank() }.orEmpty()
        }
        private var keysTop = 0f
        private var keySize = 0f
        private var charging = false
        private val battery = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                invalidate()
            }
        }
        private val zen = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = invalidate()
        }
        private fun dndOn() = Settings.Global.getInt(contentResolver, "zen_mode", 0) != 0
        private var ambient = false
        private var clockLeft = 0f
        private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
        private val wifi by lazy { getSystemService(WifiManager::class.java) }
        private var wifiHold: ConnectivityManager.NetworkCallback? = null
        private val wifiLock by lazy {
            wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "jl:sw3").apply { setReferenceCounted(false) }
        }
        @Suppress("DEPRECATION") // still honoured on Android 6, and a watch face has no window to set KEEP_SCREEN_ON on
        private val screenLock by lazy {
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "jl:sw3-screen")
                .apply { setReferenceCounted(false) }
        }
        private val wifiState = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = followWifi()
        }

        /**
         * Rule: Wi-Fi follows the user only. While it's on, hold a Wi-Fi network request and a high-perf lock so AW 1.5
         * keeps it up in ambient; the moment it's off (by the user or by the system), let go so nothing here can bring it
         * back. Never turns Wi-Fi on or off itself.
         */
        private fun followWifi() {
            if (wifi.isWifiEnabled) {
                if (!wifiLock.isHeld) wifiLock.acquire()
                // a refused request (permission) must never take the face down with it
                if (wifiHold == null) wifiHold = runCatching {
                    object : ConnectivityManager.NetworkCallback() {}.also {
                        connectivity.requestNetwork(
                            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                            it,
                        )
                    }
                }.getOrNull()
            } else releaseWifi()
        }

        private fun releaseWifi() {
            wifiHold?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
            wifiHold = null
            if (wifiLock.isHeld) wifiLock.release()
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            // the system's status icons come as one bar; the face draws its own instead, like the Fossil: Do Not Disturb
            // above the logo, the charging bolt at the bottom edge. "Ok Google" goes top-right, out of their way.
            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@SquareFace).setAcceptsTapEvents(true)
                    .setHideStatusBar(true)
                    .setHotwordIndicatorGravity(Gravity.TOP or Gravity.RIGHT)
                    .build()
            )
            registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            contentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, zen)
            registerReceiver(wifiState, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
            followWifi()
            // Rule: the screen stays on until the user turns it off. The lock stops the timeout dimming it but not the user's
            // own off (palm/button); it never wakes the screen. Interactive also means no ambient, where gms drops Wi-Fi.
            screenLock.acquire()
        }

        override fun onDestroy() {
            unregisterReceiver(wifiState)
            unregisterReceiver(battery)
            contentResolver.unregisterContentObserver(zen)
            releaseWifi()
            if (screenLock.isHeld) screenLock.release()
            super.onDestroy()
        }

        override fun onTimeTick() { super.onTimeTick(); invalidate() }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            ambient = inAmbientMode
            invalidate()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            canvas.drawColor(Color.BLACK)
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            for (f in listOf(hours, minutes)) f.dim(ambient)
            hours.set("%02d".format(cal.get(Calendar.HOUR_OF_DAY)), now, animate = !ambient)
            minutes.set("%02d".format(cal.get(Calendar.MINUTE)), now, animate = !ambient)

            // square screen: the cards fill the middle, leaving the left edge for the shortcut
            val size = w * 0.33f
            val gap = size / 11
            clockLeft = (w - 2 * size - gap) / 2
            val top = (h - size) / 2
            hours.draw(canvas, RectF(clockLeft, top, clockLeft + size, top + size), now)
            minutes.draw(canvas, RectF(clockLeft + size + gap, top, clockLeft + 2 * size + gap, top + size), now)

            if (!ambient) {
                val icon = (w * 0.115f).toInt()
                val iconTop = ((top - icon) / 2).toInt()
                logo.setBounds(((w - icon) / 2).toInt(), iconTop, ((w + icon) / 2).toInt(), iconTop + icon)
                logo.draw(canvas)
                val s = (w * 0.10f).toInt()
                val cx = (clockLeft / 2).toInt()
                val cy = (h / 2).toInt()
                remote.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                remote.draw(canvas)
                val rx = (w - clockLeft / 2).toInt()
                mauha.setBounds(rx - s / 2, cy - s / 2, rx + s / 2, cy + s / 2)
                mauha.draw(canvas)

                // the key row between the clock and the bottom edge, evenly spaced
                keySize = w * 0.15f
                keysTop = top + size + (h - top - size - keySize) * 0.35f
                val ks = keys
                ks.forEachIndexed { i, (d, _) -> draw(canvas, d, w * (i + 0.5f) / ks.size, keysTop + keySize / 2, keySize * 0.62f, 0xFFC9CBD3.toInt()) }
            }
            val mark = if (ambient) 0xFF777777.toInt() else Color.WHITE
            if (dndOn()) draw(canvas, dnd, w / 2, h * 0.045f, w * 0.06f, mark)
            if (charging) draw(canvas, bolt, w / 2, h * 0.955f, w * 0.06f, mark)
            if (hours.flipping(now) || minutes.flipping(now)) invalidate()
        }

        private fun draw(canvas: Canvas, d: Drawable, cx: Float, cy: Float, size: Float, color: Int) {
            d.setTint(color)
            d.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
            d.draw(canvas)
        }

        /** The TV keys go to HA through the phone helper (AW 1.5 apps can't reach HA themselves). */
        private fun call(domain: String, service: String, data: JSONObject) =
            Link.send(this@SquareFace, RelayPaths.CALL, JSONObject().put("domain", domain).put("service", service).put("data", data).toString().toByteArray()) {
                Handler(Looper.getMainLooper()).post { Toast.makeText(this@SquareFace, "Sin conexión con el móvil", Toast.LENGTH_SHORT).show() }
            }

        private fun tvKey(command: String) = call("remote", "send_command", JSONObject().put("entity_id", BuildConfig.TV_REMOTE).put("command", command))

        private fun tvVolume(service: String) = call("media_player", service, JSONObject().put("entity_id", BuildConfig.TV))

        /** Left: Joint Remote; right: MauHomeAssist; the clock: its 8-button ring (the Fossil's tile); the row: TV keys. */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            if (tapType != WatchFaceService.TAP_TYPE_TAP || ambient) return
            val w = surfaceHolder.surfaceFrame.width()
            val ks = keys
            if (ks.isNotEmpty() && y >= keysTop - keySize * 0.2f && y <= keysTop + keySize * 1.2f) {
                ks[(x * ks.size / w).coerceIn(0, ks.lastIndex)].second()
                return
            }
            val intent = when {
                x < clockLeft -> Intent().setClassName("com.mau.remote", "com.mau.remote.wear.WearActivity")
                x > w - clockLeft -> Intent(this@SquareFace, HaActivity::class.java)
                else -> Intent(this@SquareFace, HaActivity::class.java).putExtra(HaActivity.ROUTE, "ring")
            }
            runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Toast.makeText(this@SquareFace, "No está instalada", Toast.LENGTH_SHORT).show() }
        }
    }
}
