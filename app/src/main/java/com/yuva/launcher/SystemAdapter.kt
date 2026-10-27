package com.yuva.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Left nav rail: icon + label rows on glass. The row you are "on" (Home) is shown bright, the rest muted. */
class SystemAdapter(
    private val items: List<SystemTile>,
    private val ms: Long
) : RecyclerView.Adapter<SystemAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: LinearLayout = v.findViewById(R.id.tile_card)
        val icon: ImageView = v.findViewById(R.id.tile_icon)
        val label: TextView = v.findViewById(R.id.tile_label)
        lateinit var glass: GlassDrawable
    }

    private var rv: RecyclerView? = null
    override fun onAttachedToRecyclerView(r: RecyclerView) { rv = r }
    override fun onDetachedFromRecyclerView(r: RecyclerView) { rv = null }

    fun focus(pos: Int) {
        rv?.findViewHolderForAdapterPosition(pos)?.itemView?.findViewById<View>(R.id.tile_card)?.requestFocus()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_nav, parent, false)
        val h = VH(v)
        h.glass = GlassDrawable(h.card, parent.resources.getDimension(R.dimen.nav_h) / 2f, strength = 0.5f)
        h.card.background = h.glass
        return h
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val tile = items[pos]
        h.icon.setImageResource(tile.icon)
        h.label.text = tile.label
        paint(h, tile, false)
        h.card.setOnClickListener { tile.onClick() }
        h.card.setOnFocusChangeListener { _, has ->
            paint(h, tile, has)
            val s = if (has) 1.07f else 1f
            h.card.animate().cancel()
            h.card.animate().scaleX(s).scaleY(s).translationX(if (has) 8f * h.card.resources.displayMetrics.density else 0f)
                .setDuration(ms).setInterpolator(if (has) OvershootInterpolator(1.6f) else DecelerateInterpolator()).start()
            h.glass.animateFocus(if (has) 1f else 0f, ms)
            if (has) h.glass.playSweep(ms + 150)
        }
    }

    private fun paint(h: VH, tile: SystemTile, focused: Boolean) {
        val bright = focused || tile.selected
        val c = if (bright) 0xFFFFFFFF.toInt() else 0xCCFFFFFF.toInt()
        h.icon.setColorFilter(if (tile.selected && !focused) GlassStore.theme.accent else c)
        h.label.setTextColor(c)
    }

    override fun getItemCount() = items.size
}
