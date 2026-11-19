package com.hydrachat.app.bubble

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.hydrachat.app.domain.Persona
import com.hydrachat.app.domain.StoreItem

/** System bubbles and conversation people need a bitmap icon; personas are emoji, so draw one. */
object PersonaIcon {
    // Adaptive icons are 108dp with a 72dp safe zone; the emoji must stay inside it.
    private const val SIZE_PX = 216
    private const val EMOJI_TEXT_RATIO = 0.4f
    private const val FALLBACK_COLOR = 0xFF42A5F5

    fun create(persona: Persona, skin: StoreItem): IconCompat {
        val bitmap = createBitmap(SIZE_PX, SIZE_PX)
        val canvas = Canvas(bitmap)
        canvas.drawColor((skin.ringColors.firstOrNull() ?: FALLBACK_COLOR).toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SIZE_PX * EMOJI_TEXT_RATIO
            textAlign = Paint.Align.CENTER
        }
        val baseline = SIZE_PX / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(persona.emoji, SIZE_PX / 2f, baseline, paint)
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }
}
