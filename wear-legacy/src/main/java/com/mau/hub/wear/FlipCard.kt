// Separate from the Fossil (wear-legacy and app share nothing): its own FlipCard copy, kept apart on purpose
package com.mau.hub.wear

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** One flip card (same drawing as the launcher's FlipCard), shared by the watch face and the app's clock page: the old top leaf folds down onto the new number. */
class FlipCard {
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt() }
    private val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8B8B8.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val fold = Paint().apply { color = Color.BLACK }
    private var now = ""
    private var before = ""
    private var start = 0L

    fun set(text: String, time: Long, animate: Boolean) {
        if (text == now) return
        before = if (animate && now.isNotEmpty()) now else text
        now = text
        start = time
    }

    /** Ambient: near-black card and dim digits (burn-in, battery). */
    fun dim(on: Boolean) {
        card.color = if (on) 0xFF0A0A0A.toInt() else 0xFF111111.toInt()
        digits.color = if (on) 0xFF7A7A7A.toInt() else 0xFFB8B8B8.toInt()
    }

    fun flipping(time: Long) = before != now && time - start < FLIP_MS

    fun draw(canvas: Canvas, r: RectF, time: Long) {
        val progress = if (before == now) 1f else ((time - start) / FLIP_MS.toFloat()).coerceIn(0f, 1f)
        val h = r.height(); val mid = r.centerY()
        digits.textSize = h * 0.7f
        val baseline = mid - (digits.descent() + digits.ascent()) / 2
        fun half(text: String, top: Boolean, squash: Float = 1f) {
            canvas.save()
            if (top) canvas.clipRect(r.left, r.top, r.right, mid) else canvas.clipRect(r.left, mid, r.right, r.bottom)
            canvas.scale(1f, squash, r.centerX(), mid) // a leaf turning about the fold
            canvas.drawRoundRect(r, h * 0.09f, h * 0.09f, card)
            canvas.drawText(text, r.centerX(), baseline, digits)
            canvas.restore()
        }
        half(now, top = true)
        half(if (progress < 0.5f) before else now, top = false)
        if (progress < 1f) {
            if (progress < 0.5f) half(before, top = true, squash = Math.cos(progress * Math.PI).toFloat())
            else half(now, top = false, squash = -Math.cos(progress * Math.PI).toFloat())
        }
        canvas.drawRect(r.left, mid - h * 0.006f, r.right, mid + h * 0.006f, fold)
        if (progress >= 1f) before = now
    }

    companion object {
        const val FLIP_MS = 600L
    }
}
