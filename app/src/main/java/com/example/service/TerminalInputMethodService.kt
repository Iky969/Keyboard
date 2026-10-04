package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.example.R

/**
 * Official Android InputMethodService designed specifically for terminal/shell/Termux/SSH sessions.
 * Sends authentic hardware KeyEvents with full modifier states (Ctrl, Alt, Shift), DPAD arrows,
 * Esc, Tab, and F1–F12 keys.
 */
class TerminalInputMethodService : InputMethodService() {

    // Modifiers State
    private var isCtrlActive = false
    private var isCtrlLocked = false

    private var isAltActive = false
    private var isAltLocked = false

    private var isShiftActive = false
    private var isShiftLocked = false

    // Layer enumeration
    private enum class KeyboardLayer {
        QWERTY,
        SYMBOLS,
        FN
    }
    private var currentLayer = KeyboardLayer.QWERTY

    // Views
    private var rootView: View? = null
    private var btnCtrl: Button? = null
    private var btnAlt: Button? = null
    private var btnShift: Button? = null
    private var layerQwerty: View? = null
    private var layerSymbols: View? = null
    private var layerFn: View? = null
    private var snippetsBar: HorizontalScrollView? = null
    private var snippetsContainer: LinearLayout? = null

    private val qwertyLetterButtons = mutableListOf<Button>()

    // Handler for key repeats (e.g. holding backspace or arrows)
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    // Vibrator
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onCreateInputView(): View {
        val inflater = LayoutInflater.from(this)
        val root = inflater.inflate(R.layout.keyboard_main, null)
        root.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rootView = root

        btnCtrl = root.findViewById(R.id.key_ctrl)
        btnAlt = root.findViewById(R.id.key_alt)
        layerQwerty = root.findViewById(R.id.layer_qwerty)
        layerSymbols = root.findViewById(R.id.layer_symbols)
        layerFn = root.findViewById(R.id.layer_fn)
        snippetsBar = root.findViewById(R.id.snippets_bar)
        snippetsContainer = root.findViewById(R.id.snippets_container)

        setupTerminalTopBar(root)
        setupQwertyLayer(root)
        setupSymbolsLayer(root)
        setupFnLayer(root)
        setupSnippetsBar()

        updateModifierUi()
        switchLayer(KeyboardLayer.QWERTY)

        return root
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return true
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateInputViewShown()
        if (!restarting) {
            if (!isCtrlLocked) isCtrlActive = false
            if (!isAltLocked) isAltActive = false
            if (!isShiftLocked) isShiftActive = false
            updateModifierUi()
        }
    }

    override fun onDestroy() {
        stopKeyRepeat()
        super.onDestroy()
    }

