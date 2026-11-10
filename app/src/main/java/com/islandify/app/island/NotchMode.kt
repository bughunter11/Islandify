package com.islandify.app.island

import com.islandify.app.core.IslandSettings

// NOTCHMODE: "Notch style" ON = island screen ke upar ke edge se juda (y = 0), upar ke corner seedhe.
internal fun notchOn(): Boolean = IslandSettings.notchMode.value

/** Island window ka top (dp). Notch ON me hamesha 0: Up/Down, Banner Y aur Music Y offsets ignore. */
internal fun pillTopDp(): Float =
    if (IslandSettings.notchMode.value) 0f
    else IslandSettings.offsetY.value + bannerYAdjDp() + musicYAdjDp()
