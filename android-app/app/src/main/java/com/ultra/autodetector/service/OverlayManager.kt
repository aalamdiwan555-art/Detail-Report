package com.ultra.autodetector.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ultra.autodetector.R
import kotlin.math.roundToInt

/**
 * Owns every window used by the detection overlay.
 *
 * The status bubble is intentionally a small touchable window instead of a
 * full-screen touchable window. That keeps the close button clickable without
 * blocking taps in the app currently being detected.
 */
class OverlayManager(
    private val context: Context,
    private val onCloseClicked: () -> Unit,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private var controlView: FrameLayout? = null
    private var highlightView: DetectionHighlightView? = null

    fun show() {
        if (controlView != null) return

        val highlights = DetectionHighlightView(context)
        val highlightParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val controls = createControlView()
        val controlParams = WindowManager.LayoutParams(
            dp(156),
            dp(88),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(96)
        }

        runCatching {
            windowManager.addView(highlights, highlightParams)
            windowManager.addView(controls, controlParams)
            highlightView = highlights
            controlView = controls
        }.onFailure {
            runCatching { windowManager.removeViewImmediate(highlights) }
            throw it
        }
    }

    fun showMatch(rect: Rect) {
        highlightView?.show(rect)
    }

    fun hide() {
        highlightView?.hide()
        controlView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        highlightView?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        controlView = null
        highlightView = null
    }

    private fun createControlView(): FrameLayout {
        val root = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.ink_light))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), ContextCompat.getColor(context, R.color.primary))
            }
            elevation = dp(8).toFloat()
            contentDescription = "Detection overlay"
        }

        val pushContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = false
        }
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.logo_mark)
            contentDescription = "PUSH logo"
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        val label = TextView(context).apply {
            text = "PUSH"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            letterSpacing = 0.12f
            setPadding(dp(8), 0, 0, 0)
        }
        pushContent.addView(logo)
        pushContent.addView(label)
        root.addView(
            pushContent,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val close = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ContextCompat.getColorStateList(context, android.R.color.white)
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.error))
                shape = GradientDrawable.OVAL
            }
            contentDescription = "Close detection overlay and stop detection"
            isClickable = true
            isFocusable = true
            setOnClickListener { onCloseClicked() }
        }
        root.addView(
            close,
            FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(6)
                rightMargin = dp(6)
            },
        )
        return root
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private class DetectionHighlightView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private var rectangle: Rect? = null
        private val hideRunnable = Runnable { hide() }

        fun show(value: Rect) {
            rectangle = value
            removeCallbacks(hideRunnable)
            postDelayed(hideRunnable, 700L)
            invalidate()
        }

        fun hide() {
            rectangle = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            rectangle?.let { value ->
                canvas.drawRect(
                    value.left.toFloat(),
                    value.top.toFloat(),
                    value.right.toFloat(),
                    value.bottom.toFloat(),
                    paint,
                )
            }
        }
    }
}