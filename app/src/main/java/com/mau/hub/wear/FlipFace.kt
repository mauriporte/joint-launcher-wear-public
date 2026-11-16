package com.mau.hub.wear

import android.content.ComponentName
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationStyle
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import java.time.ZonedDateTime

/**
 * Joint Launcher's flip clock as a watch face: hours and minutes on two dark cards, weather above, steps and heart
 * rate below (complications from Google Weather and Fossil's health apps, which are already on the watch), and a
 * Sonando line at the bottom from Joint Launcher itself. Tapping the clock opens Joint Launcher.
 */
class FlipFace : WatchFaceService() {
    override fun createUserStyleSchema() = UserStyleSchema(emptyList())

    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        fun factory(textSize: Int) = CanvasComplicationFactory { watchState, listener ->
            CanvasComplicationDrawable(styled(ComplicationDrawable(this), textSize), watchState, listener)
        }
        val small = listOf(ComplicationType.SHORT_TEXT, ComplicationType.SMALL_IMAGE, ComplicationType.MONOCHROMATIC_IMAGE)
        fun slot(id: Int, bounds: RectF, policy: DefaultComplicationDataSourcePolicy, types: List<ComplicationType> = small, textSize: Int = 34) =
            ComplicationSlot.createRoundRectComplicationSlotBuilder(id, factory(textSize), types, policy, ComplicationSlotBounds(bounds)).build()
        return ComplicationSlotsManager(
            listOf(
                slot(WEATHER, RectF(0.25f, 0.08f, 0.75f, 0.28f), policy(WEATHER_SOURCE, SystemDataSources.NO_DATA_SOURCE)),
                slot(STEPS, RectF(0.15f, 0.71f, 0.50f, 0.84f), policy(STEPS_SOURCE, SystemDataSources.DATA_SOURCE_STEP_COUNT)),
                slot(HEART, RectF(0.50f, 0.71f, 0.85f, 0.84f), policy(HEART_SOURCE, SystemDataSources.NO_DATA_SOURCE)),
                slot(
                    SONANDO, RectF(0.20f, 0.85f, 0.80f, 0.95f),
                    DefaultComplicationDataSourcePolicy(ComponentName(this, SonandoSource::class.java), ComplicationType.LONG_TEXT, SystemDataSources.NO_DATA_SOURCE, ComplicationType.LONG_TEXT),
                    listOf(ComplicationType.LONG_TEXT, ComplicationType.SHORT_TEXT), textSize = 24,
                ),
            ),
            currentUserStyleRepository,
        )
    }

    private fun policy(source: ComponentName, fallback: Int) =
        DefaultComplicationDataSourcePolicy(source, ComplicationType.SHORT_TEXT, fallback, ComplicationType.SHORT_TEXT)

    /** Bare text and icon, no bubble, in the launcher's soft greys; dimmer in ambient. */
    private fun styled(d: ComplicationDrawable, textSize: Int) = d.apply {
        for ((style, text, icon) in listOf(Triple(activeStyle, 0xFFC9CBD3.toInt(), 0xFFC9CBD3.toInt()), Triple(ambientStyle, 0xFF777777.toInt(), 0xFF777777.toInt()))) {
            style.backgroundColor = Color.TRANSPARENT
            style.borderStyle = ComplicationStyle.BORDER_STYLE_NONE
            style.textColor = text
            style.titleColor = text
            style.iconColor = icon
            // wide slots put the icon beside the value; big text so it reads at a glance
            style.textSize = textSize
            style.titleSize = 26
            style.setTextTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        }
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ) = WatchFace(WatchFaceType.DIGITAL, FlipRenderer(surfaceHolder, watchState, complicationSlotsManager, currentUserStyleRepository))
        .setTapListener(object : WatchFace.TapListener {
            override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
                // the cards fill the middle band; taps on complications go to their own apps
                val h = surfaceHolder.surfaceFrame.height().toFloat()
                if (tapType != TapType.UP || complicationSlot != null || tapEvent.yPos < h * 0.29f || tapEvent.yPos > h * 0.71f) return
                startActivity(Intent(this@FlipFace, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })

    companion object {
        const val WEATHER = 0
        const val STEPS = 1
        const val HEART = 2
        const val SONANDO = 3
        val WEATHER_SOURCE = ComponentName("com.google.android.wearable.weather", "com.google.android.wearable.weather.complications.WeatherProviderService")
        val STEPS_SOURCE = ComponentName("com.fossil.wearables.watchfaces", "com.fossil.common.complication.whs.complicationservice.StepCountComplicationService")
        val HEART_SOURCE = ComponentName("com.fossil.wearables.watchfaces", "com.fossil.wearables.hrm.engine.HeartRateComplicationService")
    }
}

class FlipRenderer(
    surfaceHolder: SurfaceHolder,
    private val watchState: WatchState,
    private val slots: ComplicationSlotsManager,
    repo: CurrentUserStyleRepository,
) : Renderer.CanvasRenderer2<FlipRenderer.Assets>(surfaceHolder, repo, watchState, CanvasType.HARDWARE, 1000L, false) {
    class Assets : SharedAssets { override fun onDestroy() {} }

    override suspend fun createSharedAssets() = Assets()

    private val hours = FlipCard()
    private val minutes = FlipCard()

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        val ambient = watchState.isAmbient.value == true
        canvas.drawColor(Color.BLACK)
        // ambient: outline-free dark cards and dim digits, no animation (burn-in, battery)
        for (f in listOf(hours, minutes)) f.dim(ambient)
        val now = System.currentTimeMillis()
        hours.set("%02d".format(zonedDateTime.hour), now, animate = !ambient)
        minutes.set("%02d".format(zonedDateTime.minute), now, animate = !ambient)

        val w = bounds.width().toFloat()
        val size = w * 0.40f // as big as the round screen allows with weather above and steps/heart below
        val gap = size / 11
        val left = bounds.left + (w - 2 * size - gap) / 2
        val top = bounds.exactCenterY() - size / 2
        hours.draw(canvas, RectF(left, top, left + size, top + size), now)
        minutes.draw(canvas, RectF(left + size + gap, top, left + 2 * size + gap, top + size), now)

        for (slot in slots.complicationSlots.values) if (slot.enabled) slot.render(canvas, zonedDateTime, renderParameters)
        if (hours.flipping(now) || minutes.flipping(now)) invalidate()
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)
        for (slot in slots.complicationSlots.values) if (slot.enabled) slot.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
    }

}
