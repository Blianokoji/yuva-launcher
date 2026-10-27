package com.yuva.launcher

import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Glass app tiles. Focus is a dock-style magnify: the focused tile lifts and grows with a slight
 * overshoot, its neighbours swell a little, the glass blooms in the wallpaper's accent colour and a
 * light sweep crosses it. Everything animates off the one focus event so quick D-pad runs stay smooth.
 */
class TileAdapter(
    private val items: MutableList<AppItem>,
    private val tileScale: Float,
    private val animScaleMs: Long,
    private val showLabels: Boolean = false,
    private val onClick: (AppItem) -> Unit,
    private val onLongPress: ((Int) -> Unit)? = null,
    private val onFocused: ((Int, AppItem) -> Unit)? = null
) : RecyclerView.Adapter<TileAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: FrameLayout = v.findViewById(R.id.tile_card)
        val icon: ImageView = v.findViewById(R.id.tile_icon)
        val label: TextView = v.findViewById(R.id.tile_label)
        lateinit var glass: GlassDrawable
    }

    private var rv: RecyclerView? = null
    private val ms get() = (animScaleMs * 1.7f).toLong().coerceAtLeast(90)

    override fun onAttachedToRecyclerView(r: RecyclerView) { rv = r }
    override fun onDetachedFromRecyclerView(r: RecyclerView) { rv = null }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        val h = VH(v)
        val d = parent.resources.displayMetrics.density
        h.glass = GlassDrawable(h.card, parent.resources.getDimension(R.dimen.tile_radius))
        h.card.background = h.glass
        h.card.scaleX = tileScale; h.card.scaleY = tileScale
        h.icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, 10 * d) }
        }
        h.icon.clipToOutline = true
        h.label.visibility = if (showLabels) View.VISIBLE else View.GONE
        return h
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val a = items[pos]
        h.label.text = a.label
        if (a.glyph != null) {
            // apps without a usable icon get a themed plate + glyph instead of a clipped banner
            h.icon.setImageResource(a.glyph)
            val t = GlassStore.theme
            h.icon.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.accent, t.accentDeep))
            val dn = h.icon.resources.displayMetrics.density
            val pad = (dn * 14).toInt()
            h.icon.setPadding(pad, pad, pad, pad)
            h.icon.setColorFilter(0xFFFFFFFF.toInt())
            h.icon.layoutParams = h.icon.layoutParams.apply { width = (dn * 58).toInt() }
        } else {
            h.icon.setImageDrawable(a.icon)
            h.icon.background = null; h.icon.setPadding(0, 0, 0, 0); h.icon.clearColorFilter()
            h.icon.layoutParams = h.icon.layoutParams.apply { width = h.icon.resources.getDimensionPixelSize(R.dimen.tile_icon_w) }
        }
        h.card.setOnClickListener { onClick(a) }
        h.card.setOnLongClickListener { onLongPress?.invoke(h.bindingAdapterPosition); true }
        h.card.setOnFocusChangeListener { _, has ->
            val p = h.bindingAdapterPosition
            if (has && p >= 0) {
                layoutFocus(p)
                h.glass.playSweep(ms + 200)
                onFocused?.invoke(p, items[p])
            } else {
                // focus either moves to a sibling (which re-lays-out) or leaves the row entirely
                rv?.post { if (rv?.hasFocus() == false) layoutFocus(-1) }
            }
        }
    }

    /** Magnify around [focusPos]; -1 relaxes the whole row. */
    private fun layoutFocus(focusPos: Int) {
        val r = rv ?: return
        val d = r.resources.displayMetrics.density
        for (i in 0 until r.childCount) {
            val h = r.getChildViewHolder(r.getChildAt(i)) as? VH ?: continue
            val dist = if (focusPos < 0) 99 else abs(h.bindingAdapterPosition - focusPos)
            val s = tileScale * when (dist) { 0 -> 1.1f; 1 -> 1.03f; else -> 1f }
            val ty = when (dist) { 0 -> -8f; 1 -> -2f; else -> 0f } * d
            h.card.animate().cancel()
            h.card.animate().scaleX(s).scaleY(s).translationY(ty)
                .setDuration(ms)
                .setInterpolator(if (dist == 0) OvershootInterpolator(1.1f) else DecelerateInterpolator(1.6f))
                .start()
            h.icon.animate().cancel()
            h.icon.animate().scaleX(if (dist == 0) 1.04f else 1f).scaleY(if (dist == 0) 1.04f else 1f)
                .setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
            h.glass.animateFocus(if (dist == 0) 1f else 0f, ms)
        }
    }

    fun focusCard(pos: Int) {
        rv?.findViewHolderForAdapterPosition(pos)?.itemView?.findViewById<View>(R.id.tile_card)?.requestFocus()
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from); items.add(to, item); notifyItemMoved(from, to)
    }
}
