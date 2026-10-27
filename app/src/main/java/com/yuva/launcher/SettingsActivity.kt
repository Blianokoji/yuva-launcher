package com.yuva.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val pickWallpaper = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            copyToPrivateFile(uri)?.let {
                prefs.wallpaperPath = it
                Toast.makeText(this, "Wallpaper applied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        findViewById<Button>(R.id.btn_wallpaper).setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            pickWallpaper.launch(i)
        }

        // Quick-pick: scans Downloads/Pictures/DCIM directly, no file-picker UI needed.
        findViewById<Button>(R.id.btn_wallpaper_gallery).setOnClickListener {
            startActivity(Intent(this, WallpaperPickerActivity::class.java))
        }

        val tile = findViewById<SeekBar>(R.id.seek_tile)
        tile.progress = ((prefs.tileScale - 0.8f) / 0.4f * 100).toInt().coerceIn(0, 100)
        tile.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, u: Boolean) {
                prefs.tileScale = 0.8f + (v / 100f) * 0.4f  // 0.8–1.2
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val anim = findViewById<SeekBar>(R.id.seek_anim)
        anim.progress = ((prefs.animScale - 0.3f) / 1.2f * 100).toInt().coerceIn(0, 100)
        anim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, u: Boolean) {
                // 0.3× (fastest) to 1.5× (slowest). Default slider at 50 ≈ 0.9×
                prefs.animScale = 0.3f + (v / 100f) * 1.2f
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val list = findViewById<RecyclerView>(R.id.hidden_list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = HiddenAdapter(this, prefs)
    }

    private fun copyToPrivateFile(uri: Uri): String? {
        return runCatching {
            val dst = File(filesDir, "wallpaper.img")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dst).use { out -> input.copyTo(out) }
            }
            dst.absolutePath
        }.getOrNull()
    }

    /** Per-app checkbox; checked = hidden from the main row. Shows all apps including those currently in order. */
    class HiddenAdapter(private val ctx: SettingsActivity, private val prefs: Prefs) :
        RecyclerView.Adapter<HiddenAdapter.VH>() {

        private val apps = AppRepo.load(ctx)
        private val hidden = prefs.hidden().toMutableSet()

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.row_icon)
            val label: TextView = v.findViewById(R.id.row_label)
            val check: CheckBox = v.findViewById(R.id.row_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_hidden, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val a = apps[pos]
            h.icon.setImageDrawable(a.icon)
            h.label.text = a.label
            h.check.isChecked = hidden.contains(a.pkg)
            h.itemView.setOnClickListener {
                if (hidden.contains(a.pkg)) hidden.remove(a.pkg) else hidden.add(a.pkg)
                prefs.setHidden(hidden.toSet())
                notifyItemChanged(pos)
            }
        }

        override fun getItemCount() = apps.size
    }
}
