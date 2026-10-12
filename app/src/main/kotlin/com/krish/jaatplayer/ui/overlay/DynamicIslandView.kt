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
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import androidx.palette.graphics.Palette
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

    // Metrics of the camera area, computed when the views are built.
    private var statusBarH = 0
    private var pillW = 0
    private var pillH = 0
    private var pillTop = 0
    private var pillX = 0          // horizontal offset so the pill sits exactly on the camera hole
    private var cardTopPad = 0     // expanded card top padding (content starts below the camera)

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

    var expandedStyle: String = "blurred_art"

    fun updateExpandedStyle(newStyle: String) {
        val normalized = newStyle.lowercase()
        if (expandedStyle == normalized) return
        expandedStyle = normalized
        applyBackground()
    }

    private var primaryBgColor = Color.argb(244, 30, 25, 45)
    private var secondaryBgColor = Color.argb(250, 12, 10, 18)

    fun setArt(bitmap: Bitmap?) {
        artBitmap = bitmap
        extractPaletteColors()
        applyArt()
        applyBackground()
    }

    private fun extractPaletteColors() {
        val bitmap = artBitmap
        if (bitmap == null) {
            primaryBgColor = Color.argb(244, 30, 25, 45)
            secondaryBgColor = Color.argb(250, 12, 10, 18)
            return
        }
        runCatching {
            val palette = Palette.from(bitmap).generate()
            // Dominant swatch has the highest pixel count (population) in the album art (e.g. main background)
            val dominantSwatch = palette.dominantSwatch ?: palette.swatches.maxByOrNull { it.population }
            val rawPrimary = dominantSwatch?.rgb ?: palette.vibrantSwatch?.rgb ?: Color.argb(244, 30, 25, 45)

            val darkSwatch = palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch
            val rawSecondary = darkSwatch?.rgb ?: rawPrimary

            val r1 = (Color.red(rawPrimary) * 0.85f).toInt().coerceIn(0, 255)
            val g1 = (Color.green(rawPrimary) * 0.85f).toInt().coerceIn(0, 255)
            val b1 = (Color.blue(rawPrimary) * 0.85f).toInt().coerceIn(0, 255)
            primaryBgColor = Color.argb(244, r1, g1, b1)

            val r2 = (Color.red(rawSecondary) * 0.45f).toInt().coerceIn(0, 255)
            val g2 = (Color.green(rawSecondary) * 0.45f).toInt().coerceIn(0, 255)
            val b2 = (Color.blue(rawSecondary) * 0.45f).toInt().coerceIn(0, 255)
            secondaryBgColor = Color.argb(252, r2, g2, b2)
        }.onFailure {
            primaryBgColor = Color.argb(244, 30, 25, 45)
            secondaryBgColor = Color.argb(250, 12, 10, 18)
        }
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

    /**
     * Real-world reference (iPhone 14/15/16 Pro, vivo Funtouch "Fun island", Xiaomi HyperOS
     * island, OnePlus/Realme "capsule"): the idle pill is ~126x37 pt, it widens to ~165-190 pt
     * while media plays (art left, waveform right, camera hole in the middle), and the expanded
     * music card is ~ (screen - 24dp) wide and ~170 dp tall with a ~34 dp corner radius.
     * The pill is always vertically centred on the camera hole and as wide as needed so that
     * the artwork and the waveform stay clear of it.
     */
    private fun computeMetrics() {
        val res = context.resources
        val sbId = res.getIdentifier("status_bar_height", "dimen", "android")
        statusBarH = if (sbId > 0) res.getDimensionPixelSize(sbId) else dp(28)
        val screenW = res.displayMetrics.widthPixels

        // Defaults when no cut-out is reported: sit centred in the status bar.
        var cutW = dp(12)
        var cutH = dp(12)
        var cutCenterX = screenW / 2
        var cameraCenterY = statusBarH / 2
        var cutBottom = statusBarH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val cut = windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                if (cut != null && !cut.isEmpty) {
                    cutW = cut.width()
                    cutH = cut.height()
                    cutCenterX = cut.centerX()
                    cameraCenterY = cut.centerY()
                    cutBottom = cut.bottom
                }
            }
        }

        // Reference island (measured from the target screenshot, as a fraction of screen
        // width so it scales to every phone): 36.4 % wide, 9.0 % tall, 1.55 % from the top,
        // horizontally centred. Art is a 5.5 % rounded square, waveform 5.2 % x 4 % in yellow.
        pillW = (screenW * 0.364f).toInt()
        pillH = (screenW * 0.0897f).toInt()
        pillTop = (screenW * 0.0155f).toInt()
        pillX = 0

        // Expanded card starts at the pill's top edge; its content starts below the camera.
        cardTopPad = max(dp(20), cutBottom - pillTop + dp(8))
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

    private fun attachCollapsedTouchListener(view: View) {
        var startY = 0f
        var startX = 0f
        var isClick = true
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startX = event.rawX
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    val dx = event.rawX - startX
                    if (dy * dy + dx * dx > dp(8) * dp(8)) {
                        isClick = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - startY
                    if (dy < -dp(18)) {
                        onDismissedBySwipe?.invoke()
                    } else if (isClick || Math.abs(dy) < dp(10)) {
                        setExpanded(!isExpanded)
                    }
                    true
                }
                else -> false
            }
        }
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
            pillW,
            pillH,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = pillX
            y = pillTop
            // Without this the system refuses to draw an overlay over the camera cut-out.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        params = lp

        // ---------------- collapsed: [art]   (camera)   [waveform] ----------------
        val artSize = (context.resources.displayMetrics.widthPixels * 0.055f).toInt()
        val cArt = roundedImage(artSize, artSize * 0.2f).apply {
            layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply {
                marginStart = (context.resources.displayMetrics.widthPixels * 0.025f).toInt()
            }
            isClickable = false
            isFocusable = false
        }
        collapsedArt = cArt

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            isClickable = false
            isFocusable = false
        }
        val cWave = WaveView(context).apply {
            playing = lastIsPlaying
            val sw = context.resources.displayMetrics.widthPixels
            barColor = Color.rgb(255, 204, 0)
            layoutParams = LinearLayout.LayoutParams((sw * 0.052f).toInt(), (sw * 0.04f).toInt())
                .apply { marginEnd = (sw * 0.027f).toInt() }
            isClickable = false
            isFocusable = false
        }
        waveViews.add(cWave)

        val collapsed = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(pillW, pillH)
            addView(cArt)
            addView(spacer)
            addView(cWave)
        }
        collapsedView = collapsed
        attachCollapsedTouchListener(collapsed)

        // ---------------- expanded card ----------------
        val eArt = roundedImage(dp(52), dp(12).toFloat())
        expandedArt = eArt

        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        expandedTitle = title
        val artist = TextView(context).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        expandedArtist = artist

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
            addView(title)
            addView(artist)
        }

        val eWave = WaveView(context).apply {
            playing = lastIsPlaying
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(20))
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
            textSize = 12f
            text = formatTime(0)
        }
        timeCurrent = cur
        val total = TextView(context).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 12f
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
            ).apply { topMargin = dp(10) }
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

        val prev = flatButton(R.drawable.skip_previous, dp(48), dp(10)) { onPrevious?.invoke() }
        val next = flatButton(R.drawable.skip_next, dp(48), dp(10)) { onNext?.invoke() }
        val play = ImageButton(context).apply {
            setImageResource(if (lastIsPlaying) R.drawable.pause else R.drawable.play)
            setColorFilter(Color.rgb(43, 36, 56))
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = dp(15)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                marginStart = dp(26)
                marginEnd = dp(26)
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
            ).apply { topMargin = dp(4) }
            addView(prev)
            addView(play)
            addView(next)
        }

        val expanded = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), cardTopPad, dp(20), dp(14))
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
        if (isExpanded) {
            if (expandedStyle == "blurred_art" && artBitmap != null) {
                val blurred = createDarkBlurredBitmap(artBitmap!!)
                val bitmapDrawable = BitmapDrawable(context.resources, blurred)
                r.background = LayerDrawable(
                    arrayOf(
                        bitmapDrawable,
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.argb(175, 8, 6, 12))
                        }
                    )
                )
            } else {
                r.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(primaryBgColor, secondaryBgColor)
                ).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(34).toFloat()
                }
            }
            r.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(34).toFloat())
                }
            }
            r.clipToOutline = true
        } else {
            r.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = pillH / 2f
                setColor(Color.BLACK)
            }
            r.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, pillH / 2f)
                }
            }
            r.clipToOutline = true
        }
    }

    private fun createDarkBlurredBitmap(source: Bitmap): Bitmap {
        return runCatching {
            val width = 60
            val height = (source.height.toFloat() / source.width.toFloat() * width).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(source, width, height, true)
            blurBitmap(small, 12)
        }.getOrDefault(source)
    }

    private fun blurBitmap(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)

        val r = radius.coerceAtLeast(1)
        val wm = w - 1
        val hm = h - 1
        val div = r + r + 1

        val divsum = ((div + 1) shr 1) * ((div + 1) shr 1)
        val dv = IntArray(256 * divsum) { it / divsum }

        var yw = 0
        var yi = 0
        val vmin = IntArray(Math.max(w, h))

        for (y in 0 until h) {
            var rsum = 0
            var gsum = 0
            var bsum = 0
            for (i in -r..r) {
                val p = pix[yi + Math.min(wm, Math.max(i, 0))]
                rsum += (p shr 16) and 0xff
                gsum += (p shr 8) and 0xff
                bsum += p and 0xff
            }
            for (x in 0 until w) {
                pix[yi] = (pix[yi] and 0xff000000.toInt()) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                if (y == 0) vmin[x] = Math.min(x + r + 1, wm)
                val p1 = pix[yw + vmin[x]]
                val p2 = pix[yw + Math.max(x - r, 0)]
                rsum += ((p1 shr 16) and 0xff) - ((p2 shr 16) and 0xff)
                gsum += ((p1 shr 8) and 0xff) - ((p2 shr 8) and 0xff)
                bsum += (p1 and 0xff) - (p2 and 0xff)
                yi++
            }
            yw += w
        }
        val blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        blurred.setPixels(pix, 0, w, 0, 0, w, h)
        return blurred
    }

    private fun applyArt() {
        listOf(collapsedArt, expandedArt).forEach { iv ->
            iv?.setImageBitmap(artBitmap)
        }
    }

    // ------------------------------------------------------------------ touch --

    private fun handleTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (isExpanded) setExpanded(false)
            return true
        }
        return false
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
        val cardW = minOf(screenW - dp(24), dp(420))
        lp.width = if (expand) cardW else pillW
        lp.height = if (expand) WindowManager.LayoutParams.WRAP_CONTENT else pillH
        lp.x = if (expand) 0 else pillX
        lp.y = pillTop
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
        var barColor = Color.argb(235, 255, 255, 255)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            paint.color = barColor
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
