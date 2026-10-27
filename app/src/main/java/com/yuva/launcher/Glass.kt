package com.yuva.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Colours derived from the current wallpaper. Everything on screen reads from here. */
data class LauncherTheme(
    val accent: Int,      // vivid, used for focus ring / glow / subtitle
    val accentDeep: Int,  // darker sibling for plates
    val tint: Int,        // dark body colour of the glass
    val luma: Float = 0.3f // average brightness 0..1, drives how hard the legibility veils bite
) {
    /** Deck wallpapers carry a hand-picked accent; keep the extracted tint/luma. */
    fun withAccent(a: Int): LauncherTheme {
        val hsv = FloatArray(3); Color.colorToHSV(a, hsv)
        hsv[2] *= 0.55f
        return copy(accent = a, accentDeep = Color.HSVToColor(hsv))
    }

    companion object {
        val DEFAULT = LauncherTheme(0xFFF04438.toInt(), 0xFF7A1F22.toInt(), 0xFF11131A.toInt())
        private const val OBSIDIAN = 0xFF08090D.toInt()

        /** Hyprland/pywal-style: pick the dominant vivid hue, keep the average as glass body. */
        fun from(bmp: Bitmap): LauncherTheme {
            val w = 64; val h = 36
            val s = Bitmap.createScaledBitmap(bmp, w, h, true)
            val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
            if (s !== bmp) s.recycle()
            val hsv = FloatArray(3)
            val wt = FloatArray(24); val ws = FloatArray(24)
            var r = 0L; var g = 0L; var b = 0L; var vSum = 0f
            for (p in px) {
                r += Color.red(p); g += Color.green(p); b += Color.blue(p)
                Color.colorToHSV(p, hsv)
                vSum += hsv[2]
                val weight = hsv[1] * hsv[2] * hsv[2]
                val bin = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
                wt[bin] += weight; ws[bin] += hsv[1] * weight
            }
            val n = px.size
            var best = 0
            for (i in 1 until 24) if (wt[i] > wt[best]) best = i
            if (wt[best] < 0.01f) return DEFAULT
            val hue = best * 15f + 7.5f
            val sat = (ws[best] / wt[best] + 0.25f).coerceIn(0.5f, 0.85f)
            val accent = Color.HSVToColor(floatArrayOf(hue, sat, 0.98f))
            val deep = Color.HSVToColor(floatArrayOf(hue, (sat + 0.1f).coerceAtMost(0.95f), 0.52f))
            Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            val body = Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 0.85f).coerceIn(0.25f, 0.65f), 0.17f))
            // pull the glass body toward Obsidian so every wallpaper keeps the same dark, premium material
            val tint = Color.rgb(
                ((Color.red(body) + Color.red(OBSIDIAN)) / 2), ((Color.green(body) + Color.green(OBSIDIAN)) / 2), ((Color.blue(body) + Color.blue(OBSIDIAN)) / 2))
            return LauncherTheme(accent, deep, tint, vSum / n)
        }
    }
}

/** Process-wide wallpaper state: theme + a tiny pre-blurred copy used as the glass backdrop. */
object GlassStore {
    @Volatile var theme: LauncherTheme = LauncherTheme.DEFAULT
    @Volatile var backdrop: Bitmap? = null
    @Volatile var version = 0

    private var key: String? = null
    private var full: Bitmap? = null

    /** Decodes (and caches by path+mtime) the wallpaper, then derives theme + blurred backdrop. */
    fun load(ctx: Context, prefs: Prefs): Pair<Bitmap?, Boolean> {
        val (f, accent) = BundledWalls.resolve(ctx, prefs)
        val k = "${f.absolutePath}:${f.lastModified()}:$accent"
        if (k == key && full != null) return full to false
        val bmp = decodeFit(f, 1280) ?: return full to false
        full = bmp; key = k
        theme = LauncherTheme.from(bmp).let { if (accent != 0) it.withAccent(accent) else it }
        backdrop = blur(bmp)
        version++
        return bmp to true
    }

