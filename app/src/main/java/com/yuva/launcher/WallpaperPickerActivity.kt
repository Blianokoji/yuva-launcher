package com.yuva.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * The wallpaper deck. Browsing a card previews it full-screen (crossfade, decoded off the UI thread);
 * OK or "Apply wallpaper" applies it; BACK leaves without changing anything. The curated deck, the bundled
 * originals and anything dropped in Downloads/Pictures/DCIM live in one row, grouped by category.
 */
class WallpaperPickerActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var items: List<DeckItem>
    private lateinit var baseView: ImageView
    private lateinit var topView: ImageView
    private lateinit var catView: TextView
    private lateinit var titleView: TextView
    private lateinit var moodView: TextView
    private lateinit var creditView: TextView
    private lateinit var applyBtn: View
    private lateinit var row: RecyclerView
    private lateinit var adapter: CardAdapter

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val previews = LruCache<String, Bitmap>(3)
    private val thumbs = LruCache<String, Bitmap>(24)
    private var current: DeckItem? = null
    private var appliedPath: String? = null
    private var pending: Runnable? = null
    private var shownPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deck)
        prefs = Prefs(this)
        baseView = findViewById(R.id.bg_base); topView = findViewById(R.id.bg_top)
        catView = findViewById(R.id.deck_category); titleView = findViewById(R.id.deck_title)
        moodView = findViewById(R.id.deck_mood); creditView = findViewById(R.id.deck_credit)
        applyBtn = findViewById(R.id.deck_apply); row = findViewById(R.id.deck_row)

        // Android 13 gates Downloads/Pictures behind READ_MEDIA_IMAGES; without it only the curated deck + originals show
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 7)
        }
        items = Deck.all(this)
        appliedPath = BundledWalls.resolve(this, prefs).first.absolutePath

        val d = resources.displayMetrics.density
        val glass = GlassDrawable(applyBtn, 26 * d, strength = 0.8f)
        applyBtn.background = glass
        applyBtn.setOnFocusChangeListener { _, has ->
            glass.animateFocus(if (has) 1f else 0f, 200)
            if (has) glass.playSweep(420)
            applyBtn.animate().scaleX(if (has) 1.05f else 1f).scaleY(if (has) 1.05f else 1f).setDuration(180).start()
        }
        applyBtn.setOnClickListener { current?.let { apply(it); finish() } }

        row.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        row.itemAnimator = null
        adapter = CardAdapter()
        row.adapter = adapter

        val start = items.indexOfFirst { it.file.absolutePath == appliedPath }.coerceAtLeast(0)
        row.scrollToPosition(start)
        row.post { adapter.focus(start) }
        if (items.isNotEmpty()) show(items[start], instant = true)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 7 && res.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) recreate()
    }

    override fun onDestroy() { pending?.let(ui::removeCallbacks); io.shutdownNow(); super.onDestroy() }

    // ---------------------------------------------------------------- preview

    private fun show(item: DeckItem, instant: Boolean = false) {
        current = item
        catView.text = item.category
        catView.setTextColor(if (item.accent != 0) item.accent else GlassStore.theme.accent)
        titleView.text = item.title
        moodView.text = item.mood
        creditView.text = listOfNotNull(item.credit, item.rights).joinToString("\n")
        pending?.let(ui::removeCallbacks)
        // wait for the D-pad to settle so a fast run across the row doesn't decode every image
        pending = Runnable { loadPreview(item, instant) }.also { ui.postDelayed(it, if (instant) 0 else 140) }
    }

    private fun loadPreview(item: DeckItem, instant: Boolean) {
        val path = item.file.absolutePath
        previews.get(path)?.let { crossfade(it, path, instant); return }
        io.execute {
            val bmp = GlassStore.decodeFit(item.file, 1280)
            ui.post {
                if (bmp == null || isDestroyed) return@post
                previews.put(path, bmp)
                if (current?.file?.absolutePath == path) crossfade(bmp, path, instant)
            }
        }
    }

    private fun crossfade(bmp: Bitmap, path: String, instant: Boolean) {
        if (path == shownPath) return
        shownPath = path
        topView.animate().cancel()
        if (instant) { baseView.setImageBitmap(bmp); topView.alpha = 0f; return }
        topView.setImageBitmap(bmp); topView.alpha = 0f
        topView.animate().alpha(1f).setDuration(420).setInterpolator(DecelerateInterpolator()).withEndAction {
            baseView.setImageBitmap(bmp); topView.alpha = 0f
        }.start()
    }

    // ---------------------------------------------------------------- apply

    private fun apply(item: DeckItem) {
        if (item.category != "Yours") {
            prefs.setWallpaper(item.file.absolutePath, item.accent)
            appliedPath = item.file.absolutePath
        } else {
            // user images are copied so deleting the original from Downloads can't blank the launcher
            val dst = File(filesDir, "wallpaper.img")
            item.file.inputStream().use { i -> FileOutputStream(dst).use { o -> i.copyTo(o) } }
            prefs.wallpaperPath = dst.absolutePath
            appliedPath = dst.absolutePath
        }
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Applied: ${item.title}", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- cards

    private inner class CardAdapter : RecyclerView.Adapter<CardAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card: FrameLayout = v.findViewById(R.id.tile_card)
            val clip: View = v.findViewById(R.id.deck_clip)
            val img: ImageView = v.findViewById(R.id.tile_icon)
            val label: TextView = v.findViewById(R.id.tile_label)
            val tag: TextView = v.findViewById(R.id.tile_tag)
            lateinit var glass: GlassDrawable
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val h = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_deck, parent, false))
            val d = parent.resources.displayMetrics.density
            h.glass = GlassDrawable(h.card, 18 * d)
            h.card.background = h.glass
            h.clip.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, 15 * d) }
            }
            h.clip.clipToOutline = true
            return h
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            h.label.text = item.title
            h.tag.visibility = if (item.file.absolutePath == appliedPath) View.VISIBLE else View.GONE
            h.tag.setTextColor(GlassStore.theme.accent)
            val key = (item.thumb ?: item.file).absolutePath
            h.img.tag = key
            h.img.setImageDrawable(null)
            val cached = thumbs.get(key)
            if (cached != null) h.img.setImageBitmap(cached) else io.execute {
                val o = BitmapFactory.Options().apply { inSampleSize = if (item.thumb != null) 1 else 4 }
                val b = BitmapFactory.decodeFile(key, o)
                ui.post { if (b != null) { thumbs.put(key, b); if (h.img.tag == key) h.img.setImageBitmap(b) } }
            }
            h.card.setOnClickListener { apply(item) }
            h.card.setOnFocusChangeListener { _, has ->
                // no scale here: this GPU drops the rounded clip on scaled views, so the ring + bloom carry focus
                h.glass.animateFocus(if (has) 1f else 0f, 200)
                if (has) show(item)
            }
        }

        fun focus(pos: Int) {
            row.findViewHolderForAdapterPosition(pos)?.itemView?.findViewById<View>(R.id.tile_card)?.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
