package com.krish.jaatplayer.ui.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.krish.jaatplayer.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Camera-anchored "Dynamic Island".
 *
 * Collapsed: a small black pill locked to the front-camera cut-out at the top-centre of the
 * screen (album art on the left, animated waveform on the right).
 *
 * Motion (like the iPhone / vivo / Xiaomi / OnePlus islands): it appears as a dot on the
 * camera, then springs out sideways (left and right) into the pill, and the content fades in.
 * Hiding runs the same thing backwards: content fades, the pill pulls back into the camera.
 * The shape is animated with the view outline (not by scaling), so corners stay perfectly
 * round and nothing stretches.
 *
 * Touch: anywhere on the pill opens the player card. Swipe up on the pill hides it for the
 * track; swipe up on the open card (or tap outside it) closes it.
 *
 * Expanded card: artwork, title, artist, waveform, progress, previous / play-pause / next,
 * on either the blurred album art or a gradient built from the album's dominant colours.
 */
class DynamicIslandView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** The visible shape, animated. Per root so a hide animation never fights a new show. */
    private class Body(var w: Float = 0f, var h: Float = 0f, var top: Float = 0f, var maxRadius: Float = 0f)

    private var root: FrameLayout? = null
    private var body = Body()
    private var bodyAnim: ValueAnimator? = null
    private var params: WindowManager.LayoutParams? = null
    private var bgView: View? = null
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
    private var blurredArt: Bitmap? = null
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
    private var cameraD = 0        // diameter of the dot the island grows out of / shrinks into
    // Transparent margin around the collapsed pill: a bigger touch target (taps just off the
    // edge still count) and room for the spring overshoot so it is never clipped flat.
    private var padX = 0
    private var padY = 0
    private val collapsedWindowW: Int get() = pillW + 2 * padX
    private val collapsedWindowH: Int get() = pillH + padY

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
                playEmerge(r)
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

    // Gradient built from the album's dominant colours (start -> mid -> end).
    private var gradStart = Color.rgb(48, 40, 72)
    private var gradEnd = Color.rgb(16, 13, 24)

    fun setArt(bitmap: Bitmap?) {
        artBitmap = bitmap
        extractPaletteColors()
        blurredArt = bitmap?.let { runCatching { buildBlurred(it) }.getOrNull() }
        applyArt()
        applyBackground()
    }

    /**
     * Dominant colours by pixel population: the colour that covers the most of the artwork
     * leads the gradient, the most common clearly-different colour finishes it. (The old
     * code used Palette's dark-muted swatch for the second stop and then multiplied it down
     * to near black, so every card looked like one colour fading to black.)
     */
    private fun extractPaletteColors() {
        val bitmap = artBitmap
        if (bitmap == null) {
            gradStart = Color.rgb(48, 40, 72)
            gradEnd = Color.rgb(16, 13, 24)
            return
        }
        runCatching {
            val n = 48
            val small = Bitmap.createScaledBitmap(bitmap, n, n, true)
            val px = IntArray(n * n)
            small.getPixels(px, 0, n, 0, 0, n, n)

            val weight = FloatArray(4096)
            val sumR = IntArray(4096)
            val sumG = IntArray(4096)
            val sumB = IntArray(4096)
            val cnt = IntArray(4096)
            var total = 0f
            for (p in px) {
                if (Color.alpha(p) < 128) continue
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                val lum = (0.299f * r + 0.587f * g + 0.114f * b)
                // Pure black / white borders shouldn't win just by being big.
                val w = if (lum < 22f || lum > 238f) 0.35f else 1f
                weight[key] += w
                sumR[key] += r; sumG[key] += g; sumB[key] += b; cnt[key]++
                total += w
            }
            fun colorOf(k: Int) = Color.rgb(sumR[k] / cnt[k], sumG[k] / cnt[k], sumB[k] / cnt[k])

            var best = -1
            for (k in 0 until 4096) if (cnt[k] > 0 && (best == -1 || weight[k] > weight[best])) best = k
            if (best == -1) return@runCatching
            val c1 = colorOf(best)

            fun dist(a: Int, b: Int): Float {
                val dr = (Color.red(a) - Color.red(b)).toFloat()
                val dg = (Color.green(a) - Color.green(b)).toFloat()
                val db = (Color.blue(a) - Color.blue(b)).toFloat()
                return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
            }
            var second = -1
            for (k in 0 until 4096) {
                if (cnt[k] == 0 || weight[k] < total * 0.03f) continue
                if (dist(colorOf(k), c1) < 80f) continue
                if (second == -1 || weight[k] > weight[second]) second = k
            }
            val c2 = if (second != -1) colorOf(second) else shade(c1, 0.55f)

            gradStart = tone(c1, 0.50f)
            gradEnd = tone(c2, 0.40f)
        }.onFailure {
            gradStart = Color.rgb(48, 40, 72)
            gradEnd = Color.rgb(16, 13, 24)
        }
    }

    private fun shade(c: Int, f: Float) =
        Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    /** Keeps the colour but caps brightness so white text stays readable, and lifts near-black. */
    private fun tone(c: Int, maxLum: Float): Int {
        var r = Color.red(c).toFloat()
        var g = Color.green(c).toFloat()
        var b = Color.blue(c).toFloat()
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        if (lum > maxLum) {
            val k = maxLum / lum
            r *= k; g *= k; b *= k
        } else if (lum < 0.10f) {
            r += (255f - r) * 0.12f; g += (255f - g) * 0.12f; b += (255f - b) * 0.12f
        }
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    fun setProgress(positionMs: Long, durationMs: Long) {
        val d = if (durationMs > 0) durationMs else 0L
        val p = positionMs.coerceIn(0L, if (d > 0) d else positionMs)
        progressLine?.fraction = if (d > 0) p.toFloat() / d else 0f
        timeCurrent?.text = formatTime(p)
        timeTotal?.text = formatTime(d)
    }

    /** Pulls the island back into the camera, then removes it. */
    fun hide() {
        val r = root ?: return
        val st = body
        val c = collapsedView
        val e = expandedView
        root = null
        isExpanded = false
        collapsedView = null
        expandedView = null
        bgView = null
        waveViews.clear()
        bodyAnim?.cancel()
        bodyAnim = null

        fun remove() { runCatching { windowManager.removeView(r) } }
        if (!r.isAttachedToWindow) { remove(); return }

        e?.animate()?.cancel()
        c?.animate()?.cancel()
        e?.animate()?.alpha(0f)?.setStartDelay(0)?.setDuration(90)?.start()
        c?.animate()?.alpha(0f)?.setStartDelay(0)?.setDuration(130)?.start()
        val cam = cameraD.toFloat()
        runBodyAnimation(
            st, r, cam, cam, (pillH - cam) / 2f,
            durationMs = 300,
            widthInterpolator = AccelerateInterpolator(1.3f),
            heightInterpolator = AccelerateInterpolator(1.0f),
            onEnd = { remove() },
        )
    }

    // ------------------------------------------------------------------ build --

    /**
     * Real-world reference (iPhone 14/15/16 Pro, vivo Funtouch "Fun island", Xiaomi HyperOS
     * island, OnePlus/Realme "capsule"): the idle pill is ~126x37 pt, it widens while media
     * plays (art left, waveform right, camera hole in the middle), and the expanded music card
     * is ~ (screen - 24dp) wide with a ~34 dp corner radius.
     */
    private fun computeMetrics() {
        val res = context.resources
        val sbId = res.getIdentifier("status_bar_height", "dimen", "android")
        statusBarH = if (sbId > 0) res.getDimensionPixelSize(sbId) else dp(28)
        val screenW = res.displayMetrics.widthPixels

        // Defaults when no cut-out is reported: sit centred in the status bar.
        var cutW = dp(12)
        var cutH = dp(12)
        var cutBottom = statusBarH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val cut = windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                if (cut != null && !cut.isEmpty) {
                    cutW = cut.width()
                    cutH = cut.height()
                    cutBottom = cut.bottom
                }
            }
        }

        // Reference island (fractions of screen width so it scales to every phone): 36.4 %
        // wide, 9.0 % tall, 1.55 % from the top, horizontally centred.
        pillW = (screenW * 0.364f).toInt()
        pillH = (screenW * 0.0897f).toInt()
        pillTop = (screenW * 0.0155f).toInt()
        pillX = 0
        padX = dp(10)
        padY = dp(8)

        // The dot it grows out of: the size of the camera hole, never bigger than the pill.
        cameraD = max(dp(10), min(min(cutW, cutH).coerceAtLeast(dp(10)), (pillH * 0.8f).toInt()))

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
            collapsedWindowW,
            collapsedWindowH,
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
            // Many ROMs fade / zoom / slide a freshly added or resized overlay window on their
            // own. That ran on top of this view's animation and read as a glitch.
            windowAnimations = R.style.DynamicIslandNoWindowAnimation
            // Without this the system refuses to draw an overlay over the camera cut-out.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        params = lp

        // ---------------- collapsed: [art]   (camera)   [waveform] ----------------
        val screenW = context.resources.displayMetrics.widthPixels
        val artSize = (screenW * 0.055f).toInt()
        val cArt = roundedImage(artSize, artSize * 0.2f).apply {
            layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply {
                marginStart = (screenW * 0.025f).toInt()
            }
        }
        collapsedArt = cArt

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val cWave = WaveView(context).apply {
            playing = lastIsPlaying
            barColor = Color.rgb(255, 204, 0)
            layoutParams = LinearLayout.LayoutParams((screenW * 0.052f).toInt(), (screenW * 0.04f).toInt())
                .apply { marginEnd = (screenW * 0.027f).toInt() }
        }
        waveViews.add(cWave)

        val collapsed = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(pillW, pillH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            addView(cArt)
            addView(spacer)
            addView(cWave)
        }
        collapsedView = collapsed

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
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            )
            addView(topRow)
            addView(progressRow)
            addView(controls)
        }
        expandedView = expanded

        // Album-art / gradient layer; fades in while the pill grows into the card.
        val bg = View(context).apply {
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        bgView = bg

        val container = FrameLayout(context).apply {
            background = ColorDrawable(Color.BLACK)
            addView(bg)
            addView(collapsed)
            addView(expanded)
        }
        root = container
        isExpanded = false

        // Shape = outline, animated; the window itself never has to resize per frame.
        val st = Body(pillW.toFloat(), pillH.toFloat(), 0f, dp(34).toFloat())
        body = st
        container.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val left = ((view.width - st.w) / 2f).toInt()
                val top = st.top.toInt()
                val radius = min(min(st.w, st.h) / 2f, st.maxRadius)
                outline.setRoundRect(left, top, (left + st.w).toInt(), (top + st.h).toInt(), radius)
            }
        }
        container.clipToOutline = true

        applyBackground()
        applyArt()

        container.setOnTouchListener { _, event -> handleTouch(event) }
    }

    // --------------------------------------------------------------- animation --

    /** Damped spring: overshoots a few percent and settles, like the system islands. */
    private class SpringInterpolator(
        private val damping: Float = 8f,
        private val frequency: Float = 1.6f,
    ) : TimeInterpolator {
        override fun getInterpolation(t: Float): Float {
            if (t >= 1f) return 1f
            return 1f - exp(-damping * t) * cos(2f * PI.toFloat() * frequency * t)
        }
    }

    private fun runBodyAnimation(
        st: Body,
        view: View,
        toW: Float,
        toH: Float,
        toTop: Float,
        durationMs: Long,
        widthInterpolator: TimeInterpolator,
        heightInterpolator: TimeInterpolator,
        onEnd: (() -> Unit)? = null,
    ): ValueAnimator {
        val fromW = st.w
        val fromH = st.h
        val fromTop = st.top
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedFraction
                val tw = widthInterpolator.getInterpolation(t)
                val th = heightInterpolator.getInterpolation(t)
                st.w = fromW + (toW - fromW) * tw
                st.h = fromH + (toH - fromH) * th
                st.top = fromTop + (toTop - fromTop) * th
                view.invalidateOutline()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    st.w = toW; st.h = toH; st.top = toTop
                    view.invalidateOutline()
                    onEnd?.invoke()
                }
            })
        }
        animator.start()
        return animator
    }

    /** Dot on the camera -> springs out left and right -> pill; content fades in after. */
    private fun playEmerge(r: View) {
        val st = body
        val cam = cameraD.toFloat()
        st.w = cam
        st.h = cam
        st.top = (pillH - cam) / 2f
        collapsedView?.alpha = 0f
        r.invalidateOutline()
        bodyAnim?.cancel()
        bodyAnim = runBodyAnimation(
            st, r, pillW.toFloat(), pillH.toFloat(), 0f,
            durationMs = 480,
            widthInterpolator = SpringInterpolator(),
            heightInterpolator = DecelerateInterpolator(1.6f),
        )
        collapsedView?.animate()?.alpha(1f)?.setStartDelay(170)?.setDuration(240)?.start()
    }

    private fun applyBackground() {
        val bg = bgView ?: return
        val blurred = blurredArt
        bg.background = if (expandedStyle == "blurred_art" && blurred != null) {
            val art = BitmapDrawable(context.resources, blurred).apply {
                isFilterBitmap = true
                // A little extra saturation so the blur keeps the album's colour.
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.35f) })
            }
            LayerDrawable(
                arrayOf(
                    art,
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(150, 0, 0, 0)),
                    ),
                ),
            )
        } else {
            val mid = Color.rgb(
                (Color.red(gradStart) + Color.red(gradEnd)) / 2,
                (Color.green(gradStart) + Color.green(gradEnd)) / 2,
                (Color.blue(gradStart) + Color.blue(gradEnd)) / 2,
            )
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(gradStart, mid, gradEnd),
            )
        }
    }

    /**
     * A genuinely blurred copy of the artwork. Heavily downscaled (which already hides detail
     * when stretched back up) and then box-blurred in both directions, three times, which
     * converges on a Gaussian. The previous routine was a horizontal-only pass divided by the
     * wrong amount, so it produced a near-black smear instead of a blur.
     */
    private fun buildBlurred(src: Bitmap): Bitmap {
        val tw = 64
        val th = 36
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = max(tw / src.width.toFloat(), th / src.height.toFloat())
        val dw = src.width * scale
        val dh = src.height * scale
        canvas.drawBitmap(
            src, null,
            RectF((tw - dw) / 2f, (th - dh) / 2f, (tw + dw) / 2f, (th + dh) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        var a = IntArray(tw * th)
        var b = IntArray(tw * th)
        out.getPixels(a, 0, tw, 0, 0, tw, th)
        repeat(3) {
            boxBlurPass(a, b, tw, th, 4, horizontal = true)
            boxBlurPass(b, a, tw, th, 4, horizontal = false)
        }
        out.setPixels(a, 0, tw, 0, 0, tw, th)
        return out
    }

    private fun boxBlurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = (radius * 2 + 1).toFloat()
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var r = 0; var g = 0; var bl = 0
                for (k in -radius..radius) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = if (horizontal) src[line * w + j] else src[j * w + line]
                    r += (p shr 16) and 0xff
                    g += (p shr 8) and 0xff
                    bl += p and 0xff
                }
                val px = (0xff shl 24) or ((r / div).toInt() shl 16) or ((g / div).toInt() shl 8) or (bl / div).toInt()
                if (horizontal) dst[line * w + i] = px else dst[i * w + line] = px
            }
        }
    }

    private fun applyArt() {
        listOf(collapsedArt, expandedArt).forEach { iv ->
            iv?.setImageBitmap(artBitmap)
        }
    }

    // ------------------------------------------------------------------ touch --

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var gestureDone = false

    /**
     * The whole island is one touch target. The listener lives on the root and the collapsed
     * pill's children are not clickable, so a tap ANYWHERE on the pill (art, waveform, the dead
     * space over the camera, the transparent margin) opens the card.
     *
     * Why taps used to be missed: the island sits in the status-bar strip, where the system's
     * pull-down-shade gesture also listens. A normal finger tap drifts a hair downward, the
     * system claims the touch, and this window receives ACTION_CANCEL instead of ACTION_UP,
     * so nothing happened unless the tap landed perfectly still. The old code also treated any
     * 8dp wobble as "not a tap". A short press that is cancelled by the system is now treated
     * as the tap it was, and the movement tolerance follows the system touch slop.
     */
    private fun handleTouch(event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (isExpanded) setExpanded(false)
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                moved = false
                gestureDone = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureDone) return true
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (dx * dx + dy * dy > slop * slop) moved = true
                // Swipe up acts as soon as it is clearly a swipe (the system may take the
                // touch away a moment later).
                if (dy < -dp(24)) {
                    gestureDone = true
                    if (isExpanded) setExpanded(false) else onDismissedBySwipe?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureDone && !moved && !isExpanded) openFromTap()
                gestureDone = true
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                val shortPress = event.eventTime - downTime < 400L
                if (!gestureDone && !moved && shortPress && !isExpanded) openFromTap()
                gestureDone = true
                return true
            }
        }
        return false
    }

    private fun openFromTap() {
        root?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        setExpanded(true)
    }

    private fun setExpanded(expand: Boolean) {
        val r = root ?: return
        val lp = params ?: return
        val collapsed = collapsedView ?: return
        val expanded = expandedView ?: return
        val bg = bgView ?: return
        if (isExpanded == expand) return
        isExpanded = expand

        val screenW = context.resources.displayMetrics.widthPixels
        val cardW = minOf(screenW - dp(24), dp(420))
        bodyAnim?.cancel()
        collapsed.animate().cancel()
        expanded.animate().cancel()
        bg.animate().cancel()

        if (expand) {
            expanded.visibility = View.VISIBLE
            expanded.measure(
                View.MeasureSpec.makeMeasureSpec(cardW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val cardH = expanded.measuredHeight

            lp.width = cardW
            lp.height = cardH
            lp.x = 0
            lp.y = pillTop

            // The window resize is asynchronous. Starting the animation straight away ran its
            // first frames inside the OLD, pill-sized window (clipped, jumping), which was the
            // visible glitch. Wait until the window has actually been laid out at card size.
            var started = false
            val start = {
                if (!started && root === r && isExpanded) {
                    started = true
                    bg.animate().alpha(1f).setStartDelay(0).setDuration(260).start()
                    collapsed.animate().alpha(0f).setStartDelay(0).setDuration(110).start()
                    expanded.animate().alpha(1f).setStartDelay(120).setDuration(240).start()
                    bodyAnim = runBodyAnimation(
                        body, r, cardW.toFloat(), cardH.toFloat(), 0f,
                        durationMs = 420,
                        widthInterpolator = DecelerateInterpolator(2.2f),
                        heightInterpolator = DecelerateInterpolator(2.0f),
                    )
                }
            }
            r.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View, l: Int, t: Int, rr: Int, b: Int, ol: Int, ot: Int, orr: Int, ob: Int,
                ) {
                    if (rr - l >= cardW && b - t >= cardH) {
                        v.removeOnLayoutChangeListener(this)
                        start()
                    }
                }
            })
            r.postDelayed({ start() }, 160) // safety net if the layout callback never fires
            runCatching { windowManager.updateViewLayout(r, lp) }.onFailure { start() }
        } else {
            expanded.animate().alpha(0f).setStartDelay(0).setDuration(110).start()
            bg.animate().alpha(0f).setStartDelay(100).setDuration(260).start()
            collapsed.animate().alpha(1f).setStartDelay(190).setDuration(200).start()
            bodyAnim = runBodyAnimation(
                body, r, pillW.toFloat(), pillH.toFloat(), 0f,
                durationMs = 340,
                widthInterpolator = DecelerateInterpolator(1.6f),
                heightInterpolator = DecelerateInterpolator(1.6f),
                onEnd = {
                    if (root === r && !isExpanded) {
                        // Everything but the pill is invisible by now, so shrinking the
                        // window back is not seen.
                        expanded.visibility = View.GONE
                        lp.width = collapsedWindowW
                        lp.height = collapsedWindowH
                        runCatching { windowManager.updateViewLayout(r, lp) }
                    }
                },
            )
        }

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
