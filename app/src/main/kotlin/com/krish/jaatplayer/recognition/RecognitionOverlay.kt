package com.krish.jaatplayer.recognition

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.krish.jaatplayer.R
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Frameless floating UI drawn directly over whatever app is on screen (Instagram, YouTube...)
 * while a song is recognised from the Quick Settings tile. There is NO capsule / card behind
 * it - only the content itself, parked on the right edge:
 *
 *  - Listening : the 5 bouncing bars + "Listening..." (tap them to cancel)
 *  - Identifying: the diamond icon, pulsing
 *  - Result    : a purple speech bubble (title + artist, with a cross) pointing at the
 *                round cover art. It stays until the user taps the cross; tapping the
 *                bubble / cover opens the song in the app.
 *
 * Lives in a singleton so it outlives the foreground service (which stops itself as soon as
 * recognition is done). Call everything on the main thread. Needs "Display over other apps";
 * check [canShow] first - every show* returns false if the window can't be added.
 */
object RecognitionOverlay {
    private const val TAG = "RecognitionOverlay"

    private val LAVENDER = 0xFFC0C6EE.toInt()
    private val PINK = 0xFFF8E1FB.toInt()
    private val BUBBLE = 0xFF6D58A5.toInt()
    private val BUBBLE_TITLE = 0xFFF3EDFF.toInt()
    private val BUBBLE_ARTIST = 0xFFCDBEF2.toInt()
    private val SUBTLE_FILL = 0x33FFFFFF
    private val TEXT_SHADOW = 0xB3000000.toInt()

