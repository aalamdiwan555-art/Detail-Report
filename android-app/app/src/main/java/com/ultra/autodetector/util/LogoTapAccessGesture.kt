package com.ultra.autodetector.util

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.ultra.autodetector.ui.admin.AdminActivity

object LogoTapAccessGesture {
    private const val HOLD_DURATION_MS = 6000L

    fun attach(target: View?) {
        if (target == null) return
        
        target.isClickable = true
        target.isLongClickable = true
        target.isFocusable = true
        target.isFocusableInTouchMode = false
        
        // Important: Prevent parent ScrollView from intercepting
        target.setOnTouchListener(object : View.OnTouchListener {
            private val handler = Handler(Looper.getMainLooper())
            private var runnable: Runnable? = null
            
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Tell parent to not intercept touch
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        
                        runnable = Runnable {
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val ctx = v.context
                            Toast.makeText(ctx, "Admin access", Toast.LENGTH_SHORT).show()
                            try {
                                val intent = Intent(ctx, AdminActivity::class.java)
                                // FIX: Add NEW_TASK flag if context is not Activity
                                if (ctx !is Activity) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(v.context, "Admin error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        handler.postDelayed(runnable!!, HOLD_DURATION_MS)
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        runnable?.let { handler.removeCallbacks(it) }
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                        v.performClick()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // If finger moves too far, cancel
                        return true
                    }
                }
                return true
            }
        })
    }
}
