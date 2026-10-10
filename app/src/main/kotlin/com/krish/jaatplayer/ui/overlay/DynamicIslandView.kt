package com.krish.jaatplayer.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.krish.jaatplayer.R
import kotlin.math.max
import kotlin.random.Random

/**
 * Camera-anchored "Dynamic Island".
 *
 * Collapsed: a small black pill locked to the front-camera cut-out at the top-centre of the
 * screen (album art on the left, animated waveform on the right). It never moves - there is
 * no dragging.
 *
 * Expanded (tap): the pill grows into a translucent purple-grey player card just under the
 * status bar - artwork, title, artist, waveform, progress bar with times, and
 * previous / play-pause / next. Tap outside the card (or on empty card space) to collapse,
 * swipe up to hide it for the rest of the track.
 */
class DynamicIslandView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var collapsedView: LinearLayout? = null
    private var expandedView: LinearLayout? = null

    private var collapsedArt: ImageView? = null
    private var expandedArt: ImageView? = null
    private var expandedTitle: TextView? = null
    private var expandedArtist: TextView? = null
    private var timeCurrent: TextView? = null
    private var timeTotal: TextView? = null
    private var progressLine: ProgressLine? = null
    private var playPauseButton: ImageButton? = null
    private val waveViews = mutableListOf<WaveView>()

    private var artBitmap: Bitmap? = null
    private var lastIsPlaying = true

    var isExpanded = false
        private set

    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onDismissedBySwipe: (() -> Unit)? = null
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    private var downRawY = 0f

    // Metrics of the camera area, computed when the views are built.
    private var statusBarH = 0
    private var pillW = 0
    private var pillH = 0
    private var pillTop = 0

    val isShowing: Boolean get() = root != null

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    // ------------------------------------------------------------------ public --

    fun show(title: String, artist: String, isPlaying: Boolean) {
        if (root == null) buildViews()
        update(title, artist, isPlaying)
        val r = root ?: return
        if (r.parent == null && !r.isAttachedToWindow) {
            try {
                windowManager.addView(r, params)
                r.pivotY = 0f
                r.pivotX = pillW / 2f
                r.alpha = 0f
                r.scaleX = 0.7f
                r.scaleY = 0.7f
                r.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
            } catch (e: Exception) {
                root = null
            }
        }
    }

    fun update(title: String, artist: String, isPlaying: Boolean) {
        expandedTitle?.text = title
        expandedArtist?.text = artist
        lastIsPlaying = isPlaying
        playPauseButton?.setImageResource(if (isPlaying) R.drawable.pause else R.drawable.play)
        waveViews.forEach { it.playing = isPlaying }
    }

    fun setArt(bitmap: Bitmap?) {
        artBitmap = bitmap
        applyArt()
    }

    fun setProgress(positionMs: Long, durationMs: Long) {
        val d = if (durationMs > 0) durationMs else 0L
        val p = positionMs.coerceIn(0L, if (d > 0) d else positionMs)
        progressLine?.fraction = if (d > 0) p.toFloat() / d else 0f
        timeCurrent?.text = formatTime(p)
        timeTotal?.text = formatTime(d)
    }

    fun hide() {
        val r = root ?: return
        root = null
        isExpanded = false
        collapsedView = null
        expandedView = null
        waveViews.clear()
        try {
            r.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f)
                .setDuration(160)
                .withEndAction { runCatching { windowManager.removeView(r) } }
                .start()
        } catch (e: Exception) {
            runCatching { windowManager.removeView(r) }
        }
    }

    // ------------------------------------------------------------------ build --

    private fun computeMetrics() {
        val res = context.resources
        val sbId = res.getIdentifier("status_bar_height", "dimen", "android")
        statusBarH = if (sbId > 0) res.getDimensionPixelSize(sbId) else dp(28)
        pillH = dp(35) // ~95px
        pillW = dp(150) // ~420px
        pillTop = max(0, (statusBarH - pillH) / 2)

        // Lock onto the real camera cut-out when the system reports one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val cut = windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                if (cut != null && !cut.isEmpty) {
                    pillH = max(pillH, cut.height() + dp(8))
                    pillTop = max(0, cut.centerY() - pillH / 2)
                    pillW = max(pillW, cut.width() + dp(64))
                }
            }
        }
    }

    private fun roundedImage(sizePx: Int, radiusPx: Float): ImageView =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 255, 255, 255))
                cornerRadius = radiusPx
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }

    private fun buildViews() {
        computeMetrics()
        waveViews.clear()

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = pillTop
            // Without this the system refuses to draw an overlay over the camera cut-out.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        params = lp

        // ---------------- collapsed: [art]            [waveform] ----------------
        val artSize = pillH - dp(8)
        val cArt = roundedImage(artSize, dp(6).toFloat())
        collapsedArt = cArt

        val spacer = View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val cWave = WaveView(context).apply {
            playing = lastIsPlaying
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(14)).apply { marginEnd = dp(6) }
        }
        waveViews.add(cWave)

        val collapsed = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(5), 0)
            layoutParams = LinearLayout.LayoutParams(pillW, pillH)
            addView(cArt)
            addView(spacer)
            addView(cWave)
        }
        collapsedView = collapsed

        // ---------------- expanded card ----------------
        val eArt = roundedImage(dp(58), dp(10).toFloat())
        expandedArt = eArt

        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        expandedTitle = title
        val artist = TextView(context).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 16f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        expandedArtist = artist

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
                marginEnd = dp(10)
            }
            addView(title)
            addView(artist)
        }

        val eWave = WaveView(context).apply {
            playing = lastIsPlaying
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(22)).apply { bottomMargin = dp(18) }
        }
        waveViews.add(eWave)

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(eArt)
            addView(texts)
            addView(eWave)
        }

        val cur = TextView(context).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 13f
            text = formatTime(0)
        }
        timeCurrent = cur
        val total = TextView(context).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 13f
            text = formatTime(0)
        }
        timeTotal = total
        val progress = ProgressLine(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            }
        }
        progressLine = progress

        val progressRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
            addView(cur)
            addView(progress)
            addView(total)
        }

        fun flatButton(res: Int, size: Int, pad: Int, onClick: () -> Unit) = ImageButton(context).apply {
            setImageResource(res)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }

        val prev = flatButton(R.drawable.skip_previous, dp(56), dp(10)) { onPrevious?.invoke() }
        val next = flatButton(R.drawable.skip_next, dp(56), dp(10)) { onNext?.invoke() }
        val play = ImageButton(context).apply {
            setImageResource(if (lastIsPlaying) R.drawable.pause else R.drawable.play)
            setColorFilter(Color.rgb(43, 36, 56))
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                marginStart = dp(28)
                marginEnd = dp(28)
            }
            setOnClickListener { onPlayPause?.invoke() }
        }
        playPauseButton = play

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
            addView(prev)
            addView(play)
            addView(next)
        }

        val expanded = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            visibility = View.GONE
            addView(topRow)
            addView(progressRow)
            addView(controls)
        }
        expandedView = expanded

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(collapsed)
            addView(expanded)
        }
        root = container
        isExpanded = false
        applyBackground()
        applyArt()

        container.setOnTouchListener { _, event -> handleTouch(event) }
    }

    private fun applyBackground() {
        val r = root ?: return
        r.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            if (isExpanded) {
                cornerRadius = dp(30).toFloat()
                setColor(Color.argb(244, 92, 80, 108))
            } else {
                cornerRadius = pillH / 2f
                setColor(Color.BLACK)
            }
        }
    }

    private fun applyArt() {
        listOf(collapsedArt, expandedArt).forEach { iv ->
            iv?.setImageBitmap(artBitmap)
        }
    }

    // ------------------------------------------------------------------ touch --

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (isExpanded) setExpanded(false)
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                downRawY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (event.rawY - downRawY < -dp(24)) {
                    onDismissedBySwipe?.invoke()
                } else {
                    setExpanded(!isExpanded)
                }
                return true
            }
        }
        return true
    }

    private fun setExpanded(expand: Boolean) {
        val r = root ?: return
        val lp = params ?: return
        if (isExpanded == expand) return
        isExpanded = expand

        collapsedView?.visibility = if (expand) View.GONE else View.VISIBLE
        expandedView?.visibility = if (expand) View.VISIBLE else View.GONE
        applyBackground()

        val screenW = context.resources.displayMetrics.widthPixels
        val cardW = screenW - dp(32)
        lp.width = if (expand) cardW else WindowManager.LayoutParams.WRAP_CONTENT
        lp.y = if (expand) statusBarH + dp(6) else pillTop
        runCatching { windowManager.updateViewLayout(r, lp) }

        r.pivotY = 0f
        r.pivotX = (if (expand) cardW else pillW) / 2f
        r.scaleX = if (expand) 0.55f else 1.12f
        r.scaleY = if (expand) 0.55f else 1.12f
        r.alpha = 0.6f
        r.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(240).setInterpolator(DecelerateInterpolator(1.4f)).start()

        onExpandedChanged?.invoke(expand)
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000).toInt()
        return "%02d:%02d".format(total / 60, total % 60)
    }

    // ------------------------------------------------------------ custom views --

    /** Thin rounded progress bar: faint track, white fill, no thumb. */
    private class ProgressLine(context: Context) : View(context) {
        var fraction = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            val w = width.toFloat()
            paint.color = Color.argb(70, 255, 255, 255)
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, h / 2, h / 2, paint)
            paint.color = Color.WHITE
            rect.set(0f, 0f, maxOf(h, w * fraction), h)
            canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        }
    }

    /** Small animated sound-wave: bounces while playing, rests as low bars when paused. */
    private class WaveView(context: Context) : View(context) {
        var playing = true
            set(value) {
                field = value
                invalidate()
            }
        private val n = 5
        private val cur = FloatArray(n) { 0.3f }
        private val from = FloatArray(n) { 0.3f }
        private val to = FloatArray(n) { Random.nextFloat() * 0.7f + 0.3f }
        private val startAt = LongArray(n) { SystemClock.uptimeMillis() }
        private val durMs = LongArray(n) { Random.nextLong(260, 520) }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255) }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            val w = width.toFloat()
            val h = height.toFloat()
            val barW = w / (n + (n - 1) * 0.9f)
            val gap = barW * 0.9f
            for (i in 0 until n) {
                if (playing) {
                    val t = ((now - startAt[i]).toFloat() / durMs[i]).coerceIn(0f, 1f)
                    cur[i] = from[i] + (to[i] - from[i]) * t
                    if (t >= 1f) {
                        from[i] = cur[i]
                        to[i] = Random.nextFloat() * 0.7f + 0.3f
                        startAt[i] = now
                        durMs[i] = Random.nextLong(260, 520)
                    }
                } else {
                    cur[i] = 0.25f
                }
                val barH = h * cur[i]
                val left = i * (barW + gap)
                rect.set(left, (h - barH) / 2f, left + barW, (h + barH) / 2f)
                canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
            }
            if (playing) postInvalidateOnAnimation()
        }
    }
}
