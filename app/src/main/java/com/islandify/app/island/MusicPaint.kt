package com.islandify.app.island

// MUSICAUTO: music card / music pill ka background gaane (album art) ke rang se.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** [song] = album art ka rang. Upar-baayen halka chamakdar, neeche-daayen gehra; patli border. */
internal fun Modifier.songPaint(song: Color, alpha: Float, shape: Shape, big: Boolean): Modifier = this
    .background(
        Brush.linearGradient(
            listOf(
                lerp(Color.Black, song, if (big) 0.62f else 0.55f).copy(alpha = alpha),
                lerp(Color.Black, song, if (big) 0.26f else 0.22f).copy(alpha = alpha),
            ),
            start = Offset.Zero,
            end = Offset(1200f, 900f),
        )
    )
    .drawBehind {
        drawRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                endY = size.height * 0.55f,
            )
        )
    }
    .border(1.dp, song.copy(alpha = 0.40f), shape)
