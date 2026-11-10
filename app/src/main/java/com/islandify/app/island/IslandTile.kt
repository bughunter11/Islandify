package com.islandify.app.island

// TILEKIT-FILE: Quick Settings tile = island on / off

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.islandify.app.R
import com.islandify.app.core.IslandSettings

class IslandTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        IslandSettings.init(this)
        refresh()
    }

    override fun onClick() {
        super.onClick()
        IslandSettings.init(this)
        IslandSettings.enabled.value = !IslandSettings.enabled.value
        IslandSettings.save()
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (IslandSettings.enabled.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = getString(R.string.tile_label)
        t.updateTile()
    }
}
