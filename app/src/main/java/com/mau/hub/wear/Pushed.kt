package com.mau.hub.wear

import android.content.Context
import java.io.File

/**
 * Setup for release builds, where `run-as` doesn't work: config.json, token and url pushed with adb into the app's
 * external files folder (/sdcard/Android/data/<package>/files) move into private storage, and the pushed copy is
 * deleted so the token doesn't stay on shared storage.
 */
fun importPushed(ctx: Context) {
    val ext = ctx.getExternalFilesDir(null) ?: return
    for (name in listOf("config.json", "token", "url")) {
        val f = File(ext, name)
        if (!f.isFile) continue
        runCatching { f.copyTo(File(ctx.filesDir, name), overwrite = true) }
        f.delete()
    }
}
