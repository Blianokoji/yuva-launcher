package com.yuva.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

/**
 * Quick-pick wallpaper gallery. Scans the standard public media folders — Downloads,
 * Pictures, DCIM — and shows thumbnails in a grid. Tap one, it becomes the wallpaper.
 * Lets you drop an image into /sdcard/Download via any browser and apply it in two clicks.
 */
class WallpaperPickerActivity : AppCompatActivity() {

    private val folders = listOf(
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_DCIM
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@WallpaperPickerActivity, 4)
            setPadding(48, 48, 48, 48)
            clipToPadding = false
            setBackgroundColor(0xFF0C0A1C.toInt())
        }
        setContentView(rv)

        val images = scanImages()
        if (images.isEmpty()) {
            Toast.makeText(this, "No images found in Downloads, Pictures, or DCIM", Toast.LENGTH_LONG).show()
            finish(); return
        }
        rv.adapter = Adapter(images) { file ->
            applyWallpaper(file)
            Toast.makeText(this, "Applied: ${file.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun scanImages(): List<File> {
        val out = mutableListOf<File>()
        for (name in folders) {
            val dir = Environment.getExternalStoragePublicDirectory(name) ?: continue
            if (!dir.isDirectory) continue
            dir.walkTopDown().maxDepth(2).forEach { f ->
                if (f.isFile && f.length() > 0 && f.extension.lowercase() in SUPPORTED) out.add(f)
            }
        }
        // newest first — a just-downloaded image shows up at the top
        return out.sortedByDescending { it.lastModified() }
    }

    private fun applyWallpaper(src: File) {
        val dst = File(filesDir, "wallpaper.img")
        src.inputStream().use { input -> FileOutputStream(dst).use { out -> input.copyTo(out) } }
        Prefs(this).wallpaperPath = dst.absolutePath
    }

    private class Adapter(
        private val files: List<File>,
        private val onPick: (File) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(android.R.id.icon)
            val label: TextView = v.findViewById(android.R.id.title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // reuse the standard tile layout — same focus animation, same hairline border
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
            // retag ids so we don't need another layout file
            v.findViewById<View>(R.id.tile_icon).id = android.R.id.icon
            v.findViewById<View>(R.id.tile_label).id = android.R.id.title
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = files[pos]
            h.label.text = f.name
            // downsample thumbnail — 220 px wide is plenty for a 4-col grid at 720p
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            var s = 1; while (opts.outWidth / s > 440) s *= 2
            val bmp: Bitmap? = BitmapFactory.decodeFile(
                f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s }
            )
            h.thumb.setImageBitmap(bmp)
            h.itemView.findViewById<View>(R.id.tile_card).setOnClickListener { onPick(f) }
        }

        override fun getItemCount() = files.size
    }

    companion object {
        private val SUPPORTED = setOf("jpg", "jpeg", "png", "webp", "bmp")
    }
}
