package com.mau.hub.wear

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

/** One notification as the app shows it; [open] is what tapping it does in its own app. */
class Notif(val key: String, val app: String, val title: String, val text: String, val time: Long, val open: PendingIntent?)

/**
 * Mirrors the watch's notifications for the app's Notificaciones page. Needs notification access, granted once
 * (Wear OS has no settings screen for it): adb shell cmd notification allow_listener com.mau.hub.wear/com.mau.hub.wear.Notifs
 */
class Notifs : NotificationListenerService() {
    override fun onListenerConnected() {
        connected = true
        list.clear()
        list.addAll(activeNotifications.orEmpty().mapNotNull { toNotif(it) }.sortedByDescending { it.time })
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = toNotif(sbn) ?: return
        list.removeAll { it.key == n.key }
        list.add(0, n)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        list.removeAll { it.key == sbn.key }
    }

    private fun toNotif(sbn: StatusBarNotification): Notif? {
        val n = sbn.notification
        // group summaries and ongoing ones (media, charging…) aren't messages to read
        if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return null
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        return Notif(sbn.key, app, title, text, sbn.postTime, n.contentIntent)
    }

    companion object {
        val list = mutableStateListOf<Notif>()
        var connected = false
            private set
    }
}
