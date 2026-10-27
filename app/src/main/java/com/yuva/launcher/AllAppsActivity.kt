package com.yuva.launcher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Grid of every launchable app on the device. Hidden items are still shown here. */
class AllAppsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AllAppsActivity, 7)
            setPadding(40, 48, 40, 48)
            clipToPadding = false
            clipChildren = false
        }
        setContentView(themedScreen(this, rv))
        val items = AppRepo.load(this).toMutableList()
        rv.adapter = TileAdapter(
            items = items,
            tileScale = 1.0f,
            animScaleMs = 120,
            showLabels = true,
            onClick = {
                runCatching { startActivity(it.launch) }
                finish()
            }
        )
    }
}