    /** Decodes to the launcher's real render width (1280). Larger sources are downsampled, then exactly scaled, so a 1920px deck image costs 3.7 MB, not 8. */
    fun decodeFit(f: File, width: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= width) sample *= 2
        val raw = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (raw.width <= width) return raw
        val h = (raw.height * width.toFloat() / raw.width).toInt()
        return Bitmap.createScaledBitmap(raw, width, h, true).also { if (it !== raw) raw.recycle() }
    }

    /** 1/10 scale, three box-blur passes. Drawn back up with bilinear filtering = soft frosted glass for ~0 GPU cost. */
    private fun blur(src: Bitmap): Bitmap {
        val w = 128; val h = 72
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        val tmp = IntArray(w * h)
        repeat(3) { boxPass(px, tmp, w, h, 5, true); boxPass(tmp, px, w, h, 5, false) }
        val hsv = FloatArray(3)
        for (i in px.indices) {                       // richer, slightly brighter glass body
            Color.colorToHSV(px[i], hsv)
            hsv[1] = (hsv[1] * 1.35f).coerceAtMost(1f); hsv[2] = (hsv[2] * 1.08f).coerceAtMost(1f)
            px[i] = Color.HSVToColor(hsv)
        }
        val soft = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(soft, 320, 180, true).also { if (it !== soft) soft.recycle() }
    }

    private fun boxPass(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int, horiz: Boolean) {
        val len = if (horiz) w else h
        val lines = if (horiz) h else w
        val d = 2 * r + 1
        for (l in 0 until lines) {
            for (c in 0..2) {
                val sh = 16 - 8 * c
                var acc = 0
                for (i in -r..r) acc += (inp[idx(l, i.coerceIn(0, len - 1), w, horiz)] shr sh) and 255
                for (i in 0 until len) {
                    out[idx(l, i, w, horiz)] = if (c == 0) 0xFF000000.toInt() or ((acc / d) shl sh)
                    else out[idx(l, i, w, horiz)] or ((acc / d) shl sh)
                    val add = inp[idx(l, (i + r + 1).coerceAtMost(len - 1), w, horiz)]
                    val sub = inp[idx(l, (i - r).coerceAtLeast(0), w, horiz)]
                    acc += ((add shr sh) and 255) - ((sub shr sh) and 255)
                }
            }
        }
    }
    private fun idx(line: Int, i: Int, w: Int, horiz: Boolean) = if (horiz) line * w + i else i * w + line
}

/** The three wallpapers that ship in the APK. Copied to filesDir once so the picker treats them like any other image. */
object BundledWalls {
    val all = listOf(
        "Water Breathing" to R.drawable.wall_water,
        "Flame Dawn" to R.drawable.wall_flame,
        "Sakura Dusk" to R.drawable.wall_sakura
    )
    fun dir(ctx: Context) = File(ctx.filesDir, "bundled").apply { mkdirs() }

