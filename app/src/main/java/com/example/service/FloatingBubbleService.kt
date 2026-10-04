package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot

/**
 * FloatingBubbleService provides a draggable overlay bubble (gelembung melayang)
 * that allows users to summon the Terminal Keyboard anywhere on Android,
 * even when the current foreground screen/app has no active input text field.
 */
class FloatingBubbleService : Service() {

    companion object {
        var isServiceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var dummyInputView: EditText? = null
    private var isKeyboardActive = false
    private var bubbleTextView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingBubble()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        hideKeyboardOverlay()
        removeBubbleView()
        isServiceRunning = false
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        if (bubbleView != null) return

        val sizePx = dpToPx(52)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }

        // Create circular badge container
        val container = FrameLayout(this).apply {
            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#161B22"))
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
            background = backgroundDrawable
            elevation = dpToPx(8).toFloat()
        }

        val textView = TextView(this).apply {
            text = ">_"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 17f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(textView)
        bubbleTextView = textView

        // Dragging & Click touch listener
        var initialX = 0
        var initialY = 0
        var touchDownX = 0f
        var touchDownY = 0f
        var touchDownTime = 0L

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    touchDownTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchDownX).toInt()
                    params.y = initialY + (event.rawY - touchDownY).toInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - touchDownX
                    val dy = event.rawY - touchDownY
                    val distance = hypot(dx.toDouble(), dy.toDouble())
                    val duration = System.currentTimeMillis() - touchDownTime

                    // If movement is small and brief, treat as click
                    if (distance < 18 && duration < 400) {
                        onBubbleClicked()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            bubbleView = container
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memunculkan gelembung: Izin overlay diperlukan", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun onBubbleClicked() {
        if (isKeyboardActive) {
            hideKeyboardOverlay()
            Toast.makeText(this, "Keyboard disembunyikan", Toast.LENGTH_SHORT).show()
        } else {
            showKeyboardOverlay()
            Toast.makeText(this, "Keyboard dimunculkan", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Spawns an offscreen 1x1 focused window overlay to force the IME to display
     * even without any text field focused in the underlying application.
     */
    private fun showKeyboardOverlay() {
        hideKeyboardOverlay()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dummy = EditText(this).apply {
            alpha = 0f
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
        }

        val dummyParams = WindowManager.LayoutParams(
            1,
            1,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(dummy, dummyParams)
            dummyInputView = dummy

            dummy.requestFocus()
            dummy.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(dummy, InputMethodManager.SHOW_FORCED)
            }, 80)

            isKeyboardActive = true
            updateBubbleAppearance(true)
        } catch (_: Exception) {
            // Fallback to toggleSoftInput
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            isKeyboardActive = true
            updateBubbleAppearance(true)
        }
    }

    private fun hideKeyboardOverlay() {
        val dummy = dummyInputView
        if (dummy != null) {
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(dummy.windowToken, 0)
                windowManager.removeView(dummy)
            } catch (_: Exception) {}
            dummyInputView = null
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }
        isKeyboardActive = false
        updateBubbleAppearance(false)
    }

    private fun updateBubbleAppearance(isActive: Boolean) {
        val container = bubbleView as? FrameLayout ?: return
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (isActive) {
                setColor(Color.parseColor("#003844"))
                setStroke(dpToPx(2), Color.parseColor("#00E676"))
            } else {
                setColor(Color.parseColor("#161B22"))
                setStroke(dpToPx(2), Color.parseColor("#00E5FF"))
            }
        }
        container.background = backgroundDrawable
        bubbleTextView?.apply {
            text = if (isActive) "⌨️" else ">_"
            setTextColor(if (isActive) Color.parseColor("#00E676") else Color.parseColor("#00E5FF"))
        }
    }

    private fun removeBubbleView() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        bubbleView = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
