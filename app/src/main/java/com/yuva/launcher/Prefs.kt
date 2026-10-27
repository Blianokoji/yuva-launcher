package com.yuva.launcher

import android.content.Context
import android.content.SharedPreferences

/** Single JSON-free prefs store. Keeps the launcher's config lean. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("yuva", Context.MODE_PRIVATE)

    // Default order: 8 apps you asked for, matched by package. First match wins.
    // The user can reorder from the launcher; this seeds the first run only.
    private val defaultOrder = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.mediaclient",
        "in.startv.hotstar",
        "com.apple.atve.androidtv.appletv",
        "com.amazon.amazonvideo.livingroom",
        "com.crunchyroll.crunchyroid",
        "org.jellyfin.androidtv",
        "com.internet.tvbrowser"
    )

    var wallpaperPath: String?
        get() = sp.getString("wallpaper", null)
        set(v) = sp.edit().putString("wallpaper", v).apply()

    var tileScale: Float
        get() = sp.getFloat("tile_scale", 1.0f)
        set(v) = sp.edit().putFloat("tile_scale", v).apply()

    var animScale: Float
        get() = sp.getFloat("anim_scale", 1.0f)
        set(v) = sp.edit().putFloat("anim_scale", v).apply()

    fun order(): List<String> = sp.getString("order", null)?.split("|")?.filter { it.isNotEmpty() } ?: defaultOrder
    fun setOrder(pkgs: List<String>) = sp.edit().putString("order", pkgs.joinToString("|")).apply()

    fun hidden(): Set<String> = sp.getStringSet("hidden", emptySet()) ?: emptySet()
    fun setHidden(h: Set<String>) = sp.edit().putStringSet("hidden", h).apply()
}
