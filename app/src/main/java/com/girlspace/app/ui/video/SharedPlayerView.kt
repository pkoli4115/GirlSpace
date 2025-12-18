package com.girlspace.app.ui.video

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun SharedPlayerView(
    player: Player,
    modifier: Modifier = Modifier,
    showController: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    playerViewFactory: (android.content.Context) -> PlayerView = { ctx ->
        PlayerView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = showController
            this.resizeMode = resizeMode

            // Prevent black shutter flash
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

            // ✅ Use setter method if your Media3 has it (most do)
            runCatching { setKeepContentOnPlayerReset(true) }
        }
    }
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            playerViewFactory(ctx).apply {
                this.player = player
                useController = showController
                this.resizeMode = resizeMode
            }
        },
        update = { pv ->
            if (pv.player !== player) pv.player = player
            pv.useController = showController
            pv.resizeMode = resizeMode
        }
    )
}
