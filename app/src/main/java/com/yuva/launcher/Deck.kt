package com.yuva.launcher

import android.content.Context
import android.graphics.Color
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * One wallpaper in the deck. [accent] == 0 means "derive from the image".
 * Deck artwork is read from the app's private external folder (…/Android/data/<pkg>/files/deck),
 * so it can be pushed to the device without ever being committed to the repo or shipped in the APK.
 */
data class DeckItem(
    val id: String,
    val title: String,
    val category: String,
    val mood: String,
    val file: File,
    val thumb: File?,
    val accent: Int,
    val credit: String?,
    val rights: String?,
    val width: Int = 0,
    val height: Int = 0
)

object Deck {
    const val DEFAULT_ID = "crimson-eclipse"
    /** Browsing order for the gallery. Anything else falls to the end. */
    private val ORDER = listOf("Signature", "Crimson", "Moonlight", "Serenity", "Action", "Original", "Yours")

    fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null), "deck")

    /** Curated entries whose files actually exist; a bad manifest or missing image is skipped, not fatal. */
    private fun curated(ctx: Context): List<DeckItem> {
        val manifest = File(dir(ctx), "deck.json")
        if (!manifest.isFile) return emptyList()
        val arr = runCatching { JSONObject(manifest.readText()).getJSONArray("wallpapers") }.getOrNull() ?: return emptyList()
        val out = ArrayList<DeckItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("image")
            if (name.contains('/') || name.contains('\\') || name.contains("..")) continue   // manifest names are file names, never paths
            val img = File(dir(ctx), name)
            if (!img.isFile || img.length() == 0L) continue
            val thumb = o.optString("thumbnail").takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains("..") }
                ?.let { File(dir(ctx), it) }?.takeIf { it.isFile }
            out += DeckItem(
                id = o.optString("id", img.nameWithoutExtension),
                title = o.optString("title", img.nameWithoutExtension),
                category = o.optString("category", "Signature"),
                mood = o.optString("mood"),
                file = img, thumb = thumb,
                accent = runCatching { Color.parseColor(o.optString("accentColor")) }.getOrDefault(0),
                credit = o.optString("attribution").takeIf { it.isNotEmpty() },
                rights = o.optString("rights").takeIf { it.isNotEmpty() },
                width = o.optInt("width"), height = o.optInt("height")
            )
        }
        return out
    }

    fun defaultItem(ctx: Context): DeckItem? = curated(ctx).let { l -> l.firstOrNull { it.id == DEFAULT_ID } ?: l.firstOrNull() }

    /** Curated deck, then the originals bundled in the APK, then images the user dropped in Downloads/Pictures/DCIM. */
    fun all(ctx: Context): List<DeckItem> {
        val list = ArrayList<DeckItem>(curated(ctx))
        BundledWalls.ensure(ctx).forEachIndexed { i, f ->
            list += DeckItem("orig-$i", BundledWalls.all[i].first, "Original", "Bundled original artwork", f, null, 0, "Original artwork, MIT licensed", null)
        }
        for (name in listOf(Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM)) {
            val d = Environment.getExternalStoragePublicDirectory(name) ?: continue
            if (!d.isDirectory) continue
            d.walkTopDown().maxDepth(2).forEach { f ->
                if (f.isFile && f.length() > 0 && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp"))
                    list += DeckItem("yours-${f.absolutePath.hashCode()}", f.nameWithoutExtension.replace('_', ' '), "Yours", "From ${d.name}", f, null, 0, null, null)
            }
        }
        return list.sortedWith(compareBy({ ORDER.indexOf(it.category).let { i -> if (i < 0) 99 else i } }))
    }
}