    fun ensure(ctx: Context): List<File> = all.map { (name, res) ->
        val f = File(dir(ctx), name.replace(' ', '_') + ".jpg")
        if (!f.exists() || f.length() == 0L) {
            ctx.resources.openRawResource(res).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        f
    }

    /** Saved choice -> deck default (Crimson Eclipse) -> bundled original. Missing files always fall through, never blank. */
    fun resolve(ctx: Context, prefs: Prefs): Pair<File, Int> {
        val chosen = prefs.wallpaperPath?.let(::File)
        if (chosen != null && chosen.exists() && chosen.length() > 0) return chosen to prefs.wallpaperAccent
        Deck.defaultItem(ctx)?.let { return it.file to it.accent }
        return ensure(ctx).first() to 0
    }
}

/**
 * Liquid-glass surface. Draws, bottom to top:
 *   focus glow → blurred wallpaper (aligned to its on-screen position so it reads as refraction)
 *   → tint → diagonal sheen → rim light → accent ring + sweep highlight on focus.
 * [focus] and [sweep] are animated from outside.
 */
class GlassDrawable(
    private val view: View,
    private val radius: Float,
    private val strength: Float = 1f,
    private val circle: Boolean = false,
    private val density: Float = view.resources.displayMetrics.density
) : Drawable() {

    var focus = 0f
        set(v) { field = v; invalidateSelf() }
    var sweep = -1f
        set(v) { field = v; invalidateSelf() }

    private var focusAnim: android.animation.ValueAnimator? = null
    private var sweepAnim: android.animation.ValueAnimator? = null

    /** Eases [focus] to [to]; cancels any in-flight animation so rapid D-pad presses never fight. */
    fun animateFocus(to: Float, ms: Long) {
        focusAnim?.cancel()
        focusAnim = android.animation.ValueAnimator.ofFloat(focus, to).apply {
            duration = ms
            interpolator = android.view.animation.DecelerateInterpolator(1.4f)
            addUpdateListener { focus = it.animatedValue as Float }
            start()
        }
    }

    /** One diagonal light pass across the surface — the "liquid" glint when something gains focus. */
    fun playSweep(ms: Long) {
        sweepAnim?.cancel()
        sweepAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { sweep = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { sweep = -1f }
                override fun onAnimationCancel(a: android.animation.Animator) { sweep = -1f }
            })
            start()
        }
    }

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.25f * density }
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * density }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweepP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val m = Matrix()
    private val loc = IntArray(2)
    private val clip = android.graphics.Path()
    private val src = android.graphics.Rect()
    private var baked: Bitmap? = null
    private var bakedVer = -1
    private var bakedX = 0
    private var bakedY = 0
    private val maskP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val inP = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN) }

    private fun rebake(bd: Bitmap, bw: Int, bh: Int, vs: Float) {
        val root = view.rootView
        val sx = max(root.width, 1).toFloat() / bd.width
        val sy = max(root.height, 1).toFloat() / bd.height
        val l = (loc[0] / sx).toInt().coerceIn(0, bd.width - 2)
        val t = (loc[1] / sy).toInt().coerceIn(0, bd.height - 2)
        val rr = Math.ceil(((loc[0] + bw * vs) / sx).toDouble()).toInt().coerceIn(l + 1, bd.width)
        val bb = Math.ceil(((loc[1] + bh * vs) / sy).toDouble()).toInt().coerceIn(t + 1, bd.height)
        src.set(l, t, rr, bb)
        var bmp = baked
        if (bmp == null || bmp.width != bw || bmp.height != bh) { bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); baked = bmp }
        bmp.eraseColor(0)
        val cv = Canvas(bmp)
        cv.drawPath(clip, maskP)
        cv.drawBitmap(bd, src, RectF(0f, 0f, bw.toFloat(), bh.toFloat()), inP)
        bakedVer = GlassStore.version; bakedX = loc[0]; bakedY = loc[1]
    }
    private var sweepShader: LinearGradient? = null
    private val sweepW get() = rect.width() * 0.7f

    override fun onBoundsChange(b: android.graphics.Rect) {
        rect.set(b)
        val w = rect.width(); val h = rect.height()
        val cr = if (circle) min(w, h) / 2f else radius
        clip.reset(); clip.addRoundRect(0f, 0f, w, h, cr, cr, android.graphics.Path.Direction.CW)
        sheenP.shader = LinearGradient(0f, 0f, w * 0.9f, h * 1.1f,
            intArrayOf(0x3DFFFFFF, 0x0FFFFFFF, 0x00FFFFFF, 0x14FFFFFF), floatArrayOf(0f, 0.38f, 0.7f, 1f), Shader.TileMode.CLAMP)
        rimP.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(0x99FFFFFF.toInt(), 0x1AFFFFFF, 0x0DFFFFFF, 0x59FFFFFF), floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
        sweepShader = LinearGradient(0f, 0f, sweepW, sweepW * 0.35f,
            intArrayOf(0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        if (rect.isEmpty) return
        val t = GlassStore.theme
        val f = focus
        val r = if (circle) min(rect.width(), rect.height()) / 2f else radius
        val save = c.save()
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val local = RectF(0f, 0f, rect.width(), rect.height())

        if (f > 0.01f) {                       // soft accent bloom, stacked rounded rects
            for (i in 3 downTo 1) {
                val e = i * 8f * density
                glowP.color = withAlpha(t.accent, (0.13f * f * (4 - i) / 3f))
                c.drawRoundRect(-e, -e, local.right + e, local.bottom + e, r + e, r + e, glowP)
            }
        }

        val bd = GlassStore.backdrop
        if (bd != null) {
            // The refraction is baked into a tile-sized rounded bitmap (cheap CPU) and re-baked only when the
            // tile has moved >24px, so each frame is one tiny drawBitmap. Live shaders/clips cost 2x on this GPU.
            view.getLocationInWindow(loc)
            val vs = if (view.scaleX == 0f) 1f else view.scaleX
            val bw = rect.width().toInt().coerceAtLeast(1); val bh = rect.height().toInt().coerceAtLeast(1)
            val stale = baked == null || bakedVer != GlassStore.version || baked!!.width != bw || baked!!.height != bh ||
                abs(loc[0] - bakedX) > 24 || abs(loc[1] - bakedY) > 24
            if (stale) rebake(bd, bw, bh, vs)
            c.drawBitmap(baked!!, 0f, 0f, null)
        }

        tintP.color = withAlpha(mix(t.tint, Color.WHITE, 0.10f * f), (0.46f - 0.10f * f) * strength + 0.04f)
        c.drawRoundRect(local, r, r, tintP)
        if (f > 0.01f) {
            tintP.color = withAlpha(t.accent, 0.16f * f)
            c.drawRoundRect(local, r, r, tintP)
        }
        c.drawRoundRect(local, r, r, sheenP)

        val sw = sweep
        if (sw in 0f..1f && sweepShader != null) {
            m.setTranslate(-sweepW + (local.width() + sweepW) * sw, 0f)
            sweepShader!!.setLocalMatrix(m); sweepP.shader = sweepShader
            c.drawRoundRect(local, r, r, sweepP)
        }

        val half = rimP.strokeWidth / 2f
        c.drawRoundRect(half, half, local.right - half, local.bottom - half, r - half, r - half, rimP)
        if (f > 0.01f) {
            ringP.color = withAlpha(t.accent, f)
            val hr = ringP.strokeWidth / 2f
            c.drawRoundRect(hr, hr, local.right - hr, local.bottom - hr, r - hr, r - hr, ringP)
        }
        c.restoreToCount(save)
    }

    private fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Wallpaper + dark veil container for the secondary screens, so they share the home screen's world. */
fun themedScreen(ctx: Context, content: View): android.widget.FrameLayout {
    val frame = android.widget.FrameLayout(ctx)
    val (bmp, _) = GlassStore.load(ctx, Prefs(ctx))
    frame.addView(android.widget.ImageView(ctx).apply {
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; setImageBitmap(bmp)
    }, android.widget.FrameLayout.LayoutParams(-1, -1))
    frame.addView(View(ctx).apply { setBackgroundColor(0xB8000000.toInt()) }, android.widget.FrameLayout.LayoutParams(-1, -1))
    frame.addView(content, android.widget.FrameLayout.LayoutParams(-1, -1))
    return frame
}
