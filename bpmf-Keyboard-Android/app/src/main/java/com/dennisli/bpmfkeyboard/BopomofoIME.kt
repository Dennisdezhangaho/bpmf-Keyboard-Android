package com.dennisli.bpmfkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.media.AudioManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class BopomofoIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    // ── Keyboards ─────────────────────────────────────────────────────────────
    private lateinit var keyboardView: BopomofoKeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private var isSymbols = false
    private var shiftState = 0   // 0=off  1=on  2=caps-lock

    // ── Tone row ──────────────────────────────────────────────────────────────
    private lateinit var rowA0: LinearLayout
    private lateinit var tone1: Button
    private lateinit var tone2: Button
    private lateinit var tone3: Button
    private lateinit var tone4: Button

    // ── Tone detection state (mirrors original Swift logic) ───────────────────
    private var deleteCount  = 0
    private var letterRocket = ""
    private var yuanyin      = ""

    private val yuanyinji = "üuioeɑÜUIOEA"
    private val yunmuji = listOf(
        "Ü","U","I","O","E","A",
        "ü","u","i","o","e","ɑ",
        "UN","IN","ON","OU","EN","ER","EI","AO","AN","AI",
        "un","in","on","ou","en","er","ei","ɑo","ɑn","ɑi",
        "UNG","ING","ONG","ANG","ENG",
        "ung","ing","ong","ɑng","eng"
    )

    // ── IME lifecycle ─────────────────────────────────────────────────────────
    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)

        keyboardView = root.findViewById(R.id.keyboardView)
        rowA0  = root.findViewById(R.id.rowA0)
        tone1  = root.findViewById(R.id.tone1)
        tone2  = root.findViewById(R.id.tone2)
        tone3  = root.findViewById(R.id.tone3)
        tone4  = root.findViewById(R.id.tone4)

        qwertyKeyboard  = Keyboard(this, R.xml.keyboard_qwerty)
        symbolsKeyboard = Keyboard(this, R.xml.keyboard_symbols)

        keyboardView.keyboard = qwertyKeyboard
        keyboardView.isPreviewEnabled = false
        keyboardView.setOnKeyboardActionListener(this)

        tone1.setOnClickListener { onTonePressed(tone1) }
        tone2.setOnClickListener { onTonePressed(tone2) }
        tone3.setOnClickListener { onTonePressed(tone3) }
        tone4.setOnClickListener { onTonePressed(tone4) }

        return root
    }

    // ── KeyboardView.OnKeyboardActionListener ─────────────────────────────────
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                hideToneRow()
                playClick()
            }
            Keyboard.KEYCODE_SHIFT -> {
                shiftState = when (shiftState) {
                    0    -> 1
                    1    -> 2
                    else -> 0
                }
                updateShiftState()
                playClick()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                isSymbols = !isSymbols
                keyboardView.keyboard = if (isSymbols) symbolsKeyboard else qwertyKeyboard
                if (isSymbols) hideToneRow()
                playClick()
            }
            -3 -> {
                // ABC button inside symbols keyboard
                isSymbols = false
                keyboardView.keyboard = qwertyKeyboard
                playClick()
            }
            32 -> {   // space
                ic.commitText(" ", 1)
                hideToneRow()
                playClick()
            }
            10 -> {   // return / enter
                ic.commitText("\n", 1)
                playClick()
            }
            else -> {
                if (primaryCode <= 0) return
                val raw  = primaryCode.toChar().toString()
                val text = if (shiftState > 0) raw.uppercase() else raw
                ic.commitText(text, 1)
                if (shiftState == 1) { shiftState = 0; updateShiftState() }
                detectTones(text)
                playClick()
            }
        }
    }

    override fun onPress(primaryCode: Int)    { keyboardView.pressedCode = primaryCode }
    override fun onRelease(primaryCode: Int)  { keyboardView.pressedCode = Int.MIN_VALUE }
    override fun onText(text: CharSequence?)  {}
    override fun swipeLeft()  {}
    override fun swipeRight() {}
    override fun swipeDown()  {}
    override fun swipeUp()    {}

    // ── Tone button pressed ───────────────────────────────────────────────────
    private fun onTonePressed(btn: Button) {
        val ic = currentInputConnection ?: return
        val toned = btn.text?.toString() ?: return
        repeat(deleteCount) { ic.deleteSurroundingText(1, 0) }
        ic.commitText(toned, 1)
        ic.commitText(" ", 1)
        hideToneRow()
        playClick()
    }

    // ── Tone detection (same logic as original Swift) ─────────────────────────
    private fun detectTones(typed: String) {
        val ic = currentInputConnection ?: return
        val str1 = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        val str2 = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        val str3 = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""

        fun vowelsIn(s: String) = s.filter { yuanyinji.contains(it) }.toSet().joinToString("")

        // single char
        if (yunmuji.contains(str1)) {
            yuanyin = vowelsIn(str1); deleteCount = 1; letterRocket = str1; showTones()
        }
        // two chars
        if (yunmuji.contains(str2)) {
            yuanyin = vowelsIn(str2); deleteCount = 2; letterRocket = str2; showTones()
        }
        listOf("ɑo","ɑi","ei","ou","AO","AI","EI","OU").forEach { combo ->
            if (str2.contains(combo)) {
                yuanyin = vowelsIn(combo.first().toString())
                deleteCount = 2; letterRocket = str2; showTones()
            }
        }
        // three chars
        if (str1 != str2 && str2 != str3 && yunmuji.contains(str3)) {
            yuanyin = vowelsIn(str3); deleteCount = 3; letterRocket = str3; showTones()
        }
    }

    private fun showTones() {
        if (yuanyin.isEmpty()) return
        val macron = "\u0304"
        val acute  = "\u0301"
        val caron  = "\u030C"
        val grave  = "\u0300"
        tone1.text = replaceLast(letterRocket, yuanyin, yuanyin + macron)
        tone2.text = replaceLast(letterRocket, yuanyin, yuanyin + acute)
        tone3.text = replaceLast(letterRocket, yuanyin, yuanyin + caron)
        tone4.text = replaceLast(letterRocket, yuanyin, yuanyin + grave)
        rowA0.visibility = View.VISIBLE
    }

    private fun hideToneRow() {
        rowA0.visibility = View.GONE
        yuanyin = ""; letterRocket = ""
    }

    private fun replaceLast(src: String, target: String, replacement: String): String {
        val idx = src.lastIndexOf(target)
        if (idx < 0) return src
        return src.substring(0, idx) + replacement + src.substring(idx + target.length)
    }

    // ── Shift: update key labels and keyboard shifted state ───────────────────
    private fun updateShiftState() {
        val kb = keyboardView.keyboard ?: return
        kb.isShifted = shiftState > 0
        for (key in kb.keys) {
            val lbl = key.label?.toString() ?: continue
            if (lbl.length == 1 && lbl[0].isLetter()) {
                key.label = if (shiftState > 0) lbl.uppercase() else lbl.lowercase()
            }
        }
        // ɑ ↔ A  and  ü ↔ Ü
        for (key in kb.keys) {
            when (key.label?.toString()) {
                "\u0251" -> if (shiftState > 0) key.label = "A"
                "A"      -> if (shiftState == 0 && key.codes[0] == 593) key.label = "\u0251"
                "\u00FC" -> if (shiftState > 0) key.label = "\u00DC"
                "\u00DC" -> if (shiftState == 0) key.label = "\u00FC"
            }
        }
        keyboardView.invalidateAllKeys()
    }

    // ── Audio click ───────────────────────────────────────────────────────────
    private fun playClick() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
    }
}