    // ==========================================
    // HARDWARE KEYCODE INJECTION HELPER
    // ==========================================
    private fun sendHardwareKey(keyCode: Int, metaState: Int = 0) {
        performHaptic()
        val ic = currentInputConnection ?: return
        val eventTime = SystemClock.uptimeMillis()

        var effectiveMeta = metaState
        if (isCtrlActive || isCtrlLocked) {
            effectiveMeta = effectiveMeta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (isAltActive || isAltLocked) {
            effectiveMeta = effectiveMeta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        if (isShiftActive || isShiftLocked) {
            effectiveMeta = effectiveMeta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }

        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, effectiveMeta))
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, effectiveMeta))

        // Reset non-locked sticky modifiers
        var modified = false
        if (isCtrlActive && !isCtrlLocked) {
            isCtrlActive = false
            modified = true
        }
        if (isAltActive && !isAltLocked) {
            isAltActive = false
            modified = true
        }
        if (isShiftActive && !isShiftLocked) {
            isShiftActive = false
            modified = true
        }
        if (modified) {
            updateModifierUi()
        }
    }

    private fun commitCharacterOrHardwareKey(char: Char) {
        performHaptic()
        val isModifierActive = isCtrlActive || isCtrlLocked || isAltActive || isAltLocked

        if (isModifierActive) {
            // When Ctrl or Alt is active, send as hardware KeyEvent with modifier state
            val keyCode = charToKeyCode(char)
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                sendHardwareKey(keyCode)
                return
            }
        }

        val ic = currentInputConnection ?: return
        val textToCommit = if (isShiftActive || isShiftLocked) {
            char.uppercase()
        } else {
            char.lowercase()
        }
        ic.commitText(textToCommit, 1)

        if (isShiftActive && !isShiftLocked) {
            isShiftActive = false
            updateModifierUi()
        }
    }

    private fun commitRawText(text: String) {
        performHaptic()
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
    }

    private fun charToKeyCode(char: Char): Int {
        return when (char.lowercaseChar()) {
            'a' -> KeyEvent.KEYCODE_A
            'b' -> KeyEvent.KEYCODE_B
            'c' -> KeyEvent.KEYCODE_C
            'd' -> KeyEvent.KEYCODE_D
            'e' -> KeyEvent.KEYCODE_E
            'f' -> KeyEvent.KEYCODE_F
            'g' -> KeyEvent.KEYCODE_G
            'h' -> KeyEvent.KEYCODE_H
            'i' -> KeyEvent.KEYCODE_I
            'j' -> KeyEvent.KEYCODE_J
            'k' -> KeyEvent.KEYCODE_K
            'l' -> KeyEvent.KEYCODE_L
            'm' -> KeyEvent.KEYCODE_M
            'n' -> KeyEvent.KEYCODE_N
            'o' -> KeyEvent.KEYCODE_O
            'p' -> KeyEvent.KEYCODE_P
            'q' -> KeyEvent.KEYCODE_Q
            'r' -> KeyEvent.KEYCODE_R
            's' -> KeyEvent.KEYCODE_S
            't' -> KeyEvent.KEYCODE_T
            'u' -> KeyEvent.KEYCODE_U
            'v' -> KeyEvent.KEYCODE_V
            'w' -> KeyEvent.KEYCODE_W
            'x' -> KeyEvent.KEYCODE_X
            'y' -> KeyEvent.KEYCODE_Y
            'z' -> KeyEvent.KEYCODE_Z
            '0' -> KeyEvent.KEYCODE_0
            '1' -> KeyEvent.KEYCODE_1
            '2' -> KeyEvent.KEYCODE_2
            '3' -> KeyEvent.KEYCODE_3
            '4' -> KeyEvent.KEYCODE_4
            '5' -> KeyEvent.KEYCODE_5
            '6' -> KeyEvent.KEYCODE_6
            '7' -> KeyEvent.KEYCODE_7
            '8' -> KeyEvent.KEYCODE_8
            '9' -> KeyEvent.KEYCODE_9
            '/' -> KeyEvent.KEYCODE_SLASH
            '\\' -> KeyEvent.KEYCODE_BACKSLASH
            '-' -> KeyEvent.KEYCODE_MINUS
            '=' -> KeyEvent.KEYCODE_EQUALS
            '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            ';' -> KeyEvent.KEYCODE_SEMICOLON
            '\'' -> KeyEvent.KEYCODE_APOSTROPHE
            '`' -> KeyEvent.KEYCODE_GRAVE
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }

    // ==========================================
    // TOP TERMINAL BAR SETUP
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTerminalTopBar(root: View) {
        root.findViewById<Button>(R.id.key_esc).setOnClickListener {
            sendHardwareKey(KeyEvent.KEYCODE_ESCAPE)
        }

        root.findViewById<Button>(R.id.key_tab).setOnClickListener {
            sendHardwareKey(KeyEvent.KEYCODE_TAB)
        }

        btnCtrl?.setOnClickListener {
            toggleCtrlState()
        }

        btnAlt?.setOnClickListener {
            toggleAltState()
        }

        root.findViewById<Button>(R.id.key_pipe).setOnClickListener {
            commitRawText("|")
        }

        root.findViewById<Button>(R.id.key_tilde).setOnClickListener {
            commitRawText("~")
        }

        // DPAD Arrow Keys with auto-repeat on hold
        bindRepeatKey(root.findViewById(R.id.key_up), KeyEvent.KEYCODE_DPAD_UP)
        bindRepeatKey(root.findViewById(R.id.key_down), KeyEvent.KEYCODE_DPAD_DOWN)
        bindRepeatKey(root.findViewById(R.id.key_left), KeyEvent.KEYCODE_DPAD_LEFT)
        bindRepeatKey(root.findViewById(R.id.key_right), KeyEvent.KEYCODE_DPAD_RIGHT)

        root.findViewById<Button>(R.id.key_fn_toggle).setOnClickListener {
            if (currentLayer == KeyboardLayer.FN) {
                switchLayer(KeyboardLayer.QWERTY)
            } else {
                switchLayer(KeyboardLayer.FN)
            }
        }

        root.findViewById<Button>(R.id.key_snippets_toggle).setOnClickListener {
            toggleSnippetsBar()
        }
    }

    // ==========================================
    // QWERTY LAYER SETUP
    // ==========================================
    private fun setupQwertyLayer(root: View) {
        qwertyLetterButtons.clear()

        val letterMap = mapOf(
            R.id.key_q to 'q', R.id.key_w to 'w', R.id.key_e to 'e', R.id.key_r to 'r',
            R.id.key_t to 't', R.id.key_y to 'y', R.id.key_u to 'u', R.id.key_i to 'i',
            R.id.key_o to 'o', R.id.key_p to 'p', R.id.key_a to 'a', R.id.key_s to 's',
            R.id.key_d to 'd', R.id.key_f to 'f', R.id.key_g to 'g', R.id.key_h to 'h',
            R.id.key_j to 'j', R.id.key_k to 'k', R.id.key_l to 'l', R.id.key_z to 'z',
            R.id.key_x to 'x', R.id.key_c to 'c', R.id.key_v to 'v', R.id.key_b to 'b',
            R.id.key_n to 'n', R.id.key_m to 'm'
        )

        for ((id, char) in letterMap) {
            val btn = root.findViewById<Button>(id)
            if (btn != null) {
                qwertyLetterButtons.add(btn)
                btn.setOnClickListener {
                    commitCharacterOrHardwareKey(char)
                }
            }
        }

        btnShift = root.findViewById(R.id.key_shift)
        btnShift?.setOnClickListener {
            toggleShiftState()
        }

        bindRepeatKey(root.findViewById(R.id.key_backspace), KeyEvent.KEYCODE_DEL)

        root.findViewById<Button>(R.id.key_switch_symbols)?.setOnClickListener {
            switchLayer(KeyboardLayer.SYMBOLS)
        }

        root.findViewById<Button>(R.id.key_slash)?.setOnClickListener {
            commitCharacterOrHardwareKey('/')
        }

        root.findViewById<Button>(R.id.key_minus)?.setOnClickListener {
            commitCharacterOrHardwareKey('-')
        }

        root.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            commitRawText(" ")
        }

        root.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            sendHardwareKey(KeyEvent.KEYCODE_ENTER)
        }

        root.findViewById<Button>(R.id.key_switch_ime)?.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
    }

    // ==========================================
    // SYMBOLS LAYER SETUP
    // ==========================================
    private fun setupSymbolsLayer(root: View) {
        val symbolMap = mapOf(
            R.id.sym_1 to "1", R.id.sym_2 to "2", R.id.sym_3 to "3", R.id.sym_4 to "4",
            R.id.sym_5 to "5", R.id.sym_6 to "6", R.id.sym_7 to "7", R.id.sym_8 to "8",
            R.id.sym_9 to "9", R.id.sym_0 to "0", R.id.sym_backtick to "`",
            R.id.sym_exclamation to "!", R.id.sym_at to "@", R.id.sym_hash to "#",
            R.id.sym_dollar to "$", R.id.sym_percent to "%", R.id.sym_caret to "^",
            R.id.sym_ampersand to "&", R.id.sym_asterisk to "*", R.id.sym_underscore to "_",
            R.id.sym_plus to "+", R.id.sym_equals to "=", R.id.sym_backslash to "\\",
            R.id.sym_brace_open to "{", R.id.sym_brace_close to "}",
            R.id.sym_bracket_open to "[", R.id.sym_bracket_close to "]",
            R.id.sym_lt to "<", R.id.sym_gt to ">", R.id.sym_colon to ":",
            R.id.sym_semicolon to ";", R.id.sym_doublequote to "\"", R.id.sym_singlequote to "'"
        )

        for ((id, text) in symbolMap) {
            root.findViewById<Button>(id)?.setOnClickListener {
                commitRawText(text)
            }
        }

        root.findViewById<Button>(R.id.key_switch_qwerty_from_sym)?.setOnClickListener {
            switchLayer(KeyboardLayer.QWERTY)
        }

        root.findViewById<Button>(R.id.sym_space)?.setOnClickListener {
            commitRawText(" ")
        }

        bindRepeatKey(root.findViewById(R.id.sym_backspace), KeyEvent.KEYCODE_DEL)

        root.findViewById<Button>(R.id.sym_enter)?.setOnClickListener {
            sendHardwareKey(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ==========================================
    // FN & NAVIGATION LAYER SETUP (F1-F12 + NAV)
    // ==========================================
    private fun setupFnLayer(root: View) {
        val fnMap = mapOf(
            R.id.fn_f1 to KeyEvent.KEYCODE_F1,
            R.id.fn_f2 to KeyEvent.KEYCODE_F2,
            R.id.fn_f3 to KeyEvent.KEYCODE_F3,
            R.id.fn_f4 to KeyEvent.KEYCODE_F4,
            R.id.fn_f5 to KeyEvent.KEYCODE_F5,
            R.id.fn_f6 to KeyEvent.KEYCODE_F6,
            R.id.fn_f7 to KeyEvent.KEYCODE_F7,
            R.id.fn_f8 to KeyEvent.KEYCODE_F8,
            R.id.fn_f9 to KeyEvent.KEYCODE_F9,
            R.id.fn_f10 to KeyEvent.KEYCODE_F10,
            R.id.fn_f11 to KeyEvent.KEYCODE_F11,
            R.id.fn_f12 to KeyEvent.KEYCODE_F12,
            R.id.nav_home to KeyEvent.KEYCODE_MOVE_HOME,
            R.id.nav_end to KeyEvent.KEYCODE_MOVE_END,
            R.id.nav_pgup to KeyEvent.KEYCODE_PAGE_UP,
            R.id.nav_pgdn to KeyEvent.KEYCODE_PAGE_DOWN,
            R.id.nav_insert to KeyEvent.KEYCODE_INSERT,
            R.id.nav_del to KeyEvent.KEYCODE_FORWARD_DEL,
            R.id.fn_esc to KeyEvent.KEYCODE_ESCAPE,
            R.id.fn_tab to KeyEvent.KEYCODE_TAB,
            R.id.fn_enter to KeyEvent.KEYCODE_ENTER,
            R.id.fn_dpad_center to KeyEvent.KEYCODE_DPAD_CENTER
        )

        for ((id, keyCode) in fnMap) {
            root.findViewById<Button>(id)?.setOnClickListener {
                sendHardwareKey(keyCode)
            }
        }

        root.findViewById<Button>(R.id.key_switch_qwerty_from_fn)?.setOnClickListener {
            switchLayer(KeyboardLayer.QWERTY)
        }

        root.findViewById<Button>(R.id.fn_space)?.setOnClickListener {
            commitRawText(" ")
        }
    }

    // ==========================================
    // QUICK SNIPPETS BAR SETUP
    // ==========================================
    private fun setupSnippetsBar() {
        val container = snippetsContainer ?: return
        container.removeAllViews()

        val snippets = listOf(
            "clear\n" to "clear",
            "ls -la\n" to "ls -la",
            "cd ..\n" to "cd ..",
            "git status\n" to "git status",
            "git pull\n" to "git pull",
            "sudo " to "sudo",
            "top\n" to "top",
            "exit\n" to "exit",
            "grep -rn " to "grep",
            "chmod +x " to "chmod +x",
            "curl -O " to "curl -O",
            "nano " to "nano",
            "python " to "python"
        )

        for ((command, label) in snippets) {
            val btn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = label
                setTextColor(getColor(R.color.terminal_cyan))
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setBackgroundResource(R.drawable.bg_key_special)
                setPadding(dpToPx(10), 0, dpToPx(10), 0)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(dpToPx(2), dpToPx(1), dpToPx(2), dpToPx(1))
                }
                layoutParams = params
                setOnClickListener {
                    commitRawText(command)
                }
            }
            container.addView(btn)
        }
    }

    private fun toggleSnippetsBar() {
        val bar = snippetsBar ?: return
        bar.visibility = if (bar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ==========================================
    // LAYER SWITCHING
    // ==========================================
    private fun switchLayer(layer: KeyboardLayer) {
        currentLayer = layer
        layerQwerty?.visibility = if (layer == KeyboardLayer.QWERTY) View.VISIBLE else View.GONE
        layerSymbols?.visibility = if (layer == KeyboardLayer.SYMBOLS) View.VISIBLE else View.GONE
        layerFn?.visibility = if (layer == KeyboardLayer.FN) View.VISIBLE else View.GONE

        // Update Fn button indicator
        val fnBtn = rootView?.findViewById<Button>(R.id.key_fn_toggle)
        if (layer == KeyboardLayer.FN) {
            fnBtn?.setBackgroundResource(R.drawable.bg_key_ctrl_active)
            fnBtn?.setTextColor(getColor(R.color.terminal_cyan))
        } else {
            fnBtn?.setBackgroundResource(R.drawable.bg_key_special)
            fnBtn?.setTextColor(getColor(R.color.terminal_purple))
        }
    }

    // ==========================================
    // STICKY / LOCKED MODIFIER STATE MACHINE
    // ==========================================
    private fun toggleCtrlState() {
        performHaptic()
        when {
            !isCtrlActive && !isCtrlLocked -> {
                // Inactive -> Sticky Active
                isCtrlActive = true
                isCtrlLocked = false
            }
            isCtrlActive && !isCtrlLocked -> {
                // Sticky Active -> Locked
                isCtrlActive = true
                isCtrlLocked = true
            }
            else -> {
                // Locked -> Inactive
                isCtrlActive = false
                isCtrlLocked = false
            }
        }
        updateModifierUi()
    }

    private fun toggleAltState() {
        performHaptic()
        when {
            !isAltActive && !isAltLocked -> {
                isAltActive = true
                isAltLocked = false
            }
            isAltActive && !isAltLocked -> {
                isAltActive = true
                isAltLocked = true
            }
            else -> {
                isAltActive = false
                isAltLocked = false
            }
        }
        updateModifierUi()
    }

    private fun toggleShiftState() {
        performHaptic()
        when {
            !isShiftActive && !isShiftLocked -> {
                isShiftActive = true
                isShiftLocked = false
            }
            isShiftActive && !isShiftLocked -> {
                isShiftActive = true
                isShiftLocked = true
            }
            else -> {
                isShiftActive = false
                isShiftLocked = false
            }
        }
        updateModifierUi()
    }

    private fun updateModifierUi() {
        // Ctrl UI
        when {
            isCtrlLocked -> {
                btnCtrl?.setBackgroundResource(R.drawable.bg_key_ctrl_locked)
                btnCtrl?.setTextColor(getColor(R.color.terminal_bg))
                btnCtrl?.text = "CTRL 🔒"
            }
            isCtrlActive -> {
                btnCtrl?.setBackgroundResource(R.drawable.bg_key_ctrl_active)
                btnCtrl?.setTextColor(getColor(R.color.terminal_cyan))
                btnCtrl?.text = "CTRL •"
            }
            else -> {
                btnCtrl?.setBackgroundResource(R.drawable.bg_key_special)
                btnCtrl?.setTextColor(getColor(R.color.terminal_text_primary))
                btnCtrl?.text = "CTRL"
            }
        }

        // Alt UI
        when {
            isAltLocked -> {
                btnAlt?.setBackgroundResource(R.drawable.bg_key_alt_locked)
                btnAlt?.setTextColor(getColor(R.color.terminal_bg))
                btnAlt?.text = "ALT 🔒"
            }
            isAltActive -> {
                btnAlt?.setBackgroundResource(R.drawable.bg_key_alt_active)
                btnAlt?.setTextColor(getColor(R.color.terminal_amber))
                btnAlt?.text = "ALT •"
            }
            else -> {
                btnAlt?.setBackgroundResource(R.drawable.bg_key_special)
                btnAlt?.setTextColor(getColor(R.color.terminal_text_primary))
                btnAlt?.text = "ALT"
            }
        }

        // Shift UI
        when {
            isShiftLocked -> {
                btnShift?.setBackgroundResource(R.drawable.bg_key_shift_locked)
                btnShift?.setTextColor(getColor(R.color.terminal_bg))
                btnShift?.text = "⇪"
            }
            isShiftActive -> {
                btnShift?.setBackgroundResource(R.drawable.bg_key_shift_active)
                btnShift?.setTextColor(getColor(R.color.terminal_green))
                btnShift?.text = "⇧ •"
            }
            else -> {
                btnShift?.setBackgroundResource(R.drawable.bg_key_special)
                btnShift?.setTextColor(getColor(R.color.terminal_text_primary))
                btnShift?.text = "⇧"
            }
        }

        // Update letters casing
        val uppercase = isShiftActive || isShiftLocked
        for (btn in qwertyLetterButtons) {
            val text = btn.text.toString()
            btn.text = if (uppercase) text.uppercase() else text.lowercase()
        }
    }

    // ==========================================
    // KEY REPEAT LISTENER (FOR BACKSPACE & ARROWS)
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private fun bindRepeatKey(button: View?, keyCode: Int) {
        button?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    sendHardwareKey(keyCode)
                    startKeyRepeat(keyCode)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopKeyRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun startKeyRepeat(keyCode: Int) {
        stopKeyRepeat()
        repeatRunnable = object : Runnable {
            override fun run() {
                sendHardwareKey(keyCode)
                repeatHandler.postDelayed(this, 50)
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 400) // Initial delay before repeat starts
    }

    private fun stopKeyRepeat() {
        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        repeatRunnable = null
    }

    // ==========================================
    // HAPTIC FEEDBACK
    // ==========================================
    private fun performHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
            }
        } catch (_: Exception) {
            rootView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
