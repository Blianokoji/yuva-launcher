package com.yuva.launcher

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var clock: TextView
    private lateinit var wifiLabel: TextView
    private lateinit var wallpaperView: ImageView
    private val tick = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { updateClock(); updateWifi(); tick.postDelayed(this, 15_000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        wallpaperView = findViewById(R.id.wallpaper)
        clock = findViewById(R.id.clock)
        wifiLabel = findViewById(R.id.wifi_label)

        setupAppRow()
        setupSystemRow()
    }

    override fun onResume() {
        super.onResume()
        loadWallpaper()
        updateClock(); updateWifi()
        tick.postDelayed(ticker, 15_000)
        // reload app row in case packages changed while away
        refreshAppRow()
    }

    override fun onPause() { tick.removeCallbacks(ticker); super.onPause() }

    /** HOME key should bring us to the front without reloading. */
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); refreshAppRow() }

    // Block BACK on home — standard launcher behavior.
    override fun onBackPressed() { /* no-op */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> { openSettings(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun loadWallpaper() {
        val path = prefs.wallpaperPath
        if (path != null && File(path).exists()) {
            // downsample to screen width to keep RAM low — important on this 1GB device
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            val target = resources.displayMetrics.widthPixels.coerceAtLeast(1280)
            var sample = 1
            while (opts.outWidth / sample > target * 2) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            wallpaperView.setImageBitmap(bmp)
        } else {
            wallpaperView.setImageDrawable(null)  // canvas color shows through
        }
    }

    private fun setupAppRow() {
        val rv = findViewById<RecyclerView>(R.id.app_row)
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rv.setHasFixedSize(true)
        rv.itemAnimator = null  // snappier focus on 1GB RAM
        refreshAppRow()
    }

    private fun refreshAppRow() {
        val rv = findViewById<RecyclerView>(R.id.app_row)
        val items = AppRepo.ordered(this, prefs).toMutableList()
        val adapter = TileAdapter(
            items = items,
            tileScale = prefs.tileScale,
            animScaleMs = (120 * prefs.animScale).toLong().coerceAtLeast(40),
            onClick = { launch(it) },
            onLongPress = { pos ->
                // simple reorder: move focused item forward one slot; wraps to start at end.
                val next = if (pos + 1 >= items.size) 0 else pos + 1
                (rv.adapter as TileAdapter).move(pos, next)
                prefs.setOrder(items.map { it.pkg })
            }
        )
        rv.adapter = adapter
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.findViewById<android.view.View>(R.id.tile_card)?.requestFocus() }
    }

    private fun setupSystemRow() {
        val rv = findViewById<RecyclerView>(R.id.system_row)
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rv.setHasFixedSize(true)
        rv.itemAnimator = null

        val sys = mutableListOf<SystemTile>()
        // HDMI 1 — tries Droidlogic's TV input if present, else swallows harmlessly
        sys += SystemTile(getString(R.string.hdmi_1)) {
            val i = Intent("android.intent.action.VIEW_IN_HDMI")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageManager.resolveActivity(i, 0) != null) startActivity(i)
            else {
                // common Droidlogic/Amlogic HDMI switcher component on this board
                val fallback = Intent().setClassName(
                    "com.droidlogic.tvinput",
                    "com.droidlogic.tvinput.hdmi.HdmiCECSettingsActivity"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(fallback) }.onFailure {
                    Toast.makeText(this, "No HDMI switch available", Toast.LENGTH_SHORT).show()
                }
            }
        }
        sys += SystemTile(getString(R.string.all_apps)) { startActivity(Intent(this, AllAppsActivity::class.java)) }
        sys += SystemTile(getString(R.string.settings)) { openSettings() }

        rv.adapter = SystemAdapter(sys)
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun launch(a: AppItem) {
        runCatching { startActivity(a.launch) }
            .onFailure { Toast.makeText(this, "Couldn't launch ${a.label}", Toast.LENGTH_SHORT).show() }
    }

    private fun updateClock() {
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun updateWifi() {
        // ConnectivityManager — no WiFi permission needed, works on all transports
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        wifiLabel.text = when {
            caps == null -> ""
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            else -> ""
        }
    }
}

data class SystemTile(val label: String, val onClick: () -> Unit)
