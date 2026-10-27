package com.yuva.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** [glyph] replaces a poor banner with a themed plate + vector (used for the browser). */
data class AppItem(val pkg: String, val label: String, val icon: Drawable, val launch: Intent, val glyph: Int? = null)

/** Enumerates launchable apps. Prefers LEANBACK_LAUNCHER, falls back to LAUNCHER. */
object AppRepo {
    private val GLYPHS = mapOf("com.internet.tvbrowser" to R.drawable.ic_globe)

    fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val found = linkedMapOf<String, AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            val results = pm.queryIntentActivities(i, 0)
            for (r in results) {
                val a = r.activityInfo ?: continue
                if (a.packageName == ctx.packageName) continue
                if (found.containsKey(a.packageName)) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(cat)
                    .setComponent(android.content.ComponentName(a.packageName, a.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                found[a.packageName] = AppItem(
                    pkg = a.packageName,
                    label = r.loadLabel(pm)?.toString() ?: a.packageName,
                    // Prefer the TV banner where the app ships one; else fall back to the icon.
                    icon = runCatching { a.loadBanner(pm) ?: r.loadIcon(pm) }
                        .getOrNull() ?: a.loadIcon(pm),
                    launch = launch,
                    glyph = GLYPHS[a.packageName]
                )
            }
        }
        return found.values.toList()
    }

    /** Ordered + hidden filter in one pass. Items in prefs' order first, remainder appended. */
    fun ordered(ctx: Context, prefs: Prefs): List<AppItem> {
        val all = load(ctx).associateBy { it.pkg }
        val hidden = prefs.hidden()
        val desired = prefs.order().filter { !hidden.contains(it) }
        val out = ArrayList<AppItem>(all.size)
        for (p in desired) all[p]?.let(out::add)
        for ((p, a) in all) if (p !in desired && p !in hidden) out.add(a)
        return out
    }
}
