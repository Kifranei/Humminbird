package com.ella.music.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

@Composable
internal fun PlayerCustomBackground(
    uri: String,
    imageAlpha: Float = 1f,
    dimAlpha: Float = 0.26f,
    modifier: Modifier = Modifier
) {
    val dim = dimAlpha.coerceIn(0f, 0.8f)
    Box(modifier = modifier.background(Color.Black)) {
        PlayerCoverImage(
            model = Uri.parse(uri),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = imageAlpha.coerceIn(0.2f, 1f) },
            contentScale = ContentScale.Crop,
            sizePx = 1400
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = (dim * 0.32f).coerceAtMost(0.28f)),
                            Color.Transparent,
                            Color.Black.copy(alpha = (dim + 0.22f).coerceAtMost(0.85f))
                        )
                    )
                )
        )
    }
}
