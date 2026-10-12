package com.wallpaperapp.app.ui.intro

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.wallpaperapp.app.R

/**
 * The hamster waving hi (last 2 s of the boot video, square crop, silent): played once inside Home's
 * diamond button for the intro encore. Its first frame sits underneath so there's never a black flash.
 * [onEnd] fires when the clip finishes or can't play.
 */
@Composable
fun WaveClip(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val player = remember { MediaPlayer() }
    var started by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }
    Box(modifier) {
        Image(painterResource(R.drawable.boot_video_wave_first), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    alpha = 0f
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val view = this@apply
                            runCatching {
                                player.setSurface(Surface(st))
                                player.setDataSource(ctx, Uri.parse("android.resource://${ctx.packageName}/${R.raw.boot_video_wave}"))
                                player.setVolume(0f, 0f)
                                player.setOnInfoListener { _, what, _ ->
                                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) { view.alpha = 1f; started = true }
                                    false
                                }
                                player.setOnCompletionListener { onEnd() }
                                player.setOnErrorListener { _, _, _ -> onEnd(); true }
                                player.setOnPreparedListener { it.start() }
                                player.prepareAsync()
                            }.onFailure { onEnd() }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            update = { it.alpha = if (started) 1f else 0f },
        )
    }
}