    private var windowManager: WindowManager? = null
    private var root: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var coverView: ImageView? = null
    private var tapAction: (() -> Unit)? = null
    private val animators = mutableListOf<ValueAnimator>()

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** Bars + "Listening...". Tapping them cancels. */
    fun showListening(context: Context, onCancel: () -> Unit): Boolean =
        swapContent(context, edgeMarginDp = 18) { r, c ->
            tapAction = onCancel
            r.gravity = Gravity.CENTER_HORIZONTAL
            r.addView(BarsView(c), LinearLayout.LayoutParams(dp(c, 70), dp(c, 76)))
            r.addView(
                label(c, c.getString(R.string.listening), 16f, LAVENDER, shadow = true),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(c, 6) },
            )
        }

    /** The diamond icon on its own, pulsing, with a small caption. */
    fun showProcessing(context: Context, text: String): Boolean =
        swapContent(context, edgeMarginDp = 18) { r, c ->
            r.gravity = Gravity.CENTER_HORIZONTAL
            val icon = ImageView(c).apply {
                setImageResource(R.drawable.ic_shazam_diamond)
                setColorFilter(LAVENDER)
            }
            r.addView(icon, LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)))
            r.addView(
                label(c, text, 16f, LAVENDER, shadow = true),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(c, 6) },
            )
            animators.add(
                ValueAnimator.ofFloat(0.88f, 1.1f).apply {
                    duration = 800
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        val s = it.animatedValue as Float
                        icon.scaleX = s
                        icon.scaleY = s
                    }
                    start()
                },
            )
        }

    /**
     * Speech bubble (title / artist / cross) -> pointer -> round cover art.
     * Stays on screen until the cross is tapped. Tapping the bubble or cover runs [onOpen]
     * and closes it.
     */
    fun showResult(context: Context, title: String, artist: String, onOpen: () -> Unit): Boolean =
        swapContent(context, edgeMarginDp = 0) { r, c ->
            tapAction = {
                onOpen()
                dismiss()
            }
            r.gravity = Gravity.CENTER_VERTICAL

            val textMax = textMaxWidth(c, reservedDp = 64 + 13 + 40 + 38 + 8)
            r.addView(bubble(c, title, artist, textMax), wrap())
            r.addView(ArrowView(c, BUBBLE), LinearLayout.LayoutParams(dp(c, 13), dp(c, 22)))

            val cover = ImageView(c).apply {
                // Placeholder (diamond on lavender) until the real cover has loaded.
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(LAVENDER)
                }
                setImageResource(R.drawable.ic_shazam_diamond)
                setColorFilter(0xFF2A2C45.toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16))
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
            }
            coverView = cover
            r.addView(cover, LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)))
        }

    /** "No match" / error: same purple bubble with a cross, stays until dismissed. */
    fun showMessage(context: Context, message: String): Boolean =
        swapContent(context, edgeMarginDp = 12) { r, c ->
            tapAction = null
            r.gravity = Gravity.CENTER_VERTICAL
            r.addView(bubble(c, message, null, textMaxWidth(c, reservedDp = 40 + 38 + 24)), wrap())
        }

    /** Swaps the placeholder for the real cover art (no-op unless the result is showing). */
    fun setCover(bitmap: Bitmap) {
        coverView?.let {
            it.clearColorFilter()
            it.setPadding(0, 0, 0, 0)
            it.scaleType = ImageView.ScaleType.CENTER_CROP
            it.setImageBitmap(bitmap)
        }
    }

    fun dismiss() {
        stopAnimators()
        coverView = null
        tapAction = null
        val r = root
        root = null
        layoutParams = null
        if (r != null) {
            try {
                windowManager?.removeView(r)
            } catch (e: Exception) {
                Timber.tag(TAG).d(e, "removeView failed (already gone?)")
            }
        }
    }

    // --- building blocks -----------------------------------------------------------

    private fun wrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun textMaxWidth(c: Context, reservedDp: Int): Int {
        val screenW = c.resources.displayMetrics.widthPixels
        return min(dp(c, 210), screenW - dp(c, reservedDp))
    }

    /** Purple rounded rectangle with title, optional artist and the cross. */
    private fun bubble(c: Context, title: String, artist: String?, textMaxPx: Int): LinearLayout {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(BUBBLE)
                cornerRadius = dp(c, 12).toFloat()
            }
            setPadding(dp(c, 18), dp(c, 10), dp(c, 10), dp(c, 10))
        }
        val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(
            label(c, title, 20f, BUBBLE_TITLE, shadow = false).apply {
                maxLines = 2
                maxWidth = textMaxPx
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.START
            },
        )
        if (artist != null) {
            texts.addView(
                label(c, artist, 16f, BUBBLE_ARTIST, shadow = false).apply {
                    maxLines = 1
                    maxWidth = textMaxPx
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.START
                },
            )
        }
        box.addView(texts, wrap())
        box.addView(
            closeButton(c),
            LinearLayout.LayoutParams(dp(c, 28), dp(c, 28)).apply { marginStart = dp(c, 10) },
        )
        return box
    }

    private fun label(c: Context, text: String, sizeSp: Float, color: Int, shadow: Boolean) =
        TextView(c).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER
            if (shadow) setShadowLayer(dp(c, 3).toFloat(), 0f, dp(c, 1).toFloat(), TEXT_SHADOW)
        }

    private fun closeButton(c: Context) =
        ImageView(c).apply {
            setImageResource(R.drawable.close)
            setColorFilter(Color.WHITE)
            setPadding(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(SUBTLE_FILL)
            }
            contentDescription = c.getString(R.string.cancel)
            isClickable = true
            setOnClickListener { dismiss() }
        }

    // --- window handling -----------------------------------------------------------

    private fun swapContent(
        context: Context,
        edgeMarginDp: Int,
        build: (LinearLayout, Context) -> Unit,
    ): Boolean {
        val app = context.applicationContext
        if (!attach(app)) return false
        val r = root ?: return false
        val lp = layoutParams ?: return false
        stopAnimators()
        coverView = null
        tapAction = null
        r.removeAllViews()
        build(r, app)

        // Parked on the right edge, a little above the vertical centre.
        lp.x = dp(app, edgeMarginDp)
        lp.y = -(app.resources.displayMetrics.heightPixels * 0.06f).toInt()
        try {
            windowManager?.updateViewLayout(r, lp)
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "updateViewLayout failed")
        }
        return true
    }

    private fun attach(app: Context): Boolean {
        if (root != null) return true
        if (!canShow(app)) return false
        return try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // No background, no padding, no elevation: nothing but the content is drawn.
            val frame = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }

            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }

            enableDragAndTap(frame, lp, wm)
            wm.addView(frame, lp)

            windowManager = wm
            root = frame
            layoutParams = lp
            true
        } catch (e: Exception) {
            // BadTokenException / SecurityException when the permission was revoked, etc.
            Timber.tag(TAG).w(e, "Unable to add recognition overlay")
            root = null
            layoutParams = null
            false
        }
    }

    private fun stopAnimators() {
        animators.forEach { it.cancel() }
        animators.clear()
    }

    /**
     * Drag anywhere on the content to move it; a plain tap runs [tapAction]. (The cross is its
     * own clickable child, so it never reaches this listener.)
     */
    private fun enableDragAndTap(view: View, lp: WindowManager.LayoutParams, wm: WindowManager) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        // Gravity is END, so x is measured from the right edge.
                        lp.x = startX - dx.toInt()
                        lp.y = startY + dy.toInt()
                        try {
                            wm.updateViewLayout(v, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) tapAction?.invoke()
                    true
                }
                else -> true
            }
        }
    }

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    // --- custom views --------------------------------------------------------------

    /** The same 5 randomly bouncing pill bars as the in-app "Listening" screen. */
    private class BarsView(context: Context) : View(context) {
        private val n = 5
        private val cur = FloatArray(n) { 0.2f }
        private val from = FloatArray(n) { 0.2f }
        private val to = FloatArray(n) { Random.nextFloat() * 0.8f + 0.2f }
        private val startAt = LongArray(n) { SystemClock.uptimeMillis() }
        private val durMs = LongArray(n) { Random.nextLong(300, 600) }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = SystemClock.uptimeMillis()
            val w = width.toFloat()
            val h = height.toFloat()
            val barW = w / (n + (n - 1) * 0.7f)
            val gap = barW * 0.7f

            for (i in 0 until n) {
                val t = ((now - startAt[i]).toFloat() / durMs[i]).coerceIn(0f, 1f)
                cur[i] = from[i] + (to[i] - from[i]) * t
                if (t >= 1f) {
                    from[i] = cur[i]
                    to[i] = Random.nextFloat() * 0.8f + 0.2f
                    startAt[i] = now
                    durMs[i] = Random.nextLong(300, 600)
                }

                paint.color = if (i % 2 == 0) LAVENDER else PINK
                val barH = h * cur[i]
                val left = i * (barW + gap)
                val top = (h - barH) / 2f
                rect.set(left, top, left + barW, top + barH)
                canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
            }
            postInvalidateOnAnimation()
        }
    }

    /** Small right-pointing triangle that joins the bubble to the cover art. */
    private class ArrowView(context: Context, fillColor: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(width.toFloat(), height / 2f)
            path.lineTo(0f, height.toFloat())
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
