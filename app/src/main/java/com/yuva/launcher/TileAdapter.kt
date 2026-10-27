package com.yuva.launcher

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TileAdapter(
    private val items: MutableList<AppItem>,
    private val tileScale: Float,
    private val animScaleMs: Long,
    private val onClick: (AppItem) -> Unit,
    private val onLongPress: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<TileAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: FrameLayout = v.findViewById(R.id.tile_card)
        val icon: ImageView = v.findViewById(R.id.tile_icon)
        val label: TextView = v.findViewById(R.id.tile_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        // apply user tile-scale (0.8–1.2 range mapped from slider)
        v.scaleX = tileScale
        v.scaleY = tileScale
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val a = items[pos]
        h.icon.setImageDrawable(a.icon)
        h.label.text = a.label
        h.card.setOnClickListener { onClick(a) }
        h.card.setOnLongClickListener { onLongPress?.invoke(h.adapterPosition); true }

        // Focus animation: scale 1.0 -> 1.08, animScaleMs controls duration
        h.card.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus)
            animateFocus(h.label, hasFocus, labelOnly = true)
        }
    }

    private fun animateFocus(v: View, focused: Boolean, labelOnly: Boolean = false) {
        val from = if (focused) 1.0f else 1.08f
        val to = if (focused) 1.08f else 1.0f
        if (labelOnly) {
            v.animate().alpha(if (focused) 1f else 0.7f).setDuration(animScaleMs).start()
            return
        }
        ValueAnimator.ofFloat(from, to).apply {
            duration = animScaleMs
            addUpdateListener { v.scaleX = it.animatedValue as Float; v.scaleY = v.scaleX }
            start()
        }
        v.translationZ = if (focused) 8f else 0f
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from); items.add(to, item); notifyItemMoved(from, to)
    }
}
