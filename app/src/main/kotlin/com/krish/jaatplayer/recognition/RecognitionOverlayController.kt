package com.krish.jaatplayer.recognition

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs

/**
 * A small floating "listening..." / result bubble shown with [WindowManager], the same
 * mechanism apps like Messenger use for chat heads. Unlike launching an Activity, this
 * never takes over the screen or switches away from whatever app (e.g. Instagram) is in
 * the foreground — the bubble sits on top of it, and everywhere outside the bubble's own
 * small bounds still receives touches normally, so the user can keep using that app while
 * it listens.
 *
 * Needs [Settings.canDrawOverlays] to be true (the "Display over other apps" permission).
 * Caller is expected to check that before using this.
 */
class RecognitionOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var iconView: ProgressBar? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var dismissRunnable: Runnable? = null
    private var onTapResult: (() -> Unit)? = null

    private var layoutParams: WindowManager.LayoutParams? = null

    // drag-to-dismiss bookkeeping
    private var downRawX = 0f
    private var downRawY = 0f
    private var downParamX = 0
    private var downParamY = 0
    private var isDragging = false

    fun canShow(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Shows (or re-shows) the bubble in its "listening" state. */
    fun showListening() {
        mainHandler.post {
            ensureBubble()
            setListeningContent()
        }
    }

    fun showProcessing() {
        mainHandler.post { setProcessingContent() }
    }

    fun showResult(title: String, subtitle: String, autoDismissMs: Long = 4000L, onTap: (() -> Unit)? = null) {
        mainHandler.post {
            ensureBubble()
            onTapResult = onTap
            setResultContent(title, subtitle, isError = false)
            scheduleAutoDismiss(autoDismissMs)
        }
    }

    fun showMessage(message: String, autoDismissMs: Long = 2500L) {
        mainHandler.post {
            ensureBubble()
            onTapResult = null
            setResultContent(context.getString(com.krish.jaatplayer.R.string.recognize_music), message, isError = true)
            scheduleAutoDismiss(autoDismissMs)
        }
    }

    fun dismiss() {
        mainHandler.post { removeBubble() }
    }

    // -------------------------------------------------------------- internals --

    private fun scheduleAutoDismiss(delayMs: Long) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { removeBubble() }
        dismissRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    private fun ensureBubble() {
        if (bubbleView != null) return

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(90)
        }
        layoutParams = params

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(Color.argb(235, 24, 24, 28))
            setStroke(dp(1), Color.argb(60, 255, 255, 255))
        }

        val icon = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(12)
            }
        }
        iconView = icon

        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
        }
        titleView = title

        val subtitle = TextView(context).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 12f
            maxLines = 1
        }
        subtitleView = subtitle

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(18), dp(12))
            background = bg
            elevation = dp(8).toFloat()
            addView(icon)
            addView(textColumn)
            alpha = 0f
            scaleX = 0.7f
            scaleY = 0.7f
        }
        bubbleView = root

        root.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamX = params.x
                    downParamY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (!isDragging && (abs(dx) > dp(6) || abs(dy) > dp(6))) isDragging = true
                    if (isDragging) {
                        params.x = downParamX + dx
                        params.y = downParamY + dy
                        runCatching { windowManager.updateViewLayout(root, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val tap = onTapResult
                        if (tap != null) {
                            tap.invoke()
                            removeBubble()
                        } else {
                            removeBubble()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(root, params)
            root.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        } catch (e: Exception) {
            bubbleView = null
        }
    }

    private fun setListeningContent() {
        iconView?.isIndeterminate = true
        iconView?.visibility = View.VISIBLE
        titleView?.text = context.getString(com.krish.jaatplayer.R.string.recognition_notification_listening)
        subtitleView?.text = context.getString(com.krish.jaatplayer.R.string.recognize_music)
        subtitleView?.visibility = View.VISIBLE
    }

    private fun setProcessingContent() {
        titleView?.text = context.getString(com.krish.jaatplayer.R.string.recognition_notification_processing)
    }

    private fun setResultContent(title: String, subtitle: String, isError: Boolean) {
        iconView?.visibility = View.GONE
        titleView?.text = title
        subtitleView?.text = subtitle
    }

    private fun removeBubble() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        val view = bubbleView ?: return
        bubbleView = null
        try {
            view.animate()
                .alpha(0f).scaleX(0.7f).scaleY(0.7f)
                .setDuration(160)
                .withEndAction {
                    runCatching { windowManager.removeView(view) }
                }
                .start()
        } catch (e: Exception) {
            runCatching { windowManager.removeView(view) }
        }
    }
}
