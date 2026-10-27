package com.yuva.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HomeActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var root: View
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var netIcon: ImageView
    private lateinit var wallpaperView: ImageView
    private lateinit var rowLabel: TextView
    private lateinit var appRow: RecyclerView
    private lateinit var greetHi: TextView
    private lateinit var greetName: TextView
    private lateinit var greetTag: TextView
    private var rowSig = ""
    private var lastRowPos = 0
    private lateinit var nav: RecyclerView
    private var firstWallpaper = true

    private val tick = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { updateNet(); tick.postDelayed(this, 15_000) }
    }

    /** Minute ticks keep the clock exact; zone/time changes (NTP, manual) refresh instantly. The JVM caches its zone, so reset it. */
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)
            updateClock()
            setupGreeting()
        }
    }

    private val ms get() = (260 * prefs.animScale).toLong().coerceAtLeast(80)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // a launcher never exits on BACK; a dispatcher callback is the supported way (overriding onBackPressed trips lint)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* stay home */ }
        })
        prefs = Prefs(this)
        root = findViewById(R.id.root)
        wallpaperView = findViewById(R.id.wallpaper)
        clock = findViewById(R.id.clock); date = findViewById(R.id.date)
        netIcon = findViewById(R.id.net_icon)
        rowLabel = findViewById(R.id.row_label)
        appRow = findViewById(R.id.app_row)
        greetHi = findViewById(R.id.greet_hi); greetName = findViewById(R.id.greet_name); greetTag = findViewById(R.id.greet_tag)

        val chip = findViewById<View>(R.id.net_chip)
        chip.background = GlassDrawable(chip, 0f, strength = 0.6f, circle = true)

        applyWallpaper()
        setupGreeting()
        setupNav()
        refreshAppRow(force = true)
        playIntro()
    }

    override fun onResume() {
        super.onResume()
        applyWallpaper()
        TimeZone.setDefault(null)
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        updateClock(); updateNet(); setupGreeting()
        tick.postDelayed(ticker, 15_000)
        refreshAppRow(force = false)
    }

    override fun onPause() {
        tick.removeCallbacks(ticker)
        runCatching { unregisterReceiver(timeReceiver) }
        super.onPause()
    }

    /** HOME pressed while we exist: settle back to the first tile with the entrance animation. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        appRow.scrollToPosition(0)
        (appRow.adapter as? TileAdapter)?.let { a -> appRow.post { a.focusCard(0) } }
        playIntro()
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val f = currentFocus
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> { openSettings(); return true }
            // rail <-> row hand-off is explicit: geometry alone sends LEFT from the first tile to the last rail item
            KeyEvent.KEYCODE_DPAD_LEFT -> if (f != null && appRow.findContainingViewHolder(f)?.bindingAdapterPosition == 0) {
                (nav.adapter as? SystemAdapter)?.focus(0); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (f != null && nav.findContainingViewHolder(f) != null) {
                (appRow.adapter as? TileAdapter)?.focusCard(lastRowPos); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------------------------------------------------------- wallpaper + theme

    private fun applyWallpaper() {
        val (bmp, changed) = GlassStore.load(this, prefs)
        if (bmp == null) return
        if (changed) {
            val shown = compose(bmp)
            if (firstWallpaper) wallpaperView.setImageBitmap(shown)
            else wallpaperView.animate().alpha(0f).setDuration(220).withEndAction {
                wallpaperView.setImageBitmap(shown)
                wallpaperView.animate().alpha(1f).setDuration(420).start()
            }.start()
            firstWallpaper = false
            applyTheme()
        }
    }

    /** Re-tint everything that reads the wallpaper-derived colours. */
    private fun applyTheme() {
        val t = GlassStore.theme
        netIcon.setColorFilter(0xE6FFFFFF.toInt())
        invalidateAll(root)
    }

    /** Bakes the legibility veils + accent glow into one bitmap: a single full-screen layer instead of four (this GPU is weak). */
    private fun compose(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out); val w = out.width.toFloat(); val hh = out.height.toFloat()
        val p = Paint()
        // bright artwork gets a stronger veil so Moon Silver text keeps its contrast on any wallpaper
        val k = (GlassStore.theme.luma / 0.30f).coerceIn(1f, 1.9f)
        fun veil(a: Int) = (minOf(255, (a * k).toInt())) shl 24
        p.shader = LinearGradient(0f, 0f, 0f, hh, intArrayOf(veil(0x70), 0, veil(0x99)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, hh, p)
        p.shader = LinearGradient(0f, 0f, w * 0.27f, 0f, intArrayOf(veil(0xC0), veil(0x59), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w * 0.27f, hh, p)
        val a = GlassStore.theme.accent and 0x00FFFFFF
        p.shader = RadialGradient(w * 0.45f, hh * 1.15f, w * 0.62f, intArrayOf(0x59000000 or a, a), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, hh, p)
        return out
    }

    private fun invalidateAll(v: View) {
        v.invalidate()
        if (v is ViewGroup) for (i in 0 until v.childCount) invalidateAll(v.getChildAt(i))
    }

    // ---------------------------------------------------------------- greeting / clock

    private fun setupGreeting() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (hi, tags) = when (h) {
            in 5..11 -> "Good morning," to listOf("Fresh episode, fresh start.", "Coffee first, then the next arc.")
            in 12..16 -> "Good afternoon," to listOf("A long watch is a fine plan.", "The queue is waiting.")
            in 17..21 -> "Good evening," to listOf("Stories hit different here.", "Lights down, volume up.")
            else -> "Late night," to listOf("One more episode never hurt.", "The night shift of anime.")
        }
        if (greetHi.text == hi) return          // same part of the day: keep the tagline stable
        greetHi.text = hi
        greetName.text = prefs.userName
        greetTag.text = tags.random()
    }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
    }

    private fun updateNet() {
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        netIcon.setImageResource(
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) R.drawable.ic_ethernet else R.drawable.ic_wifi
        )
        netIcon.alpha = if (caps == null) 0.35f else 1f
    }

    // ---------------------------------------------------------------- nav rail

    private fun setupNav() {
        nav = findViewById(R.id.nav)
        nav.layoutManager = LinearLayoutManager(this)
        nav.itemAnimator = null
        val items = listOf(
            SystemTile(getString(R.string.home), R.drawable.ic_home, selected = true) {
                appRow.smoothScrollToPosition(0)
                (appRow.adapter as? TileAdapter)?.focusCard(0)
            },
            SystemTile(getString(R.string.all_apps), R.drawable.ic_apps) { startActivity(Intent(this, AllAppsActivity::class.java)) },
            SystemTile(getString(R.string.hdmi_1), R.drawable.ic_hdmi) { openHdmi() },
            SystemTile(getString(R.string.wallpaper), R.drawable.ic_image) { startActivity(Intent(this, WallpaperPickerActivity::class.java)) },
            SystemTile(getString(R.string.settings), R.drawable.ic_settings) { openSettings() }
        )
        nav.adapter = SystemAdapter(items, ms)
    }

    private fun openHdmi() {
        val i = Intent("android.intent.action.VIEW_IN_HDMI").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageManager.resolveActivity(i, 0) != null) { startActivity(i); return }
        // common Droidlogic/Amlogic HDMI switcher component on this board
        val fallback = Intent().setClassName("com.droidlogic.tvinput", "com.droidlogic.tvinput.hdmi.HdmiCECSettingsActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(fallback) }.onFailure {
            Toast.makeText(this, "No HDMI switch available", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- app row

    private fun refreshAppRow(force: Boolean) {
        val items = AppRepo.ordered(this, prefs).toMutableList()
        val sig = items.joinToString("|") { it.pkg } + prefs.tileScale + prefs.animScale
        if (!force && sig == rowSig) return          // nothing changed: keep focus and avoid re-animating
        rowSig = sig
        appRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        appRow.itemAnimator = null
        rowLabel.text = getString(R.string.your_apps)
        val adapter = TileAdapter(
            items = items,
            tileScale = prefs.tileScale,
            animScaleMs = (120 * prefs.animScale).toLong().coerceAtLeast(40),
            onClick = { launch(it) },
            onLongPress = { pos ->
                val next = if (pos + 1 >= items.size) 0 else pos + 1
                (appRow.adapter as TileAdapter).move(pos, next)
                prefs.setOrder(items.map { it.pkg })
            },
            onFocused = { pos, app -> onAppFocused(pos, items.size, app) }
        )
        appRow.adapter = adapter
        appRow.post { adapter.focusCard(0) }
    }

    private fun onAppFocused(pos: Int, count: Int, app: AppItem) {
        lastRowPos = pos
        swapLabel(app.label)
        val d = resources.displayMetrics.density
        wallpaperView.animate().translationX(((count - 1) / 2f - pos) * 7f * d).setDuration(700)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun swapLabel(text: String) {
        if (rowLabel.text == text) return
        rowLabel.animate().cancel()
        rowLabel.animate().alpha(0f).translationY(6f).setDuration(70).withEndAction {
            rowLabel.text = text
            rowLabel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }.start()
    }

    // ---------------------------------------------------------------- entrance animation

    private fun playIntro() {
        val d = resources.displayMetrics.density
        fun rise(v: View, delay: Long, dy: Float) {
            v.animate().cancel()
            v.alpha = 0f; v.translationY = dy * d
            v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(520)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
        rise(findViewById(R.id.greeting), 0, 18f)
        rise(rowLabel, 120, 14f)
        appRow.post {
            for (i in 0 until appRow.childCount) rise(appRow.getChildAt(i), 160L + i * 45L, 36f)
        }
        val nav = findViewById<RecyclerView>(R.id.nav)
        nav.post { for (i in 0 until nav.childCount) rise(nav.getChildAt(i), 60L + i * 50L, 0f).also {
            nav.getChildAt(i).translationX = -28f * d
            nav.getChildAt(i).animate().translationX(0f).start()
        } }
    }

    // ---------------------------------------------------------------- misc

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun launch(a: AppItem) {
        runCatching { startActivity(a.launch) }
            .onFailure { Toast.makeText(this, "Couldn't launch ${a.label}", Toast.LENGTH_SHORT).show() }
    }
}

data class SystemTile(val label: String, val icon: Int, val selected: Boolean = false, val onClick: () -> Unit)
