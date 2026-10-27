package com.yuva.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Small system-row tiles (HDMI, All Apps, Settings): label-only, no icon. */
class SystemAdapter(private val items: List<SystemTile>) :
    RecyclerView.Adapter<SystemAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: FrameLayout = v.findViewById(R.id.tile_card)
        val icon: ImageView = v.findViewById(R.id.tile_icon)
        val label: TextView = v.findViewById(R.id.tile_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        v.scaleX = 0.75f; v.scaleY = 0.75f  // smaller than app tiles
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val tile = items[pos]
        h.icon.setImageDrawable(null)
        h.label.text = tile.label
        h.card.setOnClickListener { tile.onClick() }
        h.card.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 0.82f else 0.75f).scaleY(if (has) 0.82f else 0.75f).setDuration(100).start()
        }
    }

    override fun getItemCount() = items.size
}
