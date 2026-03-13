package com.dennisli.bpmfkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * Custom KeyboardView that draws letter keys and modifier keys with distinct
 * background colours, matching the system keyboard visual style.
 */
class BopomofoKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : KeyboardView(context, attrs, defStyle) {

    // ── Colours ───────────────────────────────────────────────────────────────
    private val colorLetterKey    = Color.WHITE                  // letter: white bg
    private val colorLetterText   = Color.BLACK                  // letter: black text
    private val colorModifierKey  = Color.parseColor("#00BE5A")  // modifier: green bg
    private val colorModifierText = Color.BLACK                  // modifier: black text
    private val colorPressed      = Color.parseColor("#009944")  // pressed: darker green

    // ── Drawing helpers ───────────────────────────────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = false
    }
    private val rect = RectF()
    private val density   get() = resources.displayMetrics.density
    private val inset     get() = density * 3f
    private val corner    get() = density * 6f

    // ── Pressed key tracking (set by IME via OnKeyboardActionListener) ────────
    var pressedCode: Int = Int.MIN_VALUE
        set(v) { field = v; invalidate() }

    // ── Drawing ───────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val kb = keyboard ?: run { super.onDraw(canvas); return }

        canvas.drawColor(Color.BLACK)

        val pl = paddingLeft.toFloat()
        val pt = paddingTop.toFloat()

        for (key in kb.keys) {
            val code = if (key.codes.isNotEmpty()) key.codes[0] else 0
            val isPressed  = code == pressedCode && pressedCode != Int.MIN_VALUE
            val isModifier = isModifier(key)

            bgPaint.color = when {
                isPressed  -> colorPressed
                isModifier -> colorModifierKey
                else       -> colorLetterKey
            }

            rect.set(
                pl + key.x + inset,
                pt + key.y + inset,
                pl + key.x + key.width  - inset,
                pt + key.y + key.height - inset
            )
            canvas.drawRoundRect(rect, corner, corner, bgPaint)

            val label = key.label?.toString() ?: continue
            labelPaint.color     = if (isModifier) colorModifierText else colorLetterText
            labelPaint.textSize  = labelTextSize(label)
            val cx = pl + key.x + key.width  / 2f
            val cy = pt + key.y + key.height / 2f -
                     (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(label, cx, cy, labelPaint)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun isModifier(key: Keyboard.Key): Boolean {
        if (key.modifier) return true
        val code = if (key.codes.isNotEmpty()) key.codes[0] else 0
        if (code < 0 || code == -3) return true          // SHIFT / DELETE / custom
        val lbl = key.label?.toString() ?: return false
        return lbl in setOf("Space", "123", "ABC", "#+=", "space")
    }

    private fun labelTextSize(label: String): Float = density * when {
        label.length == 1  -> 18f    // single char: letter, digit, symbol
        label.length <= 3  -> 16f    // short: 123, ABC
        else               -> 13f    // long: Space, #+=
    }
}
